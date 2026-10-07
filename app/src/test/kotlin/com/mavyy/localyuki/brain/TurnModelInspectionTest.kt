package com.mavyy.localyuki.brain

import android.system.*
import com.mavyy.localyuki.admission.*
import com.mavyy.localyuki.foundation.admission.*
import com.mavyy.localyuki.foundation.contracts.FoundationResult
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.nio.*
import java.nio.file.*
import java.nio.file.attribute.FileTime

/** Real small-file hashing and Linux inode/timestamp changes; fixture metadata is not execution proof. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class TurnModelInspectionTest {
    private val metadata=GgufMetadata("llama",8,15,512,1,32,1,1,setOf(1),"fixture")
    private fun file()=File.createTempFile("turn-model-", ".gguf").apply {
        writeBytes(ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(1).putLong(0).array())
    }
    private fun descriptor(file:File,id:String="fixture",role:ModelRole=ModelRole.SYSTEM_ONE)=
        (ModelFiles.inspect(file,id,role) as FoundationResult.Success).value
    private fun stamp(file:File):ModelFileStamp {
        val a=Files.readAttributes(file.toPath(),"unix:dev,ino,size,mode,uid,gid,nlink,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS)
        fun n(key:String)=(a.getValue(key) as Number).toLong()
        val m=(a.getValue("lastModifiedTime") as FileTime).toInstant();val c=(a.getValue("ctime") as FileTime).toInstant()
        return ModelFileStamp(n("dev"),n("ino"),n("size"),n("mode").toInt(),n("uid").toInt(),n("gid").toInt(),n("nlink"),m.epochSecond,m.nano.toLong(),c.epochSecond,c.nano.toLong())
    }
    private fun rejects(block:()->Unit) { try { block();fail("Changed/unverified file accepted") }catch(_:IllegalStateException){} }
    @Test fun sameFileCanServeSeveralRolesButOtherFilesAndNewTurnsRequireFullHash() {
        val f=file();val other=file()
        try {
            val memo=TurnModelInspection(::stamp);var scans=0;val expected=descriptor(f)
            memo.inspect(f,expected) { scans++;descriptor(f) to metadata }
            memo.inspect(f,expected.copy(role=ModelRole.LANGUAGE_EXPRESSION)) { fail("Repeated full file read");error("unexpected") }
            assertTrue(memo.reused);assertEquals(1,scans)
            memo.inspect(other,descriptor(other,"other")) { scans++;descriptor(other,"other") to metadata }
            assertFalse(memo.reused)
            TurnModelInspection(::stamp).inspect(f,expected) { scans++;descriptor(f) to metadata }
            assertEquals(3,scans)
        }finally{f.delete();other.delete()}
    }
    @Test fun sameSizeTamperingWithRestoredMtimeAndPathReplacementInvalidateCachedInspection() {
        val f=file();val expected=descriptor(f);val memo=TurnModelInspection(::stamp)
        try {
            memo.inspect(f,expected) { descriptor(f) to metadata }
            val before=stamp(f);val time=Files.getLastModifiedTime(f.toPath())
            RandomAccessFile(f,"rw").use { it.seek(31);it.write(1) };Files.setLastModifiedTime(f.toPath(),time)
            assertEquals(before.modifiedNanos,stamp(f).modifiedNanos);assertNotEquals(before,stamp(f))
            rejects { memo.inspect(f,expected) { descriptor(f) to metadata } };assertFalse(memo.reused)
            val replacement=file();replacement.renameTo(f)
            val fresh=descriptor(f);memo.inspect(f,fresh) { fresh to metadata }
            val replacement2=file();replacement2.renameTo(f)
            var rescanned=false;memo.inspect(f,descriptor(f)) { rescanned=true;descriptor(f) to metadata }
            assertTrue(rescanned)
        }finally{f.delete()}
    }
    @Test fun changedExpectedDigestTornInspectionAndChangedHandoffDescriptorFailClosed() {
        val f=file();val expected=descriptor(f);var opened=stamp(f)
        val memo=TurnModelInspection(::stamp,{opened})
        try {
            memo.inspect(f,expected) { descriptor(f) to metadata }
            memo.requireSameDescriptor(f,FileDescriptor())
            opened=opened.copy(inode=opened.inode+1);rejects { memo.requireSameDescriptor(f,FileDescriptor()) }
            rejects { memo.inspect(f,expected.copy(sha256="a".repeat(64))) { descriptor(f) to metadata } }
            val torn=TurnModelInspection(::stamp)
            rejects { torn.inspect(f,expected) { f.appendBytes(byteArrayOf(2));expected to metadata } }
        }finally{f.delete()}
    }
    @Test fun androidStampRejectsSymlinksAndHardlinksAndRetainsNanosecondChangeTime() {
        fun stat(mode:Int=OsConstants.S_IFREG,links:Long=1)=StructStat(1,2,mode,links,3,4,0,32,
            StructTimespec(0,0),StructTimespec(10,11),StructTimespec(12,13),4096,1)
        val s=ModelFileStamp.from(stat());assertEquals(11L,s.modifiedNanos);assertEquals(13L,s.changedNanos)
        for(bad in listOf(stat(OsConstants.S_IFLNK),stat(links=2))) {
            try{ModelFileStamp.from(bad);fail("Unsafe file stamp") }catch(_:IllegalArgumentException){}
        }
    }
    @Test @Config(sdk=[26]) fun oldAndroidWithoutNanosecondStatsAlwaysRescans() {
        val f=file();var scans=0
        try {
            val memo=TurnModelInspection({ stamp(it).copy(modifiedNanos=-1,changedNanos=-1) });val expected=descriptor(f)
            repeat(2) { memo.inspect(f,expected) { scans++;expected to metadata } }
            assertEquals(2,scans);assertFalse(memo.reused)
        }finally{f.delete()}
    }
}
