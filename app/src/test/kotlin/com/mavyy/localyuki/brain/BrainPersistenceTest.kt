package com.mavyy.localyuki.brain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.affect.AffectStore
import com.mavyy.localyuki.embodiment.CapabilityStore
import com.mavyy.localyuki.recovery.RecoveryStore
import com.mavyy.localyuki.scheduler.SchedulerStore
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.memory.MemoryStore
import com.mavyy.localyuki.state.YukiStateStore
import com.mavyy.localyuki.foundation.affect.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.recovery.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.scheduler.*
import com.mavyy.localyuki.foundation.state.*
import com.mavyy.localyuki.foundation.temporal.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class BrainPersistenceTest {
    private lateinit var context: Context
    private var now=Instant.parse("2026-10-02T12:00:00Z")
    private var clockAvailable=true
    private val temporal=DeterministicTemporalGrounding(ClockSource { if(!clockAvailable) throw IllegalStateException();now },ZoneSource { ZoneId.of("UTC") })
    private val ref=EvidenceRef("input",EvidenceSourceKind.USER_INPUT)
    @Before fun setup() { context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME) }
    @After fun cleanup() { context.deleteDatabase(ContinuitySchema.NAME) }
    private fun <T:Any> ok(result: FoundationResult<T>):T=(result as FoundationResult.Success).value
    private fun seed(memory: MemoryStore) { memory.open();ok(memory.createThread("thread"));ok(memory.appendEvidence(NewEvidence("input",EvidenceSourceKind.USER_INPUT,"thread","A shared project")))
        ok(memory.create(CreateMemory("create","memory","revision",MemoryKind.FACTUAL,"A shared project",listOf(ref)))) }
    private fun db()=SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,SQLiteDatabase.OPEN_READWRITE)
    @Test fun affectPersistsAssociationsAndReplayWithoutRewritingIdentityOrEvidence() {
        MemoryStore(context,temporal).use { memory ->seed(memory)
            AffectStore(context,temporal,memory.reader()).use { affect ->
                assertFalse(ok(affect.open()));val event=AffectEvent("positive",0,AffectEventKind.POSITIVE,ref,"memory")
                assertEquals(1L,ok(affect.apply(event)));clockAvailable=false
                assertEquals(1L,ok(affect.apply(event)))
                assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),affect.apply(event.copy(kind=AffectEventKind.NEGATIVE)))
                assertEquals(10,ok(affect.associations()).single().strength)
            }
            AffectStore(context,temporal,memory.reader()).use { affect ->
                assertTrue(ok(affect.open()));assertEquals(32,ok(affect.read()).vector.valence)
                clockAvailable=true;now=now.plusSeconds(100*3600);ok(affect.normalize());assertEquals(AffectPolicy.baseline,ok(affect.read()).vector)
            }
            assertEquals("A shared project",ok(memory.getEvidence(ref)).payload)
            assertEquals("A shared project",ok(memory.getCurrent("memory")).current.content)
        }
        ContinuityStore(context).use { assertEquals(ContinuityStart.RESTORED,it.open().status) }
    }
    @Test fun corruptAffectIsIsolatedAndNotReinitialized() {
        MemoryStore(context,temporal).use { memory ->seed(memory)
            AffectStore(context,temporal,memory.reader()).use { ok(it.open()) }
            db().use { it.execSQL("UPDATE affect_state SET updated_at='invalid'") }
            AffectStore(context,temporal,memory.reader()).use { assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),it.open()) }
            assertEquals("revision",ok(memory.getCurrent("memory")).current.id)
        }
    }
    @Test fun ownerPoliciesPersistDenialsWinAndEnablingDoesNotFabricateSupport() {
        CapabilityStore(context,{ true to true },setOf(CapabilityId.LOCAL_NOTE)).use { registry ->
            assertFalse(ok(registry.permits(CapabilityId.LOCAL_NOTE,null)))
            ok(registry.configure(CapabilityId.LOCAL_NOTE,0,CapabilityPolicy(true,setOf("allowed.app"),setOf("allowed.app","private.app"))))
            assertFalse(ok(registry.permits(CapabilityId.LOCAL_NOTE,"allowed.app")))
            assertFalse(ok(registry.permits(CapabilityId.LOCAL_NOTE,"other.app")))
            assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),registry.configure(CapabilityId.LOCAL_NOTE,0,CapabilityPolicy(true)))
            ok(registry.configure(CapabilityId.SCREEN,0,CapabilityPolicy(true)))
            assertFalse(ok(registry.capabilities()).single { it.id==CapabilityId.SCREEN }.available)
        }
        CapabilityStore(context,{ false to false },setOf(CapabilityId.LOCAL_NOTE)).use { registry ->
            assertTrue(ok(registry.setting(CapabilityId.LOCAL_NOTE)).policy.enabled)
            assertFalse(ok(registry.permits(CapabilityId.LOCAL_NOTE,"allowed.app")))
        }
    }
    @Test fun sleepCheckpointsIntentionsAndInterruptedMaintenanceRestartsSafely() {
        YukiStateStore(context,temporal).use { state ->state.open()
            ok(state.mutate(StateCommand(0,StateChange.PutIntention("pending","Keep this intention"))))
            RecoveryStore(context,temporal,state.reader()).use { recovery ->
                val start=ok(recovery.open());val preparing=ok(recovery.transition(start.revision,RecoveryMode.PREPARING_SLEEP))
                assertEquals("pending",preparing.pendingIntentions.single().id)
                val sleep=ok(recovery.transition(preparing.revision,RecoveryMode.SLEEPING))
                assertEquals(FoundationResult.Failure(FailureCategory.CONFLICT),recovery.transition(sleep.revision,RecoveryMode.AWAKE))
                assertFalse(ok(recovery.maintain(listOf({ FoundationResult.Failure(FailureCategory.CONFLICT) }))))
            }
            db().use { it.execSQL("UPDATE recovery_state SET maintenance_active=1") }
            RecoveryStore(context,temporal,state.reader()).use { recovery ->
                val restored=ok(recovery.open());assertEquals(RecoveryMode.RECOVERING,restored.mode)
                assertEquals("pending",restored.pendingIntentions.single().id)
                assertEquals("pending",ok(state.reader().read()).intentions.single().id)
                assertFalse(restored.maintenanceInterrupted)
            }
        }
    }
    @Test fun schedulerCooldownSurvivesRestartAndDoesNotConsumeSkippedEscalation() {
        val signal=BackgroundSignal("signal1",SignalKind.FOREGROUND_DURATION,now,1800)
        val decision=ok(DeterministicSalienceEngine().evaluate(signal))
        SchedulerStore(context).use { scheduler ->
            assertFalse(ok(scheduler.observe(signal,decision,now,Engagement.RECOVERY)))
            assertTrue(ok(scheduler.observe(signal.copy(id="signal2"),decision,now,Engagement.ATTENTIVE)))
        }
        SchedulerStore(context).use { scheduler ->
            assertFalse(ok(scheduler.observe(signal.copy(id="signal3"),decision,now,Engagement.ATTENTIVE)))
            now=now.plusSeconds(900)
            assertTrue(ok(scheduler.observe(signal.copy(id="signal4",capturedAt=now),decision,now,Engagement.ATTENTIVE)))
        }
    }
    @Test fun phaseFiveUpgradePreservesDataAndLaterFailureRollsBackAllSteps() {
        MemoryStore(context,temporal).use(::seed)
        db().use { sql -> com.mavyy.localyuki.continuity.dropAfterPhaseFive(sql);sql.version=4 }
        AffectStore(context,temporal,MemoryStore(context,temporal).reader()).use { assertFalse(ok(it.open())) }
        db().use { sql ->
            assertEquals(ContinuitySchema.VERSION,sql.version)
            sql.rawQuery("SELECT payload FROM memory_evidence WHERE evidence_id='input'",null).use { assertTrue(it.moveToFirst());assertEquals("A shared project",it.getString(0)) }
            sql.rawQuery("SELECT count(*) FROM continuity_migration_history",null).use { it.moveToFirst();assertEquals(ContinuitySchema.VERSION-4,it.getInt(0)) }
        }
        context.deleteDatabase(ContinuitySchema.NAME)
        MemoryStore(context,temporal).use(::seed)
        db().use { sql ->
            com.mavyy.localyuki.continuity.dropAfterPhaseFive(sql)
            sql.execSQL("CREATE TABLE capability_policy (blocker TEXT)");sql.version=4
        }
        AffectStore(context,temporal,MemoryStore(context,temporal).reader()).use { assertTrue(it.open() is FoundationResult.Failure) }
        db().use { sql ->
            assertEquals(4,sql.version)
            sql.rawQuery("SELECT name FROM sqlite_master WHERE name='affect_state'",null).use { assertEquals(0,it.count) }
            sql.rawQuery("SELECT payload FROM memory_evidence WHERE evidence_id='input'",null).use { it.moveToFirst();assertEquals("A shared project",it.getString(0)) }
            sql.rawQuery("SELECT count(*) FROM continuity_migration_history",null).use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        }
    }

    @Test fun androidMaintenanceIsOwnerControlledChargingOnlyAndCoarse() {
        val control=com.mavyy.localyuki.scheduler.BackgroundMaintenance
        assertTrue(control.configure(context,false));assertFalse(control.enabled(context))
        assertTrue(control.configure(context,true));assertTrue(control.enabled(context))
        val scheduler=context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
        val job=scheduler.allPendingJobs.single { it.id==control.JOB_ID }
        assertTrue(job.isRequireCharging);assertTrue(job.isRequireBatteryNotLow)
        assertEquals(15*60*1000L,job.intervalMillis);assertFalse(job.isPersisted)
        assertTrue(control.configure(context,false))
        assertFalse(scheduler.allPendingJobs.any { it.id==control.JOB_ID })
    }

}
