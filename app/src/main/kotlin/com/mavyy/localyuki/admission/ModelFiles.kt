package com.mavyy.localyuki.admission

import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.*
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Owner-supplied file inspection. No downloaded weights or model family assumptions. */
object ModelFiles {
    const val MAX_BYTES=4L*1024*1024*1024
    fun inspect(file: File,id: String,role: ModelRole): FoundationResult<ModelDescriptor> = try {
        require(file.isFile && file.length() in 8..MAX_BYTES)
        val digest=MessageDigest.getInstance("SHA-256")
        var bytes=0L
        val header=ByteArray(24)
        file.inputStream().use { input ->
            val buffer=ByteArray(64*1024)
            while(true) {
                val count=input.read(buffer);if(count<0) break
                if(bytes<header.size) System.arraycopy(buffer,0,header,bytes.toInt(),minOf(count,header.size-bytes.toInt()))
                bytes+=count;require(bytes<=MAX_BYTES);digest.update(buffer,0,count)
            }
        }
        require(bytes==file.length())
        val sha=digest.digest().joinToString("") { "%02x".format(it) }
        val gguf=header.copyOfRange(0,4).contentEquals(byteArrayOf(0x47,0x47,0x55,0x46))
        val version=if(gguf) ByteBuffer.wrap(header,4,4).order(ByteOrder.LITTLE_ENDIAN).int else null
        val format=if(gguf && version in 2..3 && bytes>=24 &&
            ByteBuffer.wrap(header,8,8).order(ByteOrder.LITTLE_ENDIAN).long in 1..100_000 &&
            ByteBuffer.wrap(header,16,8).order(ByteOrder.LITTLE_ENDIAN).long in 0..4096) ModelFormat.GGUF else {
            // Safetensors begins with a bounded little-endian JSON header length.
            val length=ByteBuffer.wrap(header,0,8).order(ByteOrder.LITTLE_ENDIAN).long
            if(!gguf && length in 2..1024*1024 && length<bytes-8) {
                val metadata=ByteArray(length.toInt())
                RandomAccessFile(file,"r").use { it.seek(8);it.readFully(metadata) }
                val text=metadata.toString(Charsets.UTF_8).trim()
                if(text.startsWith("{") && text.endsWith("}")) ModelFormat.SAFETENSORS else ModelFormat.UNKNOWN
            } else ModelFormat.UNKNOWN
        }
        FoundationResult.Success(ModelDescriptor(id,role,sha,bytes,format,version))
    } catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
      catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    fun import(input: InputStream,destination: File,maxBytes: Long=MAX_BYTES): FoundationResult<Long> {
        require(maxBytes in 8..MAX_BYTES)
        val partial=File(destination.parentFile,"${destination.name}.partial")
        if(destination.exists() || partial.exists()) return FoundationResult.Failure(FailureCategory.CONFLICT)
        return try {
            var total=0L
            input.use { source -> partial.outputStream().use { output ->
                val buffer=ByteArray(64*1024)
                while(true) { val count=source.read(buffer);if(count<0) break;total+=count
                    if(total>maxBytes) throw IllegalArgumentException("model exceeds import budget")
                    output.write(buffer,0,count) }
                output.fd.sync()
            } }
            require(total>=8)
            check(partial.renameTo(destination))
            FoundationResult.Success(total)
        } catch (_: IllegalArgumentException) { partial.delete();FoundationResult.Failure(FailureCategory.REJECTED) }
          catch (_: Exception) { partial.delete();FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    }
}
