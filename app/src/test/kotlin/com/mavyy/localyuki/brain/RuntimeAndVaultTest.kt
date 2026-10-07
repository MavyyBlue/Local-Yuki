package com.mavyy.localyuki.brain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.temporal.*
import com.mavyy.localyuki.foundation.workspace.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.memory.MemoryStore
import com.mavyy.localyuki.vault.ContinuityVault
import com.mavyy.localyuki.admission.*
import com.mavyy.localyuki.foundation.admission.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class RuntimeAndVaultTest {
    private lateinit var context: Context
    private val now=Instant.parse("2026-10-02T12:00:00Z")
    private val temporal=DeterministicTemporalGrounding(ClockSource { now },ZoneSource { ZoneId.of("UTC") })
    private fun brain()=BrainRuntime(context,temporal,{ foreground,at -> FoundationResult.Success(
        DeviceResources(4096*ResourceGovernor.MIB,2048*ResourceGovernor.MIB,80,true,ThermalPressure.NORMAL,foreground,at)) })
    private fun <T:Any> ok(result: FoundationResult<T>):T=(result as FoundationResult.Success).value
    private val password="owner-vault-secret".toCharArray()
    @Before fun setup() { context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME) }
    @After fun cleanup() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun db()=SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,SQLiteDatabase.OPEN_READWRITE)
    @Test fun productionConversationContextRetainsCanonicalIdentityAndGroundedInput() {
        brain().use { brain ->
            assertTrue(brain.open())
            val input=ok(brain.saveInput("Hello, My Love 🥰"));val turn=ok(brain.context(input))
            val json=com.mavyy.localyuki.cognition.CognitiveJson.context(turn,brain.conversationHistory(),listOf("INTENTION: Hello, My Love 🥰"))
            val parsed=org.json.JSONObject(json)
            assertEquals(input.payload,parsed.getString("ownerInput"))
            assertEquals(9,turn.identity.personality.facets.size)
            assertTrue(parsed.getJSONObject("identity").getJSONArray("personality").length()>=1)
            assertEquals(turn.identity.self.canonicalName,parsed.getJSONObject("identity").getString("self"))
            assertEquals(4,turn.identity.honesty.rules.size)
            System.getenv("YUKI_PROOF_DIR")?.let { path->
                val dir=File(path).apply { mkdirs() };dir.resolve("one-full-user.json").writeText(json)
                dir.resolve("one-full.gbnf").writeText(com.mavyy.localyuki.inference.PromptPreparation.candidates(ModelRole.SYSTEM_ONE,json,64).first().grammar)
                val question=ok(brain.saveInput("I have a headache. Is its cause certain?"));val reasoningTurn=ok(brain.context(question))
                dir.resolve("two-full-user.json").writeText(com.mavyy.localyuki.cognition.CognitiveJson.context(reasoningTurn,brain.conversationHistory(),emptyList()))
                dir.resolve("two-full.gbnf").writeText(com.mavyy.localyuki.inference.OrganGrammar.forRole(ModelRole.SYSTEM_TWO,com.mavyy.localyuki.cognition.CognitiveJson.refs(reasoningTurn).keys))
                for((name,role,content) in listOf(Triple("one",ModelRole.SYSTEM_ONE,json),Triple("two",ModelRole.SYSTEM_TWO,dir.resolve("two-full-user.json").readText()))) {
                    com.mavyy.localyuki.inference.PromptPreparation.candidates(role,content,64).forEachIndexed { i,p->
                        dir.resolve("$name-candidate-$i-user.json").writeText(p.user);dir.resolve("$name-candidate-$i.gbnf").writeText(p.grammar);dir.resolve("$name-candidate-$i-shortened.txt").writeText(p.shortened.toString())
                    }
                }
                val crowded=dir.resolve("crowded").apply { mkdirs() }
                val history=(0 until 8).map { "USER_INPUT: Older conversation $it "+"a lengthy older topic ".repeat(18) }
                val full=com.mavyy.localyuki.cognition.CognitiveJson.context(turn,history,emptyList())
                crowded.resolve("one-full-user.json").writeText(full)
                val plan=com.mavyy.localyuki.inference.PromptPreparation.candidates(ModelRole.SYSTEM_ONE,full,64)
                crowded.resolve("one-full.gbnf").writeText(plan.first().grammar)
                plan.forEachIndexed { i,p->
                    crowded.resolve("one-candidate-$i-user.json").writeText(p.user);crowded.resolve("one-candidate-$i.gbnf").writeText(p.grammar);crowded.resolve("one-candidate-$i-shortened.txt").writeText(p.shortened.toString())
                }
                dir.resolve("language-full.gbnf").writeText(com.mavyy.localyuki.inference.OrganGrammar.forExpression(1))
                for(f in dir.listFiles()!!.filter { it.name.startsWith("two-") || it.name=="language-full.gbnf" })f.copyTo(File(crowded,f.name),true)
            }
        }
    }
    @Test fun productionConversationUsesSeparateVerifiedOrgans() {
        brain().use { brain ->
            assertTrue(brain.open());val input=ok(brain.saveInput("Hello, My Love 🥰"));val turn=ok(brain.context(input))
            val report=System.getenv("YUKI_CONVERSATION_RESULTS")?.let { org.json.JSONObject(File(it).readText()) }
            val responses=report?.getJSONObject("outputs")
                ?:org.json.JSONObject().put("SYSTEM_ONE","{\"route\":\"RESPOND\",\"confidence\":90,\"salience\":30,\"intent\":\"conversation\",\"affect\":\"warmth\",\"meaning\":[\"Hello, Mavyy.\"],\"uncertainty\":[],\"sources\":[\"input\"],\"updates\":[]}")
                    .put("LANGUAGE_EXPRESSION","{\"text\":\"Hello, Mavyy.\",\"pointIds\":[0]}")
            val calls=mutableListOf<ModelRole>();val diagnostic=com.mavyy.localyuki.cognition.ConversationDiagnostics()
            val infer:com.mavyy.localyuki.cognition.CognitiveInference={ role,_,user,_->
                calls+=role
                val supplied=org.json.JSONObject(user)
                if(role==ModelRole.LANGUAGE_EXPRESSION) {
                    assertFalse(supplied.has("ownerInput"));assertFalse(supplied.has("identity"));assertFalse(supplied.has("availableCapabilities"))
                    assertFalse(supplied.has("uncertainty"));assertTrue(supplied.has("uncertain"))
                    assertEquals(1,supplied.getJSONArray("points").length())
                }
                val shortened=report?.optJSONObject("measurements")?.optJSONObject(role.name)?.optBoolean("contextShortened",false)?:false
                com.mavyy.localyuki.inference.OrganResult(responses.getString(role.name),null,1,2,1,1,1,4096,true,64,shortened)
            }
            val one=com.mavyy.localyuki.cognition.NeuralSystemOne(infer,{ brain.conversationHistory() },{ emptyList() },diagnostic)
            val two=com.mavyy.localyuki.cognition.NeuralSystemTwo(infer,{ emptyList() },{ emptyList() },diagnostic)
            val language=com.mavyy.localyuki.cognition.NeuralExpression(infer,diagnostic)
            val reply=ok(TurnCoordinator(RealityVerification(brain.memory.reader(),brain.capabilities),one,language,two,
                com.mavyy.localyuki.cognition.CognitiveComposer(one,two,diagnostic)).respond(turn))
            assertEquals(listOf(ModelRole.SYSTEM_ONE,ModelRole.LANGUAGE_EXPRESSION),calls)
            assertTrue(reply.text.contains("Mavyy"));assertEquals(listOf(input.ref),reply.evidence)
            assertTrue(turn.uncertainty.all { it in reply.text });assertEquals("",diagnostic.reason)
            if(report?.optJSONObject("measurements")?.optJSONObject("SYSTEM_ONE")?.optBoolean("contextShortened",false)==true)
                assertTrue(reply.text.contains("omitted information is unknown"))
            if(responses.has("SYSTEM_TWO")) {
                val question=ok(brain.saveInput("I have a headache. Is its cause certain?"))
                val realReasoning:com.mavyy.localyuki.cognition.CognitiveInference={ role,_,_,_->
                    assertEquals(ModelRole.SYSTEM_TWO,role)
                    com.mavyy.localyuki.inference.OrganResult(responses.getString(role.name),null,1,2,1,1,1,4096,true,38,true)
                }
                val reasoner=com.mavyy.localyuki.cognition.NeuralSystemTwo(realReasoning,{ emptyList() },{ emptyList() },diagnostic)
                val proposal=ok(reasoner.reason(ok(brain.context(question))))
                assertTrue(proposal.plan.isNotBlank());assertEquals(listOf(question.ref),reasoner.sources)
                assertTrue(reasoner.actions.isEmpty());assertTrue(reasoner.uncertainty.any { it.contains("omitted information is unknown") })
            }
        }
    }
    @Test fun conversationFailuresRetainStageAndNeverBecomeYukiEvidence() {
        brain().use { brain ->
            assertTrue(brain.open());val input=ok(brain.saveInput("Hello, Yuki."));val turn=ok(brain.context(input))
            val diagnostic=com.mavyy.localyuki.cognition.ConversationDiagnostics()
            fun result(text:String)=com.mavyy.localyuki.inference.OrganResult(text,null,1,2,1,1,1,4096,true,64)
            val truncated:com.mavyy.localyuki.cognition.CognitiveInference={ _,_,_,_->result("{\"route\":\"RESPOND\"") }
            val broken=com.mavyy.localyuki.cognition.NeuralSystemOne(truncated,{ emptyList() },{ emptyList() },diagnostic)
            assertTrue(broken.decide(turn) is FoundationResult.Failure)
            assertEquals("System One",diagnostic.stage);assertTrue(diagnostic.reason.contains("incomplete"))
            val infer:com.mavyy.localyuki.cognition.CognitiveInference={ role,_,_,_->result(when(role){
                ModelRole.SYSTEM_ONE->"{\"route\":\"RESPOND\",\"confidence\":90,\"salience\":30,\"intent\":\"conversation\",\"affect\":\"warmth\",\"meaning\":[\"Hello, Mavyy.\"],\"uncertainty\":[],\"sources\":[\"input\"],\"updates\":[]}"
                ModelRole.LANGUAGE_EXPRESSION->"{\"text\":\"Hello, Mavyy.\",\"pointIds\":[1]}"
                else->error("Greeting must not call System Two")
            }) }
            val one=com.mavyy.localyuki.cognition.NeuralSystemOne(infer,{ emptyList() },{ emptyList() },diagnostic)
            val two=com.mavyy.localyuki.cognition.NeuralSystemTwo(infer,{ emptyList() },{ emptyList() },diagnostic)
            val expression=com.mavyy.localyuki.cognition.NeuralExpression(infer,diagnostic)
            val reply=TurnCoordinator(RealityVerification(brain.memory.reader(),brain.capabilities),one,expression,two,
                com.mavyy.localyuki.cognition.CognitiveComposer(one,two,diagnostic)).respond(turn)
            assertTrue(reply is FoundationResult.Failure);assertEquals("Language Expression",diagnostic.stage)
            assertTrue(diagnostic.reason.contains("omitted prepared meaning"))
            assertEquals(listOf("USER_INPUT: Hello, Yuki."),brain.conversationHistory())
            assertEquals(input,ok(brain.memory.getEvidence(input.ref)))
        }
    }
    @Test fun unavailableConversationPersistsCopyableStatusAcrossOrdinaryReopen() {
        var status=""
        brain().use { brain ->
            assertTrue(brain.open());val input=ok(brain.saveInput("Hello, Yuki."))
            assertTrue(brain.converse(input) is FoundationResult.Failure)
            status=brain.lastConversationFailure;assertTrue(status.contains("could not complete"))
            assertEquals(input,ok(brain.memory.getEvidence(input.ref)))
            assertEquals(listOf("USER_INPUT: Hello, Yuki."),brain.conversationHistory())
        }
        brain().use { brain ->
            assertTrue(brain.open());assertEquals(status,brain.lastConversationFailure)
            assertEquals(listOf("USER_INPUT: Hello, Yuki."),brain.conversationHistory())
        }
    }
    @Test fun ownerMemoryUpdateRestoreAndEngineReplacementKeepOneContinuity() {
        brain().use { brain ->
            assertTrue(brain.open())
            val original=ok(brain.remember("Forest sunlight song"))
            assertEquals(listOf(original.id),ok(brain.recall("forest")).candidates.map { it.memory.id })
            val updated=ok(brain.updateMemory(original,"Orchard song"))
            assertEquals(2L,updated.current.number)
            val restored=ok(brain.restorePrevious(updated));assertEquals("Forest sunlight song",restored.current.content)
            assertEquals(3L,restored.current.number)
            val input=ok(brain.saveInput("Tell me about the forest"));val turn=ok(brain.context(input))
            val verifier=RealityVerification(brain.memory.reader(),brain.capabilities)
            val one=SystemOneEngine { FoundationResult.Success(Decision(DecisionKind.RESPOND,100)) }
            val identity=turn.identity;val evidence=ok(brain.memory.getEvidence(input.ref))
            val before=ok(brain.memory.getCurrent(original.id))
            val falseIdentity=turn.copy(identity=turn.identity.copy(self=turn.identity.self.copy(canonicalName="Other identity")))
            assertFalse(ok(verifier.verify(falseIdentity)).grounded)
            assertEquals("Engine A",ok(TurnCoordinator(verifier,one,MockLanguageEngine("Engine A")).respond(turn)).text)
            assertEquals("Engine B",ok(TurnCoordinator(verifier,one,MockLanguageEngine("Engine B")).respond(turn)).text)
            assertEquals(identity,ok(brain.context(input)).identity)
            assertEquals(evidence,ok(brain.memory.getEvidence(input.ref)))
            assertEquals(before,ok(brain.memory.getCurrent(original.id)))
            val forged=LanguageExpressionEngine { FoundationResult.Success(Expression("fabricated",listOf(EvidenceRef("not-real",EvidenceSourceKind.AUTHORITY_RECORD)))) }
            assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),TurnCoordinator(verifier,one,forged).respond(turn))
        }
    }
    @Test fun reasoningAndMutatingEnginesCannotRewriteWorkspaceEvidence() {
        brain().use { brain ->
            assertTrue(brain.open());ok(brain.remember("A quiet forest with birds"))
            val turn=ok(brain.context(ok(brain.saveInput("forest"))))
            val before=turn.memories.single()
            val mutating=SystemOneEngine { context ->
                (context.memories.single().surface.terms as MutableList<String>).clear()
                FoundationResult.Success(Decision(DecisionKind.REASON,90))
            }
            val output=ok(TurnCoordinator(RealityVerification(brain.memory.reader(),brain.capabilities),mutating,MockLanguageEngine("Grounded"),MockSystemTwoEngine()).respond(turn))
            assertEquals("Grounded",output.text);assertEquals(before,turn.memories.single())
            val wrong=turn.copy(input="changed words")
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),TurnCoordinator(RealityVerification(brain.memory.reader(),brain.capabilities),MockSystemOneEngine(),MockLanguageEngine("x")).respond(wrong))
        }
    }
    @Test fun languageReceivesPreparedMeaningAfterThinkingAndCannotInventSources() {
        brain().use { brain ->
            assertTrue(brain.open())
            val input=ok(brain.saveInput("What do you think of an apple?"))
            val turn=ok(brain.context(input))
            val order=mutableListOf<String>()
            val decision=SystemOneEngine { order+="decide";FoundationResult.Success(Decision(DecisionKind.REASON,100)) }
            val reasoning=SystemTwoEngine { order+="reason";FoundationResult.Success(ReasoningProposal("Recall the apple association.",listOf(input.ref))) }
            val composer=MeaningComposer { context,_ ->
                order+="meaning"
                assertEquals("Recall the apple association.",context.reasoning)
                FoundationResult.Success(PreparedMeaning(listOf("An apple brings an orchard to mind."),listOf(input.ref),setOf("This is an association, not a recalled event.")))
            }
            val language=LanguageExpressionEngine { request ->
                order+="words"
                assertEquals(listOf("An apple brings an orchard to mind."),request.meaning.points)
                assertEquals(setOf("This is an association, not a recalled event."),request.meaning.uncertainty)
                FoundationResult.Success(Expression("Apples make me think of orchards.",request.meaning.evidence))
            }
            val coordinator=TurnCoordinator(RealityVerification(brain.memory.reader(),brain.capabilities),decision,language,reasoning,composer)
            assertEquals("Apples make me think of orchards.",ok(coordinator.respond(turn)).text)
            assertEquals(listOf("decide","reason","meaning","words"),order)
            var expressed=false
            val badComposer=MeaningComposer { _,_ -> FoundationResult.Success(PreparedMeaning(listOf("Invented fact"),listOf(EvidenceRef("invented",EvidenceSourceKind.AUTHORITY_RECORD)))) }
            val forbiddenLanguage=LanguageExpressionEngine { expressed=true;FoundationResult.Success(Expression("x",listOf(input.ref))) }
            assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),TurnCoordinator(
                RealityVerification(brain.memory.reader(),brain.capabilities),decision,forbiddenLanguage,reasoning,badComposer).respond(turn))
            assertFalse(expressed)
            assertEquals(input,ok(brain.memory.getEvidence(input.ref)))
        }
    }
    @Test fun grantedLocalNotesExecuteAndReplayExactlyOnce() {
        brain().use { brain ->assertTrue(brain.open())
            val intent=ActionIntent("note-action",CapabilityId.LOCAL_NOTE,"Saved tool note")
            assertTrue(brain.executeNote(intent) is FoundationResult.Unavailable)
            ok(brain.setNotesEnabled(true))
            val first=ok(brain.executeNote(intent));val replay=ok(brain.executeNote(intent))
            assertEquals(first,replay)
            val verifier=RealityVerification(brain.memory.reader(),brain.capabilities)
            assertTrue(ok(verifier.verifyTool(first)).grounded)
            assertFalse(ok(verifier.verifyTool(first.copy(actionId="forged-action"))).grounded)
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),brain.executeNote(intent.copy(payload="different")))
            ok(brain.sleep());assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),brain.executeNote(intent))
        }
    }
    private fun archive(): ByteArray {
        brain().use { assertTrue(it.open());ok(it.remember("Forest archive evidence")) }
        val output=ByteArrayOutputStream();ok(ContinuityVault(context).export(output,password));return output.toByteArray()
    }
    @Test fun encryptedVaultRestoresIntoNewBodyWithoutAnyEngineWeights() {
        val bytes=archive();assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Forest archive evidence"))
        context.deleteDatabase(ContinuitySchema.NAME)
        assertTrue(ok(ContinuityVault(context).restore(ByteArrayInputStream(bytes),password)))
        brain().use { assertTrue(it.open());val memory=ok(it.memory.currentMemories(null,10)).single()
            assertEquals("Forest archive evidence",memory.current.content)
            assertEquals("yuki-aster",ok(it.context(ok(it.saveInput("hello")))).identity.self.stableId)
        }
    }
    @Test fun wrongPasswordTamperingAndUnknownSchemaNeverReplaceExistingContinuity() {
        val bytes=archive()
        val target=context.getDatabasePath(ContinuitySchema.NAME)
        val before=MessageDigest.getInstance("SHA-256").digest(target.readBytes()).toList()
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),ContinuityVault(context).restore(ByteArrayInputStream(bytes),"wrong-password-here".toCharArray()))
        val changed=bytes.copyOf();changed[changed.lastIndex]=(changed.last().toInt() xor 1).toByte()
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),ContinuityVault(context).restore(ByteArrayInputStream(changed),password))
        assertEquals(before,MessageDigest.getInstance("SHA-256").digest(target.readBytes()).toList())
        db().use { it.execSQL("CREATE TABLE forbidden_extra (value TEXT)") }
        val invalid=ByteArrayOutputStream();ok(ContinuityVault(context).export(invalid,password))
        db().use { it.execSQL("DROP TABLE forbidden_extra") }
        val clean=target.readBytes().toList()
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),ContinuityVault(context).restore(ByteArrayInputStream(invalid.toByteArray()),password))
        assertEquals(clean,target.readBytes().toList())
    }
    @Test fun modelHashFormatInspectionAndOversizeImportAreBounded() {
        val file=File(context.cacheDir,"candidate.gguf")
        val header=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(0x47,0x47,0x55,0x46)).putInt(3).putLong(1).putLong(1).putLong(0).array()
        file.writeBytes(header)
        val model=ok(ModelFiles.inspect(file,"candidate",ModelRole.SYSTEM_ONE))
        assertEquals(ModelFormat.GGUF,model.format)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(header).joinToString("") { "%02x".format(it) },model.sha256)
        val destination=File(context.cacheDir,"too-big-model");destination.delete();File(destination.parentFile,"${destination.name}.partial").delete()
        assertEquals(FoundationResult.Failure(FailureCategory.REJECTED),ModelFiles.import(ByteArrayInputStream(ByteArray(16)),destination,8))
        assertFalse(destination.exists());assertFalse(File(destination.parentFile,"${destination.name}.partial").exists())
        file.delete()
    }
    @Test fun malformedModelIsInspectableButNeverAdmittedAndHashChangesAreRejected() {
        brain().use { assertTrue(it.open()) }
        val header=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(0x47,0x47,0x55,0x46)).putInt(3).putLong(1).putLong(1).putLong(0).array()
        ModelSubsystem(context,ResourceGovernor()).use { models ->
            val model=ok(models.importFile(ByteArrayInputStream(header),ModelRole.SYSTEM_ONE,now))
            assertEquals("INCOMPATIBLE",model.manifest.getString("status"))
            assertTrue(models.admit(model.id,ModelRole.SYSTEM_ONE,now) is FoundationResult.Failure)
            assertNull(models.activeHash(ModelRole.SYSTEM_ONE))
            File(context.filesDir,"model-organs/${model.id}").appendBytes(byteArrayOf(1))
            assertTrue(models.admit(model.id,ModelRole.SYSTEM_ONE,now) is FoundationResult.Failure)
            assertTrue(models.lastFailure.contains("hash/size changed"))
        }
        db().use { sql->sql.rawQuery("SELECT COUNT(*) FROM organ_receipt WHERE accepted=0",null).use { it.moveToFirst();assertEquals(2,it.getInt(0)) } }
    }
}
