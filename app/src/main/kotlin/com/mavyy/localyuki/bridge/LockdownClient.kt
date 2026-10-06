package com.mavyy.localyuki.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** The peer package and its existing signer are verified before every authenticated IPC call. */
class LockdownClient(context:Context) {
    private val app=context.applicationContext
    companion object {
        const val PACKAGE="com.mavyy.yukilockdown"
        const val AUTHORITY="com.mavyy.yukilockdown.localyuki.v1"
        const val CERT="62d2cca61747587fbff9a90523b8810cd9cc3edba6e6e46845f7ee1178d8e618"
    }
    private fun peer() {
        val provider=app.packageManager.resolveContentProvider(AUTHORITY,0)?:error("Lockdown control provider unavailable; upgrade Lockdown")
        require(provider.packageName==PACKAGE)
        @Suppress("DEPRECATION") val info=app.packageManager.getPackageInfo(PACKAGE,if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION") val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo!!.apkContentsSigners else info.signatures!!
        require(signatures.size==1 && MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray()).joinToString(""){"%02x".format(it)}==CERT) { "Lockdown signing identity mismatch" }
    }
    private fun call(command:String,args:JSONObject=JSONObject(),expected:String?=null,id:String="bridge-${UUID.randomUUID()}"):JSONObject {
        peer();val request=JSONObject().put("version",1).put("id",id).put("command",command).put("arguments",args)
        expected?.let { request.put("expectedState",it) }
        val bundle=app.contentResolver.call(Uri.parse("content://$AUTHORITY"),"command",null,Bundle().apply { putString("json",request.toString()) })?:error("No Lockdown response")
        val raw=bundle.getString("json")?:error("No result");require(raw.toByteArray().size<=128*1024)
        val response=JSONObject(raw);check(response.getBoolean("success")) { response.optString("error","Command rejected") }
        require(response.getInt("version")==1 && response.getString("id")==id && response.getString("command")==command)
        require(response.getString("stateDigest").matches(Regex("[0-9a-f]{64}")) && response.getJSONObject("state").getInt("version")==1)
        val actual=MessageDigest.getInstance("SHA-256").digest(response.getJSONObject("state").toString().toByteArray()).joinToString(""){"%02x".format(it)}
        require(actual==response.getString("stateDigest")) { "Command/result state integrity mismatch" }
        return response
    }
    fun inspect():JSONObject=call("inspect")
    fun available():Boolean=try { inspect();true }catch(_:Exception){false}
    fun execute(payload:String):String {
        val j=JSONObject(payload);require(j.keys().asSequence().all { it in setOf("command","arguments","id") })
        val command=j.getString("command");val args=j.optJSONObject("arguments")?:JSONObject()
        val before=inspect()
        if(command=="inspect")return before.getJSONObject("state").toString().take(480)
        val result=call(command,args,before.getString("stateDigest"),j.optString("id").takeIf { it.isNotBlank() }?:"bridge-${UUID.randomUUID()}")
        return "Lockdown $command succeeded; state ${result.getString("stateDigest")}"+
            if(result.has("breakToken"))"; token ${result.getString("breakToken")}; wait ${result.getInt("waitSeconds")} seconds" else ""
    }
}
