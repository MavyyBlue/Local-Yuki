package com.mavyy.localyuki.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.mavyy.localyuki.BootstrapActivity
import com.mavyy.localyuki.brain.BrainRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class QueuedConversationTest {
    private fun buttons(view:View):List<Button> = when(view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun checkCancellation(background:Boolean) {
        val controller=Robolectric.buildActivity(BootstrapActivity::class.java).setup()
        val activity=controller.get()
        val workerField=BootstrapActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }
        val worker=workerField.get(activity) as ExecutorService
        val release=CountDownLatch(1)
        try {
            // Let continuity open, then hold the real UI worker as a slow cognitive turn would.
            worker.submit {}.get(15,TimeUnit.SECONDS)
            val brainField=BootstrapActivity::class.java.getDeclaredField("brain").apply { isAccessible=true }
            val brain=brainField.get(activity) as BrainRuntime
            val prior=brain.conversationHistory()
            val entered=CountDownLatch(1)
            worker.execute { entered.countDown();check(release.await(15,TimeUnit.SECONDS)) }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val controls=buttons(activity.findViewById(android.R.id.content))
            val send=controls.single { it.text=="Send" }
            val input=activity.findViewById<EditText>(android.R.id.edit)
            input.setText("Queued greeting before cancellation")
            send.performClick();send.performClick()
            // A queued non-cognitive owner operation must still run.
            val ordinaryRan=AtomicBoolean(false)
            val task=BootstrapActivity::class.java.getDeclaredMethod("task",java.lang.Long::class.java,kotlin.jvm.functions.Function0::class.java).apply { isAccessible=true }
            task.invoke(activity,null,{ ordinaryRan.set(true);Unit })
            if(background) { controller.pause();controller.resume() }
            else controls.single { it.text=="Stop" }.performClick()
            release.countDown()
            worker.submit {}.get(15,TimeUnit.SECONDS)
            assertEquals("Cancelled queued messages must not be saved or start a new turn",prior,brain.conversationHistory())
            assertTrue(ordinaryRan.get())
            input.setText("Fresh greeting after cancellation")
            send.performClick()
            worker.submit {}.get(15,TimeUnit.SECONDS)
            assertTrue("A fresh explicit Send remains usable",brain.conversationHistory().any { it.contains("Fresh greeting after cancellation") })
            assertFalse(brain.conversationHistory().any { it.contains("Queued greeting before cancellation") })
        } finally {
            release.countDown()
            controller.pause().stop().destroy()
            assertTrue(worker.awaitTermination(15,TimeUnit.SECONDS))
        }
    }
    @Test fun stopDropsQueuedSendsAndPreservesOrdinaryTasksAndFreshSend()=checkCancellation(false)
    @Test fun backgroundCancellationCannotResumeOldQueuedSends()=checkCancellation(true)
}
