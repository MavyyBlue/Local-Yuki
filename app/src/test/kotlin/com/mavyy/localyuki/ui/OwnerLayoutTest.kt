package com.mavyy.localyuki.ui

import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import com.mavyy.localyuki.BootstrapActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OwnerLayoutTest {
    private fun layout(root: View,width: Int,height: Int) {
        repeat(2) {
            root.forceLayout()
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            root.layout(0,0,width,height)
        }
    }
    private fun insets(bottom: Int)=WindowInsets.Builder()
        .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,24,0,32))
        .setInsets(WindowInsets.Type.displayCutout(),Insets.of(8,0,0,0))
        .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,bottom)).build()
    @Test fun viewportRespectsBarsCutoutAndKeyboardAndRemainsScrollable() {
        val controller=Robolectric.buildActivity(BootstrapActivity::class.java).setup()
        val activity=controller.get()
        try {
            val content=activity.findViewById<ViewGroup>(android.R.id.content)
            val root=content.getChildAt(0) as FrameLayout
            root.dispatchApplyWindowInsets(insets(0))
            layout(root,320,480)
            val scroll=root.getChildAt(0) as ScrollView
            assertEquals(8,root.paddingLeft);assertEquals(24,root.paddingTop);assertEquals(32,root.paddingBottom)
            assertTrue("scroll left=${scroll.left}, width=${scroll.width}, parameter=${scroll.layoutParams.width}, root=${root.width}, inset=${root.paddingLeft}",scroll.left>=root.paddingLeft)
            assertTrue(scroll.bottom<=root.height-root.paddingBottom)
            val input=activity.findViewById<EditText>(android.R.id.edit)
            input.requestFocus()
            root.dispatchApplyWindowInsets(insets(240))
            layout(root,320,480)
            assertEquals(240,root.paddingBottom) // max(IME, navigation), not their sum
            assertTrue(scroll.bottom<=240)
            assertTrue(scroll.getChildAt(0).height>=scroll.height)
            root.dispatchApplyWindowInsets(insets(0))
            layout(root,320,480)
            assertEquals(32,root.paddingBottom)
            assertTrue(scroll.bottom<=448)
            layout(root,1200,700)
            assertTrue(scroll.width<= (640*activity.resources.displayMetrics.density).toInt())
            assertTrue(scroll.right<=root.width-root.paddingRight)
        } finally {
            controller.pause().stop().destroy()
            val field=BootstrapActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }
            assertTrue((field.get(activity) as ExecutorService).awaitTermination(15,TimeUnit.SECONDS))
        }
    }
}
