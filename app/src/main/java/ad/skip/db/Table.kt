package ad.skip.db

import ad.skip.db.Db.Companion.COLUMN_ID
import android.content.ContentValues
import android.content.Context
import android.database.Cursor


interface TableAdapter<T> {
    fun fromCursor(cursor: Cursor): T
    fun toContentValues(item: T, includeId: Boolean = false): ContentValues
}

abstract class BasicTable<T>(
    private val tableName: String,
) : TableAdapter<T> {
    fun addNew(ctx: Context, item: T): Long {
        val db = Db.getInstance(ctx).writableDatabase
        return db.insert(tableName, null, toContentValues(item, includeId = false))
    }

    fun addWithId(ctx: Context, item: T) {
        val db = Db.getInstance(ctx).writableDatabase
        db.insert(tableName, null, toContentValues(item, includeId = true))
    }

    fun updateById(ctx: Context, id: Long, item: T): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        return db.update(tableName, toContentValues(item), "${Db.COLUMN_ID} = ?", arrayOf(id.toString())) > 0
    }
    fun listAll(
        ctx: Context,
        whereClause: String? = null,
        whereParams: Array<String>? = null,
        limit: Int? = null
    ): List<T> {
        var sql = "SELECT * FROM $tableName"
        whereClause?.let { sql += " WHERE $it" }
        limit?.let { sql += " LIMIT $it" }

        val ret = mutableListOf<T>()
        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, whereParams)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    ret += fromCursor(it)
                } while (it.moveToNext())
            }
        }
        return ret
    }

    fun findFirst(
        ctx: Context,
        whereClause: String,
        whereParams: Array<String>? = null,
    ): T? {
        return listAll(ctx, whereClause, whereParams, limit = 1).firstOrNull()
    }
    fun findById(
        ctx: Context,
        id: Long
    ): T? {
        return findFirst(ctx, "$COLUMN_ID = ?", arrayOf("$id"))
    }

    fun clearAll(ctx: Context) {
        Db.getInstance(ctx).writableDatabase.execSQL("DELETE FROM $tableName")
    }

    open fun deleteById(ctx: Context, id: Long): Int {
        return Db.getInstance(ctx).writableDatabase
            .delete(tableName, "${Db.COLUMN_ID} = ?", arrayOf(id.toString()))
    }
}
