package com.mavyy.localyuki.admission

import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.os.Build
import com.mavyy.localyuki.foundation.admission.ModelDescriptor
import java.io.File
import java.io.FileDescriptor

/** Only private model files are eligible, and only for the lifetime of one bounded turn. */
internal data class ModelFileStamp(val device:Long,val inode:Long,val bytes:Long,val mode:Int,val uid:Int,val gid:Int,
    val links:Long,val modifiedSeconds:Long,val modifiedNanos:Long,val changedSeconds:Long,val changedNanos:Long) {
    companion object {
        internal fun from(s:StructStat):ModelFileStamp {
            require(OsConstants.S_ISREG(s.st_mode) && s.st_nlink==1L) { "Model must be a private regular file" }
            return if(Build.VERSION.SDK_INT>=27)ModelFileStamp(s.st_dev,s.st_ino,s.st_size,s.st_mode,s.st_uid,s.st_gid,s.st_nlink,
                s.st_mtim.tv_sec,s.st_mtim.tv_nsec,s.st_ctim.tv_sec,s.st_ctim.tv_nsec)
                else ModelFileStamp(s.st_dev,s.st_ino,s.st_size,s.st_mode,s.st_uid,s.st_gid,s.st_nlink,s.st_mtime,-1,s.st_ctime,-1)
        }
        fun read(file:File)=from(Os.lstat(file.path))
        fun read(fd:FileDescriptor)=from(Os.fstat(fd))
    }
}
internal class TurnModelInspection(private val stamp:(File)->ModelFileStamp=ModelFileStamp::read,
    private val descriptorStamp:(FileDescriptor)->ModelFileStamp=ModelFileStamp::read) {
    private data class Verified(val descriptor:ModelDescriptor,val metadata:GgufMetadata,val stamp:ModelFileStamp)
    private val verified=mutableMapOf<String,Verified>()
    var reused=false;private set
    fun inspect(file:File,expected:ModelDescriptor,verify:()->Pair<ModelDescriptor,GgufMetadata>):Pair<ModelDescriptor,GgufMetadata> {
        reused=false
        val before=stamp(file)
        val old=verified[file.path]
        if(before.modifiedNanos>=0 && before.changedNanos>=0 && old!=null && old.stamp==before && old.descriptor.copy(role=expected.role)==expected) {
            reused=true
            return expected to old.metadata
        }
        verified.remove(file.path)
        val fresh=verify()
        check(fresh.first==expected && before==stamp(file)) { "Model changed during integrity inspection" }
        verified[file.path]=Verified(fresh.first,fresh.second,before)
        return fresh
    }
    fun requireSameDescriptor(file:File,fd:FileDescriptor) {
        val old=verified[file.path]?:error("Model inspection missing")
        check(old.stamp==descriptorStamp(fd)) { "Model changed before runtime descriptor handoff" }
    }
}
