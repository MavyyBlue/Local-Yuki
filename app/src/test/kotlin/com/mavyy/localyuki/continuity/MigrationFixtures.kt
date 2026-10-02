package com.mavyy.localyuki.continuity

import android.database.sqlite.SQLiteDatabase

/** Remove later tables when recreating a genuine pre-foundation schema fixture. */
internal fun dropAfterPhaseFive(db: SQLiteDatabase) {
    listOf("model_admission","model_candidate","scheduler_state","recovery_intention","recovery_state",
        "capability_package","capability_policy","affect_event","affect_association","affect_state")
        .forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
}
