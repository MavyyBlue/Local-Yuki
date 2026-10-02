package com.mavyy.localyuki.brain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.mavyy.localyuki.continuity.ContinuityHelper
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*

/** Shared error/transaction plumbing, confined to the trusted app module. */
internal class BrainDatabase(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val helper = ContinuityHelper(app,!app.getDatabasePath(ContinuitySchema.NAME).exists())
    class Conflict : RuntimeException()
    fun rows(db: SQLiteDatabase, sql: String, vararg args: String): List<List<String?>> = db.rawQuery(sql,args).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).map(c::getString)) }
    }
    fun <T : Any> read(block: (SQLiteDatabase) -> T): FoundationResult<T> = guarded { block(helper.readableDatabase) }
    fun <T : Any> write(block: (SQLiteDatabase) -> T): FoundationResult<T> = guarded {
        val db=helper.writableDatabase; db.beginTransaction()
        try { val result=block(db);db.setTransactionSuccessful();result } finally { db.endTransaction() }
    }
    private fun <T : Any> guarded(block: () -> T): FoundationResult<T> = try { FoundationResult.Success(block()) }
        catch (_: Conflict) { FoundationResult.Failure(FailureCategory.CONFLICT) }
        catch (_: IllegalArgumentException) { FoundationResult.Failure(FailureCategory.INVALID_INPUT) }
        catch (_: java.time.DateTimeException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
        catch (_: SQLiteException) { FoundationResult.Failure(FailureCategory.CONFLICT) }
        catch (_: Exception) { FoundationResult.Failure(FailureCategory.INTERNAL_FAILURE) }
    override fun close() = helper.close()
}
