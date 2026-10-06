package com.mavyy.localyuki.brain
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.continuity.*
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.resource.*
import com.mavyy.localyuki.foundation.temporal.*
import com.mavyy.localyuki.presence.*
import org.junit.*;import org.junit.Assert.*;import org.junit.runner.RunWith
import org.robolectric.*;import org.robolectric.annotation.Config
import java.time.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class AutonomousPlanTest {
 private lateinit var context:Context
 private var now=Instant.parse("2026-10-06T12:00:00Z")
 private val time=DeterministicTemporalGrounding(ClockSource { now },ZoneSource { ZoneId.of("UTC") })
 private fun brain()=BrainRuntime(context,time,{ fg,at->FoundationResult.Success(DeviceResources(4096*ResourceGovernor.MIB,2048*ResourceGovernor.MIB,80,true,ThermalPressure.NORMAL,fg,at)) })
 private fun <T:Any> ok(r:FoundationResult<T>)=(r as FoundationResult.Success).value
 @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME)}
 @After fun cleanup(){context.deleteDatabase(ContinuitySchema.NAME)}
 @Test fun scheduledActionSurvivesRestartAndRunsOnceWithEvidence() {
  brain().use { b->assertTrue(b.open());val input=ok(b.saveInput("Make a note later"));ok(b.setNotesEnabled(true));ok(b.life.configure(AutonomyPolicy(enabled=true)))
   ok(b.plans.enqueue(ActionIntent("later",CapabilityId.LOCAL_NOTE,"Deferred note"),now.plusSeconds(60),input.ref,now));assertEquals(0,ok(b.executeDuePlans())) }
  now=now.plusSeconds(61)
  brain().use { b->assertTrue(b.open());assertEquals(1,ok(b.executeDuePlans()));assertEquals(0,ok(b.executeDuePlans()));val plan=ok(b.plans.list()).single();assertEquals("SUCCEEDED",plan.status)
   val ref=ToolEvidenceIdentity.ref("later",CapabilityId.LOCAL_NOTE);assertEquals("Deferred note",ok(b.memory.getEvidence(ref)).payload) }
 }
 @Test fun revokedOwnerGrantAndQuietHoursBlockDeferredDispatch() {
  brain().use { b->assertTrue(b.open());val input=ok(b.saveInput("Make a note later"));ok(b.life.configure(AutonomyPolicy(enabled=true)));ok(b.setNotesEnabled(true))
   ok(b.plans.enqueue(ActionIntent("revoked",CapabilityId.LOCAL_NOTE,"Do not execute"),now.plusSeconds(60),input.ref,now));ok(b.setNotesEnabled(false));now=now.plusSeconds(61)
   assertEquals(0,ok(b.executeDuePlans()));assertEquals("BLOCKED",ok(b.plans.list()).single().status)
   assertTrue(b.memory.getEvidence(ToolEvidenceIdentity.ref("revoked",CapabilityId.LOCAL_NOTE)) !is FoundationResult.Success) }
 }
 @Test fun interruptedDispatchIsUnknownAfterRestartAndNeverReplayed() {
  brain().use { b->assertTrue(b.open());val input=ok(b.saveInput("Later note"));ok(b.setNotesEnabled(true));ok(b.life.configure(AutonomyPolicy(enabled=true)))
   ok(b.plans.enqueue(ActionIntent("interrupted",CapabilityId.LOCAL_NOTE,"May have executed"),now.plusSeconds(60),input.ref,now));assertTrue(ok(b.plans.claim("interrupted"))) }
  now=now.plusSeconds(61);brain().use { b->assertTrue(b.open());assertEquals("UNKNOWN",ok(b.plans.list()).single().status);assertEquals(0,ok(b.executeDuePlans())) }
 }
 @Test fun authoritySurfacesAndUnavailableAccessibilityFailClosed() {
  val body=com.mavyy.localyuki.embodiment.AndroidBody(context)
  try{body.execute(ActionIntent("forged",CapabilityId.UI_INTERACTION,"{\"op\":\"click\",\"viewId\":\"enable\"}","com.mavyy.localyuki"));fail("Authority surface allowed")}catch(_:IllegalArgumentException){}
  try{body.execute(ActionIntent("cross-capability",CapabilityId.DEVICE_NAVIGATION,"{\"op\":\"click\",\"viewId\":\"enable\"}","example.app"));fail("Navigation bypassed interaction policy")}catch(_:IllegalArgumentException){}
  try{body.execute(ActionIntent("missing",CapabilityId.SCREEN,"{\"op\":\"inspect\"}","example.app"));fail("Missing Accessibility supplied perception")}catch(_:Exception){}
 }
}
