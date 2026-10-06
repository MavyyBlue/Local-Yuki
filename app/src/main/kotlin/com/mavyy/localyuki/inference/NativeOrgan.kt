package com.mavyy.localyuki.inference
/** Private process edge. Cognition receives no pointers or file access. */
internal class NativeOrgan {
 companion object { init { System.loadLibrary("yuki-organ") } }
 external fun load(fd:Int,context:Int,threads:Int,batch:Int,embeddings:Boolean,deadline:Long):Long
 external fun generate(handle:Long,system:ByteArray,user:ByteArray,grammar:ByteArray,maxTokens:Int,deadline:Long):ByteArray
 external fun embed(handle:Long,text:ByteArray,deadline:Long):FloatArray
 external fun metrics(handle:Long):LongArray
 external fun unload(handle:Long)
}
