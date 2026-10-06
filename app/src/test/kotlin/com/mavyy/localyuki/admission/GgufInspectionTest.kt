package com.mavyy.localyuki.admission

import org.junit.Test
import org.junit.Assert.*
import java.io.*

/** Container parser fixtures are never execution/admission evidence. */
class GgufInspectionTest {
 private fun file(split:Int=1,heads:Int=8,kv:Int=2,dimensions:List<Long> = listOf(32,64)):File {
  val f=File.createTempFile("metadata-only-", ".gguf")
  DataOutputStream(FileOutputStream(f)).use { o ->
   fun u(x:Int)=o.writeInt(Integer.reverseBytes(x))
   fun l(x:Long)=o.writeLong(java.lang.Long.reverseBytes(x))
   fun s(x:String){val b=x.toByteArray();l(b.size.toLong());o.write(b)}
   o.writeInt(0x47475546);u(3);l(1);l(8)
   s("general.architecture");u(8);s("llama")
   for((key,value) in mapOf("general.file_type" to 15,"split.count" to split,"llama.context_length" to 4096,"llama.block_count" to 4,"llama.embedding_length" to 256,"llama.attention.head_count" to heads,"llama.attention.head_count_kv" to kv)) {s(key);u(4);u(value)}
   s("example.weight");u(dimensions.size);dimensions.forEach(::l);u(1);l(0);o.write(ByteArray(32))
  }
  return f
 }
 private fun rejects(f:File) {try {GgufInspector.inspect(f);fail("Unsafe GGUF accepted")}catch(_:IllegalArgumentException){}finally{f.delete()}}
 @Test fun groupedQueryKvAndParameterCountUseTensorGeometry(){val f=file();try {val m=GgufInspector.inspect(f);assertEquals(2048L,m.parameters);assertEquals(15,m.quantization);assertEquals(4L*4*512*64,m.kvBytes(512));assertTrue(m.estimatedMemory(f.length(),512)>256L*1024*1024)}finally{f.delete()}}
 @Test fun splitModelsAndInvalidHeadGeometryAreRejected(){rejects(file(split=2));rejects(file(kv=9));rejects(file(heads=0))}
 @Test fun tensorParameterOverflowIsRejectedBeforeAllocation(){val f=file(dimensions=List(4){1000000L});try {GgufInspector.inspect(f);fail("overflow accepted")}catch(_:ArithmeticException){}finally{f.delete()}}
 @Test fun hostileMetadataCountIsRejectedBeforeParsing(){val f=file();RandomAccessFile(f,"rw").use {it.seek(16);it.writeLong(java.lang.Long.reverseBytes(4097L))};rejects(f)}
 @Test fun impossibleWeightGeometryIsRejectedBeforeNativeAllocation(){rejects(file(dimensions=listOf(1000000L,1000000L)))}
}
