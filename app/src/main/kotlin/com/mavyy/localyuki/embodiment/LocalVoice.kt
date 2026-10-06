package com.mavyy.localyuki.embodiment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.*
import android.speech.tts.*
import java.util.concurrent.*

/** Android's actual offline engines only. Network-dependent voices/recognizers are rejected. */
object LocalVoice {
    private var activeRecognizer:SpeechRecognizer?=null
    private var activeSpeech:CompletableFuture<Boolean>?=null
    fun listen(activity:Activity,result:(String)->Unit,error:(String)->Unit) {
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) { error("Android has no available on-device recognition engine");return }
        if(activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){error("Microphone permission is unavailable");return}
        activeRecognizer?.cancel();activeRecognizer?.destroy()
        val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);activeRecognizer=recognizer
        recognizer.setRecognitionListener(object:RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?){}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(rmsdB:Float){}
            override fun onBufferReceived(buffer:ByteArray?){}
            override fun onEndOfSpeech(){}
            override fun onError(code:Int){recognizer.destroy();if(activeRecognizer===recognizer)activeRecognizer=null;error("On-device speech recognition failed ($code)")}
            override fun onResults(results:Bundle?) { val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull();recognizer.destroy();if(activeRecognizer===recognizer)activeRecognizer=null;if(text.isNullOrBlank())error("No speech recognized")else result(text.take(2048)) }
            override fun onPartialResults(partialResults:Bundle?){}
            override fun onEvent(eventType:Int,params:Bundle?){}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1))
    }
    @Volatile private var offline:TextToSpeech?=null
    @Volatile var ready=false;private set
    private var opening=false
    fun initialize(context:Context) {
        Handler(Looper.getMainLooper()).post {
            if(opening||offline!=null)return@post;opening=true
            val tts=TextToSpeech(context.applicationContext) { status->
                val engine=offline
                if(status==TextToSpeech.SUCCESS && engine!=null) {
                    val voice=engine.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language==java.util.Locale.getDefault().language }?.maxByOrNull { it.quality }
                    if(voice!=null){engine.voice=voice;ready=true}
                };opening=false
            };offline=tts
        }
    }
    fun speak(text:String):String {
        check(ready);require(text.toByteArray().size in 1..512)
        val done=CompletableFuture<Boolean>();activeSpeech=done;val id="voice-${java.util.UUID.randomUUID()}"
        Handler(Looper.getMainLooper()).post {
            val tts=offline;val voice=tts?.voice
            if(tts==null||voice==null||voice.isNetworkConnectionRequired){done.complete(false);return@post}
            tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(utteranceId:String?){};override fun onDone(utteranceId:String?){if(utteranceId==id)done.complete(true)};@Deprecated("Android callback") override fun onError(utteranceId:String?){if(utteranceId==id)done.complete(false)}})
            if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,Bundle(),id)!=TextToSpeech.SUCCESS)done.complete(false)
        }
        check(done.get(30,TimeUnit.SECONDS));return "Offline speech completed"
    }
    fun stop(){activeSpeech?.complete(false);Handler(Looper.getMainLooper()).post { offline?.stop();activeRecognizer?.cancel();activeRecognizer?.destroy();activeRecognizer=null }}
}
