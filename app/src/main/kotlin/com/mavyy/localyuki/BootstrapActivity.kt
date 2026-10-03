package com.mavyy.localyuki

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.widget.*
import android.view.ViewGroup
import android.view.View
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.os.Build
import android.graphics.Rect
import com.mavyy.localyuki.brain.*
import com.mavyy.localyuki.scheduler.BackgroundMaintenance
import com.mavyy.localyuki.vault.ContinuityVault
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*
import com.mavyy.localyuki.foundation.memory.*
import com.mavyy.localyuki.foundation.recovery.RecoveryMode
import java.util.UUID
import java.util.concurrent.Executors

/** Owner surface. Persistence works today; missing neural engines are reported honestly. */
@Suppress("DEPRECATION")
class BootstrapActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var brain: BrainRuntime
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var message: TextView
    private lateinit var memories: LinearLayout
    private var password: CharArray?=null
    private val ink=Color.rgb(222,235,233)
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(text: String,size: Float=16f)=TextView(this).apply { this.text=text;textSize=size;setTextColor(ink);setPadding(0,dp(8),0,dp(8)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor=Color.rgb(15,31,38)
        window.navigationBarColor=Color.rgb(15,31,38)
        window.decorView.systemUiVisibility=window.decorView.systemUiVisibility and
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
        val root=FrameLayout(this).apply { setBackgroundColor(Color.rgb(15,31,38)) }
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(16),dp(20),dp(24))
        }
        // Constrain during measurement so even the first frame stays inside the safe viewport.
        val scroll=object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                val available=View.MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(640))
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.getMode(widthMeasureSpec)),heightMeasureSpec)
            }
        }.apply {
            isFillViewport=true
            addView(column,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT,Gravity.CENTER_HORIZONTAL))
        if(Build.VERSION.SDK_INT>=30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { _,insets ->
                val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                root.setPadding(safe.left,safe.top,safe.right,safe.bottom)
                if(::input.isInitialized && input.hasFocus()) input.post {
                    input.requestRectangleOnScreen(Rect(0,0,input.width,input.height),false)
                }
                WindowInsets.CONSUMED
            }
        } else {
            // The legacy decor handles bars and adjustResize handles the keyboard (API 26–29).
            root.fitsSystemWindows=true
        }
        setContentView(root)
        root.requestApplyInsets()
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label("Local Yuki",28f),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        header.addView(button("Menu") { openMenu() })
        column.addView(header)
        status=label("Opening continuity…",14f);column.addView(status)
        input=EditText(this).apply {
            id=android.R.id.edit
            hint="Write to Yuki"
            setTextColor(ink);setHintTextColor(Color.LTGRAY)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines=2;maxLines=6
        }
        column.addView(input,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(button("Save message") {
            val text=input.text.toString().trim();task {
                when(val result=brain.saveInput(text)) {
                    is FoundationResult.Success -> show("Message saved. Language expression is not connected yet.")
                    else -> showFailure(result)
                }
            }
        })
        column.addView(button("Memory & note actions") { openMessageActions() })
        message=label("Foundation mode · Your messages stay here. Models are not connected yet.",14f)
        message.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        column.addView(message)
        memories=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };column.addView(memories)
        task { brain=BrainRuntime(applicationContext);if(!brain.open()) show("Continuity is unavailable. Existing data has been preserved.")
            BackgroundMaintenance.restore(applicationContext) }
    }
    private fun button(title: String,operation: () -> Unit)=Button(this).apply {
        text=title;isAllCaps=false;minHeight=dp(48);setOnClickListener { operation() }
    }
    private fun choices(title: String,items: List<Pair<String,() -> Unit>>) {
        if(isDestroyed) return
        AlertDialog.Builder(this).setTitle(title).setItems(items.map { it.first }.toTypedArray()) { _,index -> items[index].second() }
            .setNegativeButton("Close",null).show()
    }
    private fun openMenu()=choices("Local Yuki",listOf(
        "Memory & note actions" to { openMessageActions() },
        "Local notes: ${if(notesEnabled) "enabled" else "disabled"} (toggle)" to { toggleNotes() },
        "Rest & maintenance" to { openRest() },
        "Continuity Vault" to { choices("Continuity Vault",listOf(
            "Export encrypted Vault" to { chooseVault(false) },"Restore encrypted Vault" to { chooseVault(true) })) },
        "Models" to { details("Language & voice",
            "The subsystems form thoughts and make decisions. The language engine puts prepared meaning into words; a later voice engine will speak those words.\n\nPlug-in admission is prepared, but language, Laya decisions and other runtime adapters are not connected yet.") },
        "System status" to { details("System status",diagnostics) },
        "Developer checks" to { choices("Developer checks",listOf("Simulate salience signal" to { simulateSignal() })) }
    ))
    private var diagnostics="Opening continuity…"
    private var notesEnabled=false
    private fun details(title: String,text: String) {
        val content=ScrollView(this).apply { addView(label(text).apply { setPadding(dp(24),dp(12),dp(24),dp(12)) }) }
        AlertDialog.Builder(this).setTitle(title).setView(content).setPositiveButton("Close",null).show()
    }
    private fun openMessageActions()=choices("Use the text you wrote",listOf(
        "Remember" to {
            val text=input.text.toString().trim();task { when(val result=brain.remember(text)) {
                is FoundationResult.Success -> show("Remembered. Earlier evidence and history are preserved.")
                else -> showFailure(result)
            } }
        },
        "Search memories" to {
            val text=input.text.toString().trim();task {
                if(text.isBlank() || text.toByteArray().size>512) { show("Enter a search of at most 512 UTF-8 bytes.");return@task }
                when(val result=brain.recall(text)) {
                    is FoundationResult.Success -> runOnUiThread { renderMemories(result.value) }
                    else -> showFailure(result)
                }
            }
        },
        "Create local note" to {
            val text=input.text.toString().trim();task {
                if(text.isBlank() || text.toByteArray().size>512) { show("Enter a note of at most 512 UTF-8 bytes.");return@task }
                when(val result=brain.executeNote(ActionIntent("note-${UUID.randomUUID()}",CapabilityId.LOCAL_NOTE,text))) {
                    is FoundationResult.Success -> show("Local note saved.")
                    else -> showFailure(result)
                }
            }
        },
        "Clear search results" to { memories.removeAllViews();show("Search results cleared.") }
    ))
    private fun toggleNotes() { task {
        val current=brain.capabilities.setting(CapabilityId.LOCAL_NOTE)
        if(current is FoundationResult.Success) {
            val enabled=!current.value.policy.enabled
            when(val result=brain.setNotesEnabled(enabled)) {
                is FoundationResult.Success -> show(if(enabled) "Local notes enabled. Granted notes can execute without repeated approval." else "Local notes disabled.")
                else -> showFailure(result)
            }
        } else showFailure(current)
    } }
    private fun openRest()=choices("Rest & maintenance",listOf(
        "Sleep / wake" to { task {
            val mode=brain.recovery.reader().read()
            if(mode is FoundationResult.Success) {
                val result=if(mode.value.mode in setOf(RecoveryMode.AWAKE,RecoveryMode.FATIGUED)) brain.sleep() else brain.wake()
                when(result) { is FoundationResult.Success -> show("Recovery state: ${result.value.mode.name.lowercase()}");else -> showFailure(result) }
            } else showFailure(mode)
        } },
        "Run maintenance (while asleep)" to { task {
            when(val result=brain.maintenance()) {
                is FoundationResult.Success -> show(if(result.value) "Maintenance complete." else "Maintenance paused; evidence remains intact.")
                else -> show("Put Yuki to sleep before running maintenance.")
            }
        } },
        "Charging-time maintenance: ${if(BackgroundMaintenance.enabled(applicationContext)) "on" else "off"} (toggle)" to { task {
            val enabled=!BackgroundMaintenance.enabled(applicationContext)
            if(BackgroundMaintenance.configure(applicationContext,enabled)) show(if(enabled) "Charging-time maintenance enabled." else "Background maintenance disabled.")
            else show("Android could not schedule maintenance.")
        } }
    ))
    private fun simulateSignal() { task {
        val signal=com.mavyy.localyuki.foundation.scheduler.BackgroundSignal("demo-${UUID.randomUUID()}",
            com.mavyy.localyuki.foundation.scheduler.SignalKind.SCHEDULED,java.time.Instant.now(),900,1)
        when(val result=brain.backgroundSignal(signal,true)) {
            is FoundationResult.Success -> show(if(result.value) "Simulation: escalation advised. No neural model was invoked." else "Simulation: cooldown, sleep or resource limits kept cognition quiet.")
            else -> showFailure(result)
        }
    } }
    private fun task(operation: () -> Unit) {
        if(worker.isShutdown) return
        worker.execute { ContinuityAccess.exclusive {
            try { operation();refresh() } catch (_: Exception) { show("The operation could not complete. Existing continuity was preserved.") }
        } }
    }
    private fun refresh() {
        if(!::brain.isInitialized) return
        val mode=brain.recovery.reader().read();val affect=brain.affect.read();val body=brain.refreshBody(true)
        val notes=brain.capabilities.setting(CapabilityId.LOCAL_NOTE)
        val text="Continuity: ${if(brain.ready) "ready" else "unavailable"}\nLanguage engine: not connected\n"+
            "Living memory: lexical recall\nAffect: ${if(affect is FoundationResult.Success) "persistent" else "unavailable"}\n"+
            "Recovery: ${if(mode is FoundationResult.Success) mode.value.mode.name.lowercase() else "unavailable"}\n"+
            "Body: ${if(body is FoundationResult.Success) body.value.engagement.name.lowercase() else "unavailable"}"
        runOnUiThread { if(!isDestroyed) {
            notesEnabled=notes is FoundationResult.Success && notes.value.policy.enabled
            diagnostics=text+"\nLocal notes: ${if(notesEnabled) "enabled" else "disabled"}\nCharging-time maintenance: ${if(BackgroundMaintenance.enabled(applicationContext)) "on" else "off"}"
            status.text=if(brain.ready) "Continuity ready · ${if(mode is FoundationResult.Success) mode.value.mode.name.lowercase() else "recovery unavailable"}" else "Continuity unavailable"
        } }
    }
    private fun show(text: String)=runOnUiThread { if(!isDestroyed) message.text=text }
    private fun showFailure(result: FoundationResult<*>)=show(when(result) {
        is FoundationResult.Unavailable -> "That capability or dependency is unavailable."
        is FoundationResult.Failure -> when(result.category) {
            FailureCategory.INVALID_INPUT -> "Enter valid text within the supported limit."
            FailureCategory.CONFLICT -> "The state changed or could not be validated. Refresh and try again."
            FailureCategory.REJECTED -> "That operation is unavailable in the current sleep or resource state."
            else -> "The operation could not complete."
        }
        else -> "Done."
    })
    private fun renderMemories(result: RecallCandidateSet) {
        if(isDestroyed) return
        memories.removeAllViews()
        message.text=if(result.candidates.isEmpty()) "No surfaced memory matched. Original evidence is still retained." else "${result.candidates.size} memories surfaced · ${result.coverage.name.lowercase()} coverage"
        result.candidates.forEach { candidate ->
            memories.addView(Button(this).apply { text=candidate.memory.current.content.take(120);setOnClickListener {
                task { when(val consumed=brain.consume(candidate)) {
                    is FoundationResult.Success -> runOnUiThread { inspectMemory(candidate.memory) }
                    else -> showFailure(consumed)
                } }
            } })
        }
    }
    private fun inspectMemory(memory: DurableMemory) {
        if(isDestroyed) return
        AlertDialog.Builder(this).setTitle("Memory · revision ${memory.current.number}")
            .setMessage(memory.current.content+"\n\nGrounded in ${memory.current.evidence.size} evidence references.")
            .setPositiveButton("Update") { _,_ ->
                val editor=EditText(this).apply { setText(memory.current.content) }
                AlertDialog.Builder(this).setTitle("Update memory").setView(editor).setPositiveButton("Save") { _,_ ->
                    val text=editor.text.toString().trim();task { when(val updated=brain.updateMemory(memory,text)) {
                        is FoundationResult.Success -> show("Updated by appending a revision. Earlier history remains intact.")
                        else -> showFailure(updated)
                    } }
                }.setNegativeButton("Cancel",null).show()
            }.setNeutralButton("Restore previous") { _,_ ->task { when(val restored=brain.restorePrevious(memory)) {
                is FoundationResult.Success -> show("Previous content restored as a new revision. History remains intact.")
                else -> showFailure(restored)
            } } }.setNegativeButton("Close",null).show()
    }
    private fun chooseVault(restore: Boolean) {
        val editor=EditText(this).apply { hint="Password (12–256 characters)";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle(if(restore) "Restore encrypted continuity" else "Encrypt continuity")
            .setMessage(if(restore) "Your current database will be kept as a recovery copy. Platform grants and model weights are not imported." else "Keep the password safe. The Vault contains your continuity, not model weights or signing keys.")
            .setView(editor).setPositiveButton("Choose file") { _,_ ->
                val secret=CharArray(editor.text.length) { editor.text[it] };editor.text.clear()
                if(secret.size !in 12..256) { secret.fill('\u0000');show("Use a password of 12–256 characters.");return@setPositiveButton }
                password?.fill('\u0000');password=secret
                val intent=Intent(if(restore) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE);type="application/octet-stream"
                    if(!restore) putExtra(Intent.EXTRA_TITLE,"local-yuki-vault.ykv")
                }
                startActivityForResult(intent,if(restore) 22 else 21)
            }.setNegativeButton("Cancel",null).show()
    }
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode !in 21..22) return
        val secret=password;password=null
        if(resultCode!=RESULT_OK || data?.data==null || secret==null) { secret?.fill('\u0000');return }
        val uri=data.data!!
        task {
            try {
                val body=brain.refreshBody(true)
                if(body !is FoundationResult.Success || body.value.engagement==com.mavyy.localyuki.foundation.resource.Engagement.RECOVERY) {
                    show("Wait for resource recovery before using the Vault.");return@task
                }
                val owningGovernor=brain.governor
                val lease=owningGovernor.reserve(com.mavyy.localyuki.foundation.resource.Workload(
                    "vault-${UUID.randomUUID()}",256*com.mavyy.localyuki.foundation.resource.ResourceGovernor.MIB,10_000),java.time.Instant.now())
                if(lease !is FoundationResult.Success) { showFailure(lease);return@task }
                try {
                val vault=ContinuityVault(applicationContext)
                if(requestCode==21) {
                    val out=contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
                    showFailure(vault.export(out,secret))
                } else {
                    BackgroundMaintenance.configure(applicationContext,false)
                    brain.close()
                    val result=contentResolver.openInputStream(uri)?.let { vault.restore(it,secret) } ?: FoundationResult.Failure(FailureCategory.INVALID_INPUT)
                    brain=BrainRuntime(applicationContext);brain.open()
                    if(result is FoundationResult.Success) show("Continuity restored. Background maintenance is disabled until you enable it on this device.") else showFailure(result)
                }
                } finally { owningGovernor.release(lease.value.workload.id) }
            } finally { secret.fill('\u0000') }
        }
    }
    override fun onDestroy() {
        password?.fill('\u0000');password=null
        if(!worker.isShutdown) { worker.execute { ContinuityAccess.exclusive { if(::brain.isInitialized) brain.close() } };worker.shutdown() }
        super.onDestroy()
    }
}
