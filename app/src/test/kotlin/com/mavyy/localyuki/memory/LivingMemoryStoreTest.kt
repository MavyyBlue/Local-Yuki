package com.mavyy.localyuki.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.temporal.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class LivingMemoryStoreTest {
    private lateinit var context: Context
    private var instant = Instant.parse("2026-09-25T12:00:00Z")
    private var available = true
    private val temporal = object : TemporalGroundingReader {
        override fun ground(): FoundationResult<TemporalGroundingSnapshot> = if (!available)
            FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE) else
            FoundationResult.Success(TemporalGroundingSnapshot(instant,ZoneId.of("UTC"),instant.atZone(ZoneId.of("UTC")).toLocalDateTime()))
        override fun resolve(query: CalendarQuery): FoundationResult<ResolvedTimeWindow> = FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE)
    }
    @Before fun setup() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase(ContinuitySchema.NAME); available=true; instant=Instant.parse("2026-09-25T12:00:00Z") }
    @After fun clean() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun db() = SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,SQLiteDatabase.OPEN_READWRITE)
    private fun <T : Any> ok(result: FoundationResult<T>): T = (result as FoundationResult.Success<T>).value
    private val e1 get() = EvidenceRef("e1",EvidenceSourceKind.USER_INPUT)
    private val e2 get() = EvidenceRef("e2",EvidenceSourceKind.USER_INPUT)
    private fun seed(store: MemoryStore) {
        store.open(); ok(store.createThread("thread"))
        ok(store.appendEvidence(NewEvidence("e1",EvidenceSourceKind.USER_INPUT,"thread","Original evidence")))
        ok(store.appendEvidence(NewEvidence("e2",EvidenceSourceKind.USER_INPUT,"thread","Owner correction")))
        ok(store.create(CreateMemory("cmd-a","memory","rev-a",MemoryKind.FACTUAL,"Forest forest sunlight sunlight song",listOf(e1))))
    }
    @Test fun indexedLexicalRecallAgingMeaningfulRecallAndStaleHead() {
        MemoryStore(context,temporal).use { deep ->
            seed(deep)
            LivingMemoryStore(context,temporal,deep.reader()).use { living ->
                assertEquals(LivingMemoryStore.Start.INITIALIZED,living.open())
                assertEquals(IndexCoverage.PARTIAL,ok(living.coverage()))
                assertEquals("rev-a",ok(living.reconcilePage(null,10)).single().sourceRevisionId)
                assertEquals(IndexCoverage.COMPLETE,ok(living.coverage()))
                DeterministicLexicalRecallEngine(context,deep.reader(),living.reader()).use { lexical ->
                    val before=ok(living.current("memory"))
                    assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),
                        living.setImportance("memory",before.stateRevision+1,75))
                    val recalled=ok(lexical.recall(RecallQuery("FOREST",5)))
                    assertEquals(IndexCoverage.PARTIAL,recalled.coverage)
                    assertEquals(listOf("memory"),recalled.candidates.map { it.memory.id })
                    assertTrue(ok(lexical.recall(RecallQuery("unmatchedword",5))).candidates.isEmpty())
                    assertTrue(ok(lexical.recall(RecallQuery("forest",5,MemoryKind.AUTOBIOGRAPHICAL))).candidates.isEmpty())
                    assertEquals(0L,ok(living.current("memory")).meaningfulRecallCount)
                    assertTrue(ok(lexical.ground(listOf(RecallCandidateHint("missing","rev-a",1,listOf("forest"),RecallBasis.MOCK)))).candidates.isEmpty())
                    assertTrue(ok(lexical.ground(listOf(RecallCandidateHint("memory","missing-revision",1,listOf("forest"),RecallBasis.MOCK)))).candidates.isEmpty())
                    assertEquals(before.stateRevision+1,ok(living.recordMeaningfulRecall("event-1","memory","rev-a",before.stateRevision)))
                    assertEquals(before.stateRevision+1,ok(living.recordMeaningfulRecall("event-1","memory","rev-a",before.stateRevision)))
                    assertEquals(1L,ok(living.current("memory")).meaningfulRecallCount)
                    assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),living.recordMeaningfulRecall("event-1","memory","rev-a",before.stateRevision+1))
                    val oldEvidence=ok(deep.getEvidence(e1))
                    instant=instant.plus(Duration.ofDays(400))
                    val aged=ok(living.reconcilePage(null,10)).single()
                    assertEquals(AbstractionLevel.TRACE,aged.abstraction)
                    assertTrue(aged.terms.size<=6)
                    assertEquals(oldEvidence,ok(deep.getEvidence(e1)))
                    assertEquals("Forest forest sunlight sunlight song",ok(deep.getCurrent("memory")).current.content)
                    ok(deep.ownerUpdate(OwnerUpdate("cmd-b","memory","rev-b","rev-a","New orchard story",e2)))
                    assertEquals(IndexCoverage.PARTIAL,ok(living.coverage()))
                    assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),living.current("memory"))
                    assertTrue(ok(lexical.recall(RecallQuery("forest",5))).candidates.isEmpty())
                    assertTrue(ok(lexical.ground(listOf(RecallCandidateHint("memory","rev-a",1,listOf("forest"),RecallBasis.MOCK)))).candidates.isEmpty())
                    val fresh=ok(living.reconcilePage(null,10)).single()
                    assertEquals(IndexCoverage.COMPLETE,ok(living.coverage()))
                    assertEquals("rev-b",fresh.sourceRevisionId)
                    assertEquals(MemoryStatus.HISTORICAL,ok(living.episode("memory","rev-a")).status)
                    assertEquals(listOf(e1),ok(living.episode("memory","rev-a")).evidence)
                    assertEquals(listOf("memory"),ok(lexical.recall(RecallQuery("orchard",5))).candidates.map { it.memory.id })
                }
            }
        }
    }
    @Test fun corruptionIsolationClockDependencyAndRestart() {
        MemoryStore(context,temporal).use { deep ->
            seed(deep)
            LivingMemoryStore(context,temporal,deep.reader()).use { living -> living.open(); ok(living.reconcilePage(null,2)) }
        }
        available=false
        MemoryStore(context,temporal).use { deep ->
            assertEquals(MemoryStore.Start.RESTORED,deep.open())
            LivingMemoryStore(context,temporal,deep.reader()).use { living ->
                assertEquals(LivingMemoryStore.Start.RESTORED,living.open())
                assertEquals("rev-a",ok(living.current("memory")).sourceRevisionId)
                assertEquals(FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE),living.reconcilePage(null,1))
                assertEquals(FoundationResult.Unavailable(UnavailableReason.DEPENDENCY_UNAVAILABLE),
                    living.recordMeaningfulRecall("clock-off","memory","rev-a",1))
            }
        }
        db().use { it.execSQL("DELETE FROM living_memory_metadata") }
        MemoryStore(context,temporal).use { deep ->
            assertEquals("rev-a",ok(deep.getCurrent("memory")).current.id)
            LivingMemoryStore(context,temporal,deep.reader()).use { living -> assertEquals(LivingMemoryStore.Start.UNAVAILABLE,living.open()) }
        }
    }
    @Test fun sequentialBoundedPagesEarnCompleteCoverageWithoutSkipping() {
        MemoryStore(context,temporal).use { deep ->
            seed(deep)
            ok(deep.create(CreateMemory("cmd-z","z-memory","rev-z",MemoryKind.AUTOBIOGRAPHICAL,"Later song",listOf(e1))))
            LivingMemoryStore(context,temporal,deep.reader()).use { living ->
                living.open()
                assertEquals("memory",ok(living.reconcilePage(null,1)).single().memoryId)
                assertEquals(IndexCoverage.PARTIAL,ok(living.coverage()))
                assertEquals("z-memory",ok(living.reconcilePage("memory",1)).single().memoryId)
                assertEquals(IndexCoverage.PARTIAL,ok(living.coverage()))
                assertTrue(ok(living.reconcilePage("z-memory",1)).isEmpty())
                assertEquals(IndexCoverage.COMPLETE,ok(living.coverage()))
            }
        }
    }
    @Test fun productionLexicalQueryUsesTermIndexWithoutTemporarySort() {
        MemoryStore(context,temporal).use { seed(it) }
        db().use { sql ->
            for ((query,args) in listOf(
                DeterministicLexicalRecallEngine.TERM_QUERY to arrayOf("forest","200"),
                DeterministicLexicalRecallEngine.KIND_TERM_QUERY to arrayOf("forest","FACTUAL","200")))
            sql.rawQuery("EXPLAIN QUERY PLAN $query",args).use { c ->
                val plan=buildList { while (c.moveToNext()) add(c.getString(3)) }
                assertTrue(plan.toString(),plan.any { "idx_living_term_lookup" in it })
                assertFalse(plan.toString(),plan.any { "TEMP B-TREE" in it.uppercase() || "SCAN living_memory_term" in it })
            }
        }
    }
    @Test fun rerankerMustReturnExactlyTheGroundedIdentities() {
        MemoryStore(context,temporal).use { deep ->
            seed(deep)
            LivingMemoryStore(context,temporal,deep.reader()).use { living ->
                living.open(); ok(living.reconcilePage(null,1))
                val memory=ok(deep.getCurrent("memory"))
                val surface=ok(living.current("memory"))
                val one=GroundedRecallCandidate(memory,surface,1,listOf("forest"),RecallBasis.LEXICAL)
                val two=one.copy(memory=memory.copy(id="other"))
                assertTrue(RecallRerankBoundary.validate(listOf(one,two),listOf(two,one)) is FoundationResult.Success)
                assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),RecallRerankBoundary.validate(listOf(one,two),listOf(one,one)))
                assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),RecallRerankBoundary.validate(listOf(one,two),listOf(one)))
                assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),RecallRerankBoundary.validate(listOf(one),listOf(two)))
                val forged=one.copy(memory=memory.copy(current=memory.current.copy(content="forged")))
                assertEquals(one,ok(RecallRerankBoundary.validate(listOf(one),listOf(forged))).single())
            }
        }
    }
}
