package com.mavyy.localyuki.embodiment

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.brain.BrainDatabase
import com.mavyy.localyuki.brain.BrainDatabase.Conflict
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.embodiment.*

data class CapabilitySetting(val policy: CapabilityPolicy,val revision: Long)
/** Actual producers/executors are wired by the app; owner controls cannot fabricate support. */
class CapabilityStore(context: Context, private val platform: (CapabilityId) -> Pair<Boolean,Boolean>,
    private val executors: Set<CapabilityId>) : CapabilityReader,AutoCloseable {
    private val store=BrainDatabase(context)
    private fun setting(db: SQLiteDatabase,id: CapabilityId): CapabilitySetting {
        val row=store.rows(db,"SELECT enabled,revision FROM capability_policy WHERE capability_id=?",id.name).singleOrNull() ?: throw Conflict()
        val revision=row[1]?.toLongOrNull()?.takeIf { it>=0 } ?: throw Conflict()
        if (row[0] !in listOf("0","1")) throw Conflict()
        val packages=store.rows(db,"SELECT package_name,decision FROM capability_package WHERE capability_id=? ORDER BY package_name LIMIT 65",id.name)
        val allowed=packages.filter { it[1]=="ALLOW" }.map { it[0] ?: throw Conflict() }.toSet()
        val denied=packages.filter { it[1]=="DENY" }.map { it[0] ?: throw Conflict() }.toSet()
        if (packages.any { it[1] !in listOf("ALLOW","DENY") } || packages.size>64) throw Conflict()
        return try { CapabilitySetting(CapabilityPolicy(row[0]=="1",allowed,denied),revision) } catch (_: IllegalArgumentException) { throw Conflict() }
    }
    fun setting(id: CapabilityId): FoundationResult<CapabilitySetting> = store.read { setting(it,id) }
    internal fun configure(id: CapabilityId,expectedRevision: Long,policy: CapabilityPolicy): FoundationResult<Long> = store.write { db ->
        val prior=setting(db,id)
        if (prior.revision!=expectedRevision || prior.revision==Long.MAX_VALUE) throw Conflict()
        db.execSQL("UPDATE capability_policy SET enabled=?,revision=? WHERE capability_id=?",arrayOf<Any?>(if(policy.enabled) 1 else 0,prior.revision+1,id.name))
        db.execSQL("DELETE FROM capability_package WHERE capability_id=?",arrayOf(id.name))
        policy.allowedPackages.forEach { db.execSQL("INSERT INTO capability_package VALUES (?,?,'ALLOW')",arrayOf(id.name,it)) }
        policy.deniedPackages.forEach { db.execSQL("INSERT INTO capability_package VALUES (?,?,'DENY')",arrayOf(id.name,it)) }
        prior.revision+1
    }
    override fun capabilities(): FoundationResult<List<CapabilityView>> = store.read { db ->
        if (store.rows(db,"SELECT capability_id FROM capability_policy").map { it[0] }.toSet()!=CapabilityId.entries.map { it.name }.toSet()) throw Conflict()
        CapabilityId.entries.map { id -> val policy=setting(db,id).policy;val actual=platform(id)
            CapabilityView(id,policy.enabled,actual.first,actual.second,id in executors) }
    }
    override fun permits(capability: CapabilityId,targetPackage: String?): FoundationResult<Boolean> = store.read { db ->
        val policy=setting(db,capability).policy;val actual=platform(capability)
        policy.enabled && actual.first && actual.second && capability in executors &&
            (targetPackage==null && policy.allowedPackages.isEmpty() || targetPackage!=null &&
                targetPackage !in policy.deniedPackages && (policy.allowedPackages.isEmpty() || targetPackage in policy.allowedPackages))
    }
    override fun close()=store.close()
}
