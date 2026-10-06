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
        header.addView(button("Stop") { com.mavyy.localyuki.inference.NativeSupervisor.cancel();com.mavyy.localyuki.embodiment.LocalVoice.stop();show("Cognition cancellation requested.") })
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
        column.addView(button("Send") {
            val text=input.text.toString().trim();task {
                when(val result=brain.saveInput(text)) {
                    is FoundationResult.Success -> {
                        show("Yuki is thinking within the current resource budget…")
                        when(val reply=brain.converse(result.value)) {
                            is FoundationResult.Success-> { lastExpression=reply.value.text;show(reply.value.text) }
                            else->showConversationFailure()
                        }
                    }
                    else -> showFailure(result)
                }
            }
        })
        column.addView(button("Memory & note actions") { openMessageActions() })
        message=label("Your conversation and Yuki’s continuity stay on this device. Connect admitted organs in Models.",14f)
        message.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        column.addView(message)
        memories=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };column.addView(memories)
        task { brain=BrainRuntime(applicationContext);if(!brain.open()) show("Continuity is unavailable. Existing data has been preserved.")
            BackgroundMaintenance.restore(applicationContext)
            val history=brain.conversationHistory();if(history.isNotEmpty())show(history.joinToString("\n\n"))
            com.mavyy.localyuki.embodiment.LocalVoice.initialize(applicationContext)
            val policy=brain.life.policy();com.mavyy.localyuki.scheduler.PresenceScheduling.configure(applicationContext,policy is FoundationResult.Success&&policy.value.enabled) }
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
        "Models" to { openModels() },
        "Autonomy & capabilities" to { openAutonomy() },
        "Voice" to { voiceMenu() },
        "Read text from an image locally" to { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),33) },
        "Inner life & reflections" to { openLife() },
        "Device action" to { deviceAction() },
        "System status" to { details("System status",diagnostics) },
        "Developer checks" to { choices("Developer checks",listOf("Simulate salience signal" to { simulateSignal() })) }
    ))
    private var diagnostics="Opening continuity…"
    private var notesEnabled=false
    private fun details(title: String,text: String) {
        val content=ScrollView(this).apply { addView(label(text).apply { setPadding(dp(24),dp(12),dp(24),dp(12));setTextIsSelectable(true) }) }
        AlertDialog.Builder(this).setTitle(title).setView(content).setPositiveButton("Close",null).setNeutralButton("Copy") { _,_->copyDetails(title,text) }.show()
    }
    private fun showConversationFailure() {
        lastExpression=""
        show("Your message is saved. No reply completed.\n${brain.lastConversationFailure}\nOpen System status to copy these details.")
    }
    private fun copyDetails(title:String,text:String) {
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(title,text))
        show("Copied $title.")
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
        val text="Continuity: ${if(brain.ready) "ready" else "unavailable"}\nLanguage: ${if(brain.models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.LANGUAGE_EXPRESSION)!=null) "admitted local organ" else "not enabled"}\n"+
            "Living memory: ${if(brain.models.activeHash(com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)!=null) "semantic + lexical" else "lexical"}\nAffect: ${if(affect is FoundationResult.Success) "persistent" else "unavailable"}\n"+
            "Recovery: ${if(mode is FoundationResult.Success) mode.value.mode.name.lowercase() else "unavailable"}\n"+
            "Body: ${if(body is FoundationResult.Success) body.value.engagement.name.lowercase() else "unavailable"}"
        runOnUiThread { if(!isDestroyed) {
            notesEnabled=notes is FoundationResult.Success && notes.value.policy.enabled
            diagnostics=text+"\nCurrent capabilities: ${(brain.capabilities.capabilities() as? FoundationResult.Success)?.value?.filter { it.available }?.joinToString { it.id.name } ?: "unavailable"}\nLocal notes: ${if(notesEnabled) "enabled" else "disabled"}\nCharging-time maintenance: ${if(BackgroundMaintenance.enabled(applicationContext)) "on" else "off"}"
            if(brain.lastConversationFailure.isNotBlank())diagnostics+="\nLast conversation failure: ${brain.lastConversationFailure}"
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
    private var importRole=com.mavyy.localyuki.foundation.admission.ModelRole.LANGUAGE_EXPRESSION
    private var lastExpression=""
    private fun openModels() { task {
        val result=brain.models.models();if(result !is FoundationResult.Success){showFailure(result);return@task}
        runOnUiThread { choices("Local cognitive organs",listOf("Import owner model" to { chooseImport() },"Index one changed memory with embeddings" to { task { brain.refreshBody(true);showFailure(brain.semantic.maintain()) } },"Unload / cancel native cognition" to { brain.models.unload();show("Native cognition cancellation requested; continuity retained.") })+
            result.value.filter { !it.manifest.optBoolean("removed") }.map { model->"${model.manifest.optString("name",model.id)} · ${model.manifest.optString("status")} · ${model.activeRoles.joinToString()}" to { inspectModel(model) } }) }
    } }
    private fun roles(action:(com.mavyy.localyuki.foundation.admission.ModelRole)->Unit) {
        val supported=listOf(com.mavyy.localyuki.foundation.admission.ModelRole.SYSTEM_ONE,com.mavyy.localyuki.foundation.admission.ModelRole.SYSTEM_TWO,com.mavyy.localyuki.foundation.admission.ModelRole.LANGUAGE_EXPRESSION,com.mavyy.localyuki.foundation.admission.ModelRole.EMBEDDING)
        choices("Cognitive role",supported.map { it.name to { action(it) } })
    }
    private fun chooseImport()=roles { role->
        importRole=role;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),31)
    }
    private fun inspectModel(model:com.mavyy.localyuki.admission.OwnerModel) {
        val display=ScrollView(this).apply { addView(label(model.manifest.toString(2)).apply { setPadding(dp(20),dp(12),dp(20),dp(12));setTextIsSelectable(true) }) }
        AlertDialog.Builder(this).setTitle("Model manifest & measurements").setView(display)
            .setPositiveButton("Manage") { _,_->choices("${model.manifest.optString("name",model.id)}",listOf(
                "Benchmark, admit & use / replace role" to { roles { role->show("Measuring real runtime and unload. Unsafe payloads are rejected before load.");task { val result=brain.models.admit(model.id,role,java.time.Instant.now());if(result is FoundationResult.Success){show("Admitted for $role. Device experience acceptance remains yours.");runOnUiThread { inspectModel(result.value) }}else show("Admission rejected: ${brain.models.lastFailure}") } } },
                "Disable a role" to { choices("Disable",model.activeRoles.map { role->role.name to { task { showFailure(brain.models.disable(role)) } } }) },
                "Unload" to { brain.models.unload();show("Native process cancellation requested.") },
                "Remove private weights (disable roles first)" to { task { showFailure(brain.models.remove(model.id)) } }
            )) }.setNeutralButton("Copy") { _,_->copyDetails("Model manifest",model.manifest.toString(2)) }.setNegativeButton("Close",null).show()
    }
    private fun openAutonomy() { task {
        val policy=brain.life.policy();val capabilities=brain.capabilities.capabilities()
        if(policy !is FoundationResult.Success||capabilities !is FoundationResult.Success){showFailure(policy);return@task}
        val p=policy.value
        runOnUiThread { choices("Autonomy & actual capabilities",listOf(
            "Autonomy: ${if(p.enabled) "on" else "off"} (toggle)" to { task { val next=p.copy(enabled=!p.enabled);showFailure(brain.life.configure(next));com.mavyy.localyuki.scheduler.PresenceScheduling.configure(applicationContext,next.enabled) } },
            "Configure check-ins, quiet hours & cooldown" to { policyEditor(p) },
            "Emergency off: disable capabilities & cognition" to { brain.models.unload();task {com.mavyy.localyuki.embodiment.LocalVoice.stop();CapabilityId.entries.forEach { brain.configureCapability(it,false) };brain.life.configure(p.copy(enabled=false));com.mavyy.localyuki.scheduler.PresenceScheduling.configure(applicationContext,false);show("Autonomy and capabilities disabled.") } },
            "Grant a document folder" to { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),32) }
        )+capabilities.value.map { c->"${c.id}: owner ${if(c.enabled) "on" else "off"}, Android ${if(c.platformGranted) "granted" else "unavailable"}" to { capabilitySetup(c) } }) }
    } }
    private fun capabilitySetup(c:CapabilityView) {
        val description=when(c.id) {
            CapabilityId.SCREEN,CapabilityId.UI_INTERACTION,CapabilityId.DEVICE_NAVIGATION->"Accessibility lets Yuki inspect structured nodes and use navigation/node actions. Password fields are excluded. Android may require App info → Allow restricted settings for a sideloaded app. Owner enablement is separate from Android access."
            CapabilityId.NOTIFICATIONS,CapabilityId.MEDIA_CONTROL->"Notification Listener lets Yuki observe notification package/category signals and access Android media sessions. Private notification messages are not collected by the background producer."
            CapabilityId.LOCKDOWN_CONTROL->"Upgrade Lockdown, then enable Allow Local Yuki control in its Settings. Calls verify both apps’ existing signing identities."
            CapabilityId.IMAGE_TEXT->"Bundled local OCR reads text from owner-selected images. Screenshot OCR additionally requires Accessibility on Android 11+, checks the foreground target, and refuses password windows. It does not recognize scenes or objects."
            CapabilityId.COMPANION->"Draw over other apps enables temporary companion check-in messages."
            CapabilityId.CONVERSATION_NOTIFY->"Android notification permission allows bounded check-ins subject to quiet hours and cooldown."
            else->"This enables the named capability for trusted executive dispatch. Android grants and actual executors are checked again for every action."
        }
        AlertDialog.Builder(this).setTitle(c.id.name).setMessage(description)
            .setPositiveButton(if(c.enabled) "Disable" else "Enable") { _,_->task { showFailure(brain.configureCapability(c.id,!c.enabled)) } }
            .setNeutralButton("Android setup") { _,_->try {
                val intent=when(c.id) {
                    CapabilityId.SCREEN,CapabilityId.UI_INTERACTION,CapabilityId.DEVICE_NAVIGATION->Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    CapabilityId.NOTIFICATIONS,CapabilityId.MEDIA_CONTROL->Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    CapabilityId.COMPANION->Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName"))
                    CapabilityId.SPEECH_INPUT->{requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),51);null}
                    CapabilityId.CONVERSATION_NOTIFY->{if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),52);null}
                    CapabilityId.LOCKDOWN_CONTROL->packageManager.getLaunchIntentForPackage("com.mavyy.yukilockdown")
                    else->Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                };intent?.let(::startActivity)
            }catch(_:Exception){show("Android setup page unavailable.")} }
            .setNegativeButton("Exclusions") { _,_->val edit=EditText(this).apply { hint="Denied package names, comma separated" }
                AlertDialog.Builder(this).setTitle("Sensitive app exclusions").setView(edit).setPositiveButton("Save") { _,_->val denied=edit.text.toString().split(',').map(String::trim).filter(String::isNotBlank).toSet();task { showFailure(brain.configureCapability(c.id,c.enabled,denied)) } }.setNeutralButton("Close",null).show()
            }.show()
    }
    private fun policyEditor(p:com.mavyy.localyuki.presence.AutonomyPolicy) {
        val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val fields=listOf("Quiet start hour" to p.quietStart,"Quiet end hour" to p.quietEnd,"Cooldown minutes" to p.cooldownMinutes,"Daily limit" to p.dailyLimit,"Salience threshold" to p.threshold).map { (name,value)->EditText(this).apply { hint=name;setText(value.toString());inputType=InputType.TYPE_CLASS_NUMBER;column.addView(this) } }
        val notifications=CheckBox(this).apply { text="Notification check-ins";isChecked=p.notifications;column.addView(this) }
        val overlay=CheckBox(this).apply { text="Overlay check-ins";isChecked=p.overlay;column.addView(this) }
        AlertDialog.Builder(this).setTitle("Autonomous interaction policy").setView(column).setPositiveButton("Save") { _,_->try {
            val next=p.copy(notifications=notifications.isChecked,overlay=overlay.isChecked,quietStart=fields[0].text.toString().toInt(),quietEnd=fields[1].text.toString().toInt(),cooldownMinutes=fields[2].text.toString().toInt(),dailyLimit=fields[3].text.toString().toInt(),threshold=fields[4].text.toString().toInt())
            task { showFailure(brain.life.configure(next)) }
        }catch(_:Exception){show("Use hours 0–23, cooldown 15–1440 minutes, daily limit 1–12 and threshold 1–100.")} }.setNegativeButton("Cancel",null).show()
    }
    private fun openLife() { task {
        val r=brain.life.records(limit=100);if(r !is FoundationResult.Success){showFailure(r);return@task}
        runOnUiThread { choices("Ongoing life & structured reflections",listOf("Add an ongoing concern / intention / opinion" to { addLife() },"Deferred executive plans" to { task { val plans=brain.plans.list();if(plans is FoundationResult.Success)runOnUiThread { choices("Plans",plans.value.map { plan->"${plan.intent.capability} · ${plan.due} · ${plan.status}" to { AlertDialog.Builder(this).setMessage(plan.intent.payload).setPositiveButton("Cancel plan") { _,_->task { showFailure(brain.plans.cancel(plan.intent.id)) } }.setNegativeButton("Close",null).show() } }) } } })+
            r.value.map { item->"${item.kind} · ${item.topic} · ${item.created}" to { AlertDialog.Builder(this).setTitle(item.topic).setMessage(item.content+"\n\n${item.evidence.size} evidence references; prior revision ${item.previous?:"none"}.").setPositiveButton("Resolve") { _,_->task { showFailure(brain.life.resolve(item.id)) } }.setNegativeButton("Close",null).show() } }) }
    } }
    private fun addLife()=choices("Record type",com.mavyy.localyuki.presence.LifeKind.entries.filter { it!=com.mavyy.localyuki.presence.LifeKind.REFLECTION }.map { kind->kind.name to {
        val text=input.text.toString().trim();val topic=EditText(this).apply { hint="Topic" }
        AlertDialog.Builder(this).setTitle("Use written text as $kind").setView(topic).setPositiveButton("Save") { _,_->val name=topic.text.toString().trim();task { val source=brain.saveInput(text);if(source is FoundationResult.Success)showFailure(brain.life.append(kind,name,text,listOf(source.value.ref),java.time.Instant.now()))else showFailure(source) } }.setNegativeButton("Cancel",null).show()
    } })
    private fun voiceMenu()=choices("Local voice",listOf(
        "Wake phrase while this app is visible" to { if(com.mavyy.localyuki.embodiment.ForegroundWakePhrase.enabled){com.mavyy.localyuki.embodiment.ForegroundWakePhrase.stop();show("Wake phrase disabled.")}else {
            com.mavyy.localyuki.embodiment.ForegroundWakePhrase.start(this,{ text ->input.setText(text);task { when(val source=brain.saveInput(text)){is FoundationResult.Success->when(val reply=brain.converse(source.value)){is FoundationResult.Success->{lastExpression=reply.value.text;show(reply.value.text)};else->showConversationFailure()};else->showFailure(source)} } },::show)
            show("Foreground wake phrase enabled. Start with Yuki. This uses offline speech recognition and stops when you leave this app or resources become constrained.")
        } },
        "Listen on device" to { if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),51)}else {
            show("Listening through Android's on-device recognizer…")
            com.mavyy.localyuki.embodiment.LocalVoice.listen(this,{ text->input.setText(text);task { brain.observeEvent("VOICE",null,"On-device speech transcription: ${text.take(256)}");show("Speech transcribed locally. Press Send to converse.") } },::show)
        } },
        "Speak last reply offline" to { task { if(lastExpression.isNotBlank())showFailure(brain.executeAction(ActionIntent("voice-${UUID.randomUUID()}",CapabilityId.SPEECH_OUTPUT,lastExpression.take(480))))else show("No accepted reply to speak.") } }
    ))
    private fun deviceAction() {
        val caps=CapabilityId.entries.filter { it !in setOf(CapabilityId.LOCAL_NOTE,CapabilityId.SPEECH_INPUT) }
        choices("Trusted executive action",caps.map { capability->capability.name to {
            val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            val target=EditText(this).apply { hint="Target Android package (when required)";column.addView(this) }
            val payload=EditText(this).apply { hint="Typed JSON command";setText(if(capability==CapabilityId.LOCKDOWN_CONTROL)"{\"command\":\"inspect\"}" else "{\"op\":\"inspect\"}");column.addView(this) }
            AlertDialog.Builder(this).setTitle(capability.name).setView(column).setPositiveButton("Execute granted capability") { _,_->val pkg=target.text.toString().trim().ifEmpty { null };val text=payload.text.toString();task { when(val result=brain.executeAction(ActionIntent("owner-action-${UUID.randomUUID()}",capability,text,pkg))) { is FoundationResult.Success->show(result.value.summary);else->showFailure(result) } } }.setNegativeButton("Cancel",null).show()
        } })
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
        if(requestCode==31) {
            if(resultCode==RESULT_OK && data?.data!=null) { val uri=data.data!!;val role=importRole
                show("Importing, hashing and inspecting owner model…")
                task { val stream=contentResolver.openInputStream(uri)?:throw IllegalStateException("Unable to read file")
                    when(val imported=brain.models.importFile(stream,role,java.time.Instant.now())) {
                        is FoundationResult.Success->runOnUiThread { inspectModel(imported.value) };else->showFailure(imported)
                    }
                }
            };return
        }
        if(requestCode==33) {
            if(resultCode==RESULT_OK&&data?.data!=null) { val uri=data.data!!
                task { val payload=org.json.JSONObject().put("op","file").put("uri",uri.toString()).toString()
                    when(val result=brain.executeAction(ActionIntent("image-${UUID.randomUUID()}",CapabilityId.IMAGE_TEXT,payload))) { is FoundationResult.Success->show(result.value.summary);else->showFailure(result) }
                }
            };return
        }
        if(requestCode==32) {
            if(resultCode==RESULT_OK&&data?.data!=null) {
                val uri=data.data!!;contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                task { showFailure(brain.configureCapability(CapabilityId.DOCUMENTS,true)) }
            };return
        }
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
                    com.mavyy.localyuki.scheduler.PresenceScheduling.configure(applicationContext,false)
                    brain.close()
                    val result=contentResolver.openInputStream(uri)?.let { vault.restore(it,secret) } ?: FoundationResult.Failure(FailureCategory.INVALID_INPUT)
                    brain=BrainRuntime(applicationContext);brain.open()
                    if(result is FoundationResult.Success) show("Continuity restored. Background maintenance is disabled until you enable it on this device.") else showFailure(result)
                }
                } finally { owningGovernor.release(lease.value.workload.id) }
            } finally { secret.fill('\u0000') }
        }
    }
    override fun onResume(){super.onResume();com.mavyy.localyuki.resource.OwnerVisibility.active=true}
    override fun onPause(){com.mavyy.localyuki.resource.OwnerVisibility.active=false;com.mavyy.localyuki.inference.NativeSupervisor.cancel();com.mavyy.localyuki.embodiment.ForegroundWakePhrase.stop();super.onPause()}
    override fun onDestroy() {
        password?.fill('\u0000');password=null
        if(!worker.isShutdown) { worker.execute { ContinuityAccess.exclusive { if(::brain.isInitialized) brain.close() } };worker.shutdown() }
        super.onDestroy()
    }
}
