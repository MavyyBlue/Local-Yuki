package com.mavyy.localyuki.brain
import android.content.Context
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.admission.*
import com.mavyy.localyuki.foundation.admission.ModelRole
import com.mavyy.localyuki.foundation.contracts.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
/** Tests the trusted commit ledger, not an inference/admission success for fixture weights. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class AdmissionLedgerTest {
 private lateinit var context:Context
 private fun <T:Any> ok(r:FoundationResult<T>)=(r as FoundationResult.Success).value
 @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME)}
 @After fun cleanup(){context.deleteDatabase(ContinuitySchema.NAME)}
 private fun prepare(b:BrainRuntime):OwnerModel {val j=JSONObject().put("status","INSPECTED");BrainDatabase(context).use {db->ok(db.write {it.execSQL("INSERT INTO organ_manifest VALUES(?,?)",arrayOf("ledger-fixture",j.toString()));true})};return OwnerModel("ledger-fixture",j.put("status","ADMITTED"),emptySet())}
 @Test fun receiptFailureRollsBackRoleAndManifest(){BrainRuntime(context).use {b->assertTrue(b.open());val candidate=prepare(b);BrainDatabase(context).use {db->ok(db.write {it.execSQL("CREATE TRIGGER reject_receipt BEFORE INSERT ON organ_receipt BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END");true})};assertTrue(b.models.publishAdmission(candidate,ModelRole.SYSTEM_ONE,Instant.now(),"fixture ledger only") is FoundationResult.Failure);BrainDatabase(context).use {db->assertTrue(ok(db.read {db.rows(it,"SELECT role FROM organ_role")} ).isEmpty());assertEquals("INSPECTED",JSONObject(ok(db.read {db.rows(it,"SELECT json FROM organ_manifest WHERE id='ledger-fixture'")}).single().single()!!).getString("status"))}}}
 @Test fun successfulCommitPersistsReceiptAndRoleTogetherAndReceiptIsImmutable(){BrainRuntime(context).use {b->assertTrue(b.open());val candidate=prepare(b);ok(b.models.publishAdmission(candidate,ModelRole.SYSTEM_ONE,Instant.now(),"fixture ledger only"));BrainDatabase(context).use {db->assertEquals(1,ok(db.read {db.rows(it,"SELECT accepted FROM organ_receipt")} ).size);assertEquals(1,ok(db.read {db.rows(it,"SELECT role FROM organ_role")} ).size);assertTrue(db.write {it.execSQL("DELETE FROM organ_receipt");true} is FoundationResult.Failure)}}}
}
