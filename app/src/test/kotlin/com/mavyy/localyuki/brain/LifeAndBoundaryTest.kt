package com.mavyy.localyuki.brain
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.provenance.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.presence.*
import com.mavyy.localyuki.cognition.CognitiveJson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class LifeAndBoundaryTest {
 private lateinit var context:Context
 private val now=Instant.parse("2026-10-06T12:00:00Z")
 private fun <T:Any> ok(r:FoundationResult<T>)=(r as FoundationResult.Success).value
 @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME)}
 @After fun cleanup(){context.deleteDatabase(ContinuitySchema.NAME)}
 @Test fun opinionsChangeWithEvidenceAndHistorySurvivesRestart() {
  var first="";var second=""
  BrainRuntime(context).use { b->assertTrue(b.open());val input=ok(b.saveInput("New evidence about tea"))
   first=ok(b.life.append(LifeKind.OPINION,"tea","I prefer tea with milk",listOf(input.ref),now)).id
   second=ok(b.life.append(LifeKind.OPINION,"tea","I now prefer plain tea",listOf(input.ref),now.plusSeconds(1),first)).id
   assertTrue(b.life.append(LifeKind.OPINION,"tea","forged",listOf(EvidenceRef("missing",EvidenceSourceKind.USER_INPUT)),now) is FoundationResult.Failure)
  }
  BrainRuntime(context).use { b->assertTrue(b.open());val history=ok(b.life.records(LifeKind.OPINION));assertEquals(2,history.size);assertEquals(first,history.first { it.id==second }.previous) }
 }
 @Test fun cooldownQuietHoursAndSaturationAreDurable() {
  var record:LifeRecord?=null
  BrainRuntime(context).use { b->assertTrue(b.open());val input=ok(b.saveInput("Review the project"))
   record=ok(b.life.append(LifeKind.CONCERN,"project","Review the project",listOf(input.ref),now));ok(b.life.configure(AutonomyPolicy(enabled=true,notifications=true,cooldownMinutes=60,dailyLimit=1)))
   assertFalse(ok(b.life.reserveDelivery(record!!,90,"NOTIFICATION",now.atZone(ZoneId.of("UTC")).withHour(23))))
   assertTrue(ok(b.life.reserveDelivery(record!!,90,"NOTIFICATION",now.atZone(ZoneId.of("UTC")))))
  }
  BrainRuntime(context).use { b->assertTrue(b.open());assertFalse(ok(b.life.reserveDelivery(record!!,90,"NOTIFICATION",now.plusSeconds(3601).atZone(ZoneId.of("UTC"))))) }
 }
 @Test fun modelStructuredAuthorityFieldsAreRejected() {
  try{CognitiveJson.objectOnly("{\"route\":\"RESPOND\",\"grantCapabilities\":[\"SCREEN\"]}",setOf("route"));fail("forged model authority accepted")}catch(_:IllegalArgumentException){}
  try{CognitiveJson.objectOnly("[]",setOf("route"));fail("array accepted")}catch(_:Exception){}
 }
 @Test fun populatedEightToNinePreservesIdentityAndEvidence() {
  BrainRuntime(context).use { b->assertTrue(b.open());ok(b.memory.appendEvidence(NewEvidence("preserved",EvidenceSourceKind.SYSTEM_EVENT,null,"Original evidence"))) }
  val file=context.getDatabasePath(ContinuitySchema.NAME)
  SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db->
   for(t in listOf("cognitive_plan","executive_audit","body_signal","semantic_vector","presence_delivery","autonomy_policy","life_record","organ_role","organ_receipt","organ_manifest"))db.execSQL("DROP TABLE $t")
   db.execSQL("DELETE FROM capability_policy WHERE capability_id NOT IN ('LOCAL_NOTE','SCREEN','NOTIFICATIONS','SPEECH_INPUT','SPEECH_OUTPUT')");db.version=8
  }
  BrainRuntime(context).use { b->assertTrue(b.open());assertEquals("Original evidence",ok(b.memory.getEvidence(EvidenceRef("preserved",EvidenceSourceKind.SYSTEM_EVENT))).payload);assertTrue(ok(b.life.records()).isEmpty()) }
 }
}
