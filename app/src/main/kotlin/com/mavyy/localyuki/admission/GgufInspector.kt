package com.mavyy.localyuki.admission

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class GgufMetadata(val architecture:String,val parameters:Long,val quantization:Int?,val trainedContext:Int,
    val layers:Int,val embedding:Int,val heads:Int,val kvHeads:Int,val tensorTypes:Set<Int>,val name:String) {
    fun kvBytes(context:Int):Long = Math.multiplyExact(4L*layers,Math.multiplyExact(context.toLong(),(embedding.toLong()*kvHeads+heads-1)/heads))
    fun estimatedMemory(bytes:Long,context:Int):Long = Math.addExact(Math.addExact(bytes+bytes/3,256L*1024*1024),kvBytes(context))
}
/** Bounded metadata/tensor inspection. No parser-controlled allocation or unchecked integer products. */
internal object GgufInspector {
    fun inspect(file:File):GgufMetadata=RandomAccessFile(file,"r").use { f ->
        fun uint():Long=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))
        fun ulong():Long=java.lang.Long.reverseBytes(f.readLong()).also { require(it>=0) }
        fun string(keep:Boolean=true):String {
            val n=ulong();require(n<=1024*1024 && n<=f.length()-f.filePointer)
            if(!keep){f.seek(f.filePointer+n);return ""}
            require(n<=8192);return ByteArray(n.toInt()).also { f.readFully(it) }.toString(Charsets.UTF_8)
        }
        fun primitive(type:Int,keep:Boolean):Any?=when(type) {
            0->f.readUnsignedByte();1->f.readByte().toInt();2->java.lang.Short.reverseBytes(f.readShort()).toInt() and 65535
            3->java.lang.Short.reverseBytes(f.readShort()).toInt();4->uint();5->Integer.reverseBytes(f.readInt()).toLong()
            6->Float.fromBits(Integer.reverseBytes(f.readInt()));7->f.readUnsignedByte().also { require(it<=1) }
            8->string(keep);10->ulong();11->java.lang.Long.reverseBytes(f.readLong());12->Double.fromBits(java.lang.Long.reverseBytes(f.readLong()))
            else->throw IllegalArgumentException("unsupported GGUF value type")
        }
        require(f.readInt()==0x47475546);val version=uint();require(version in 2..3)
        val tensors=ulong();val keys=ulong();require(tensors in 1..100000 && keys in 1..4096)
        val metadata=mutableMapOf<String,Any?>()
        repeat(keys.toInt()) {
            require(f.filePointer<64L*1024*1024)
            val key=string();require(key.length<=256 && key !in metadata)
            val type=uint().toInt();val keep=key in setOf("general.architecture","general.name","general.file_type","split.count") || key.endsWith(".context_length")||key.endsWith(".block_count")||key.endsWith(".embedding_length")||key.endsWith(".attention.head_count")||key.endsWith(".attention.head_count_kv")
            if(type==9){val element=uint().toInt();val n=ulong();require(n<=500000 && element!=9);repeat(n.toInt()) { primitive(element,false) };metadata[key]=null}
            else metadata[key]=primitive(type,keep)
        }
        require((metadata["split.count"] as? Number)?.toInt()?.let { it<=1 }!=false) { "split models require a bundle adapter" }
        var parameters=0L;val types=mutableSetOf<Int>()
        repeat(tensors.toInt()) {
            require(f.filePointer<64L*1024*1024);string(false);val dimensions=uint().toInt();require(dimensions in 1..4)
            var count=1L;repeat(dimensions) { val d=ulong();require(d in 1..1000000);count=Math.multiplyExact(count,d) };parameters=Math.addExact(parameters,count)
            types+=uint().toInt();val offset=ulong();require(offset<file.length())
        }
        // Every installed GGML weight encoding needs at least one bit per stored parameter.
        // Refuse impossible tensor geometry before an untrusted native loader can allocate from it.
        require(parameters<=Math.multiplyExact(file.length(),8L)) { "Declared tensors cannot fit in this model file" }
        val arch=metadata["general.architecture"] as? String ?: error("architecture missing");require(arch.matches(Regex("[a-z0-9_-]{1,64}")))
        fun number(suffix:String,fallback:Int?=null):Int { val n=(metadata["$arch.$suffix"] as? Number)?.toLong() ?: fallback?.toLong() ?: error("memory geometry missing: $suffix");require(n in 1..1000000);return n.toInt() }
        val emb=number("embedding_length");val layers=number("block_count");val heads=number("attention.head_count");val kv=number("attention.head_count_kv",heads)
        require(emb<=16384 && layers<=256 && heads<=256 && kv<=heads)
        GgufMetadata(arch,parameters,(metadata["general.file_type"] as? Number)?.toInt(),number("context_length",512).coerceAtMost(1000000),layers,emb,heads,kv,types,(metadata["general.name"] as? String ?: arch).take(128))
    }
}
