package com.jacktor.batterylab.databases

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.DatabaseUtils
import com.jacktor.batterylab.databases.HistoryContract.Columns.DATE
import com.jacktor.batterylab.databases.HistoryContract.Columns.ID
import com.jacktor.batterylab.databases.HistoryContract.Columns.RESIDUAL_CAPACITY
import com.jacktor.batterylab.databases.HistoryContract.TABLE_NAME

class HistoryDB(context: Context) :
    SQLiteOpenHelper(context, HistoryContract.DB_NAME, null, HistoryContract.DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(HistoryContract.SQL_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Not implemented
    }

    fun insert(history: History) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put(DATE, history.date)
                put(RESIDUAL_CAPACITY, history.residualCapacity)
            }
            db.insert(TABLE_NAME, null, values)
        }
    }

    fun readAll(): List<History> {
        val list = mutableListOf<History>()
        readableDatabase.use { db ->
            db.rawQuery("SELECT * FROM $TABLE_NAME", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        History().apply {
                            id = cursor.getInt(cursor.getColumnIndexOrThrow(ID))
                            date = cursor.getString(cursor.getColumnIndexOrThrow(DATE))
                            residualCapacity =
                                cursor.getInt(cursor.getColumnIndexOrThrow(RESIDUAL_CAPACITY))
                        }
                    )
                }
            }
        }
        return list
    }

    fun clear() {
        writableDatabase.use { db ->
            db.delete(TABLE_NAME, null, null)
        }
    }

    fun removeFirstRow() {
        writableDatabase.use { db ->
            db.query(TABLE_NAME, null, null, null, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(ID))
                    db.delete(TABLE_NAME, "$ID=?", arrayOf(id.toString()))
                }
            }
        }
    }

    fun remove(capacity: Int) {
        val id = getIdByResidualCapacity(capacity)
        if (id != -1) {
            writableDatabase.use { db ->
                db.delete(TABLE_NAME, "$ID=?", arrayOf(id.toString()))
            }
        }
    }

    fun getCount(): Long {
        return readableDatabase.use { db ->
            DatabaseUtils.queryNumEntries(db, TABLE_NAME)
        }
    }

    private fun getIdByResidualCapacity(capacity: Int): Int {
        readableDatabase.use { db ->
            db.rawQuery(
                "SELECT $ID FROM $TABLE_NAME WHERE $RESIDUAL_CAPACITY=?",
                arrayOf(capacity.toString())
            ).use { cursor ->
                return if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(ID)) else -1
            }
        }
    }
}
