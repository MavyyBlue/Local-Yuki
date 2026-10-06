package com.mavyy.localyuki.embodiment

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.*
import android.graphics.Path
import android.os.*
import android.view.accessibility.*
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Structured screen producer and typed effector; no screenshot claim or password/auth text collection. */
class YukiAccessibility:AccessibilityService() {
    companion object { @Volatile internal var live:YukiAccessibility?=null
        @Volatile var foregroundPackage:String?=null;private set
        @Volatile var foregroundSince:Long=0;private set
        @Volatile var observedAt:Long=0;private set
    }
    private val main=Handler(Looper.getMainLooper());private var lastEvent=0L
    override fun onServiceConnected(){live=this}
    override fun onAccessibilityEvent(event:AccessibilityEvent) {
        val pkg=event.packageName?.toString()?:return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !pkg.startsWith("com.android.systemui")) {
            if(pkg!=foregroundPackage){foregroundPackage=pkg;foregroundSince=SystemClock.elapsedRealtime()};observedAt=SystemClock.elapsedRealtime()
        }
        if(pkg==packageName)return
        if(SystemClock.elapsedRealtime()-lastEvent<15000)return
        lastEvent=SystemClock.elapsedRealtime()
        BodyEvents.submit(applicationContext,"FOREGROUND",pkg,"Foreground app observed through Accessibility")
    }
    override fun onInterrupt() { live=null }
    override fun onDestroy(){live=null;foregroundPackage=null;super.onDestroy()}
    private fun <T> onMain(block:()->T):T {
        if(Looper.myLooper()==Looper.getMainLooper())return block()
        val f=CompletableFuture<T>();main.post { try { f.complete(block()) } catch(e:Exception){f.completeExceptionally(e)} };return f.get(5,TimeUnit.SECONDS)
    }
    private fun nodes(target:String):Pair<AccessibilityNodeInfo,List<AccessibilityNodeInfo>> {
        val root=rootInActiveWindow?:error("No active window");require(root.packageName?.toString()==target) { "Foreground target changed" }
        val out=mutableListOf<AccessibilityNodeInfo>();val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root)
        while(queue.isNotEmpty() && out.size<128) { val n=queue.removeFirst();out+=n
            if(!n.isPassword)for(i in 0 until minOf(n.childCount,32))n.getChild(i)?.let(queue::addLast) }
        return root to out
    }
    internal fun hasPasswordField(target:String):Boolean=onMain { nodes(target).second.any { it.isPassword } }
    fun screen(target:String):String=onMain {
        val (_,list)=nodes(target)
        list.filter { !it.isPassword && it.isVisibleToUser }.mapIndexed { i,n->"$i ${n.viewIdResourceName?:""}: ${(n.text?:n.contentDescription?:"").toString().take(96)}" }.joinToString("\n").take(480)
    }
    fun act(target:String?,payload:String):String=onMain {
        val j=JSONObject(payload);require(j.keys().asSequence().all { it in setOf("op","viewId","text","x","y") })
        when(val op=j.getString("op")) {
            "home","back","recents","notifications" -> {
                require(target==null);val action=when(op){"home"->GLOBAL_ACTION_HOME;"back"->GLOBAL_ACTION_BACK;"recents"->GLOBAL_ACTION_RECENTS;else->GLOBAL_ACTION_NOTIFICATIONS}
                check(performGlobalAction(action));"Android accepted $op navigation"
            }
            "click","setText","scrollForward","scrollBackward" -> {
                require(target!=null);val (_,list)=nodes(target)
                val id=j.getString("viewId");require(id.length in 1..160)
                val n=list.singleOrNull { it.viewIdResourceName==id && it.isVisibleToUser && it.isEnabled && !it.isPassword }?:error("Unique safe node unavailable")
                val action=when(op){"click"->AccessibilityNodeInfo.ACTION_CLICK;"setText"->AccessibilityNodeInfo.ACTION_SET_TEXT;"scrollForward"->AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;else->AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}
                val args=if(op=="setText")Bundle().apply { val text=j.getString("text");require(text.toByteArray().size<=256);putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) } else null
                check(n.performAction(action,args));"Accessibility accepted $op on $id in $target; subsequent screen observation confirms effects"
            }
            else->error("Unsupported Accessibility operation")
        }
    }
}
