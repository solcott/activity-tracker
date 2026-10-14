package io.github.solcott.activitytracker.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal actual suspend fun SQLiteConnection.runSql(sql: String) = execSQL(sql)
