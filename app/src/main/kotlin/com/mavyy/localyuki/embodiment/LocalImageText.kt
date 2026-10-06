package com.mavyy.localyuki.embodiment

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.*
import android.accessibilityservice.AccessibilityService
import android.view.Display
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.*

internal interface ImageTextAdapter { val id:String;val version:Int;fun recognize(bitmap:Bitmap):String }
/** Bundled Latin OCR executes locally. This reports text only; it does not claim object/scene understanding. */
internal class BundledLatinOcr:ImageTextAdapter {
 override val id="mlkit-bundled-latin";override val version=1
 override fun recognize(bitmap:Bitmap):String {
  val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  return try { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS).text.take(400) }finally{recognizer.close()}
 }
}
internal object LocalImageText {
 private val adapter:ImageTextAdapter=BundledLatinOcr()
 private fun recognize(bitmap:Bitmap):String=bitmap.let { original ->
  val scale=minOf(1f,1536f/maxOf(original.width,original.height));val resized=if(scale<1f)Bitmap.createScaledBitmap(original,(original.width*scale).toInt().coerceAtLeast(1),(original.height*scale).toInt().coerceAtLeast(1),true) else original
  try { "Actual image text supplied by ${adapter.id} v${adapter.version}: ${adapter.recognize(resized).ifEmpty { "No text recognized" }}" }
  finally{if(resized!==original)resized.recycle();original.recycle()}
 }
 fun file(context:Context,uri:Uri):String {
  check(Looper.myLooper()!=Looper.getMainLooper());require(uri.scheme=="content")
  context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { require(it.length in 1..32L*1024*1024) { "Image must be a bounded local file, at most 32 MiB" } } ?: error("Image descriptor unavailable")
  val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
  context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }?:error("Granted image unavailable")
  require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384)
  var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1536)sample*=2
  val options=BitmapFactory.Options().apply { inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888 }
  val bitmap=context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }?:error("Image decode failed")
  return recognize(bitmap)
 }
 @android.annotation.TargetApi(30)
 fun screenshot(service:YukiAccessibility,target:String):String {
  require(Build.VERSION.SDK_INT>=30);check(Looper.myLooper()!=Looper.getMainLooper());require(YukiAccessibility.foregroundPackage==target)
  // Password windows use structured access only; never capture them for OCR.
  require(!service.hasPasswordField(target)) { "Screenshot refused for a password window" }
  val f=CompletableFuture<Bitmap>()
  service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,object:AccessibilityService.TakeScreenshotCallback {
   override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
    result.hardwareBuffer.use { buffer ->
     try { val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?:error("Screenshot buffer unavailable")
      try { val copy=hardware.copy(Bitmap.Config.ARGB_8888,false)?:error("Screenshot copy failed");if(!f.complete(copy))copy.recycle() }finally{hardware.recycle()} }catch(e:Exception){f.completeExceptionally(e)}
    }
   }
   override fun onFailure(errorCode:Int){f.completeExceptionally(IllegalStateException("Android screenshot failure $errorCode"))}
  })
  val bitmap=try { f.get(5,TimeUnit.SECONDS) }catch(e:Exception){f.cancel(true);throw e}
  if(YukiAccessibility.foregroundPackage!=target){bitmap.recycle();error("Foreground changed during capture")}
  return recognize(bitmap)
 }
}
