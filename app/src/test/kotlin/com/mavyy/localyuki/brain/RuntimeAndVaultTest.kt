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
