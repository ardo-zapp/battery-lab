package com.jacktor.batterylab.databases

object HistoryContract {
    const val DB_NAME = "History.db"
    const val DB_VERSION = 1
    const val TABLE_NAME = "History"

    object Columns {
        const val ID = "id"
        const val DATE = "Date"
        const val RESIDUAL_CAPACITY = "Residual_Capacity"
    }

    const val SQL_CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
            ${Columns.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${Columns.DATE} TEXT,
            ${Columns.RESIDUAL_CAPACITY} INTEGER
        )
    """
}
