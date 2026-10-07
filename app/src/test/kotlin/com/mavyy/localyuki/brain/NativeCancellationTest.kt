package com.mavyy.localyuki.brain
import android.content.ComponentCallbacks2
import com.mavyy.localyuki.YukiApplication
import com.mavyy.localyuki.inference.NativeSupervisor
import com.mavyy.localyuki.admission.ModelSubsystem
import com.mavyy.localyuki.foundation.resource.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=YukiApplication::class)
class NativeCancellationTest {
 @Test fun turnCannotReplaceAnAlreadyCancelledSubmissionEpoch(){val epoch=NativeSupervisor.cancellationEpoch();NativeSupervisor.cancel()
  ModelSubsystem(RuntimeEnvironment.getApplication(),ResourceGovernor()).use { models->
   try{models.beginTurn(epoch);fail("Queued turn renewed cancellation authority")}catch(e:IllegalStateException){assertEquals("Cognitive operation cancelled",e.message)}
   finally{models.endTurn()}
  }
 }
 @Test fun stopBeforeBindingRefusesStaleOperationBeforeFileOrServiceAccess(){val epoch=NativeSupervisor.cancellationEpoch();assertTrue(NativeSupervisor.cancel());var probed=false
  try{NativeSupervisor(RuntimeEnvironment.getApplication()).run(File("/missing-owner-model"),SafeRuntimeProfile(512,64,1,16,1,12000,512L*1024*1024,OperatingMode.INTERACTIVE),"","",safe={probed=true;true},epoch=epoch);fail("Cancelled operation started")}catch(e:IllegalStateException){assertEquals("Cognitive operation cancelled",e.message)}
  assertFalse(probed)
 }
 @Test fun realMemoryTrimInvalidatesOldCheckpointAndAllowsNewExplicitCheckpoint(){val epoch=NativeSupervisor.cancellationEpoch();(RuntimeEnvironment.getApplication() as YukiApplication).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
  try{NativeSupervisor.requireActive(epoch);fail("Pressure did not cancel operation")}catch(_:IllegalStateException){}
  NativeSupervisor.requireActive(NativeSupervisor.cancellationEpoch())
 }
}
