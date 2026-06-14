package ad.skip.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.getIntOrNull
import kotlinx.serialization.Serializable


@Serializable
data class History(
    val id: Long = 0,
    val packageName: String,
    val ruleId: Long,
    val succeeded: Boolean,
    val time: Long,
)


object HistoryTable : BasicTable<History>(Db.TABLE_HISTORY) {
    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): History {
        return History(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            packageName = cursor.getString(cursor.getColumnIndex(Db.COLUMN_PKG_NAME)),
            ruleId = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_RULE_ID)),
            succeeded = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_RULE_ID)) == 1,
            time = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_TIME)),
        )
    }

    override fun toContentValues(
        item: History,
        includeId: Boolean
    ): ContentValues {
        return ContentValues().apply {
            if (includeId) put(Db.COLUMN_ID, item.id)
            put(Db.COLUMN_PKG_NAME, item.packageName)
            put(Db.COLUMN_RULE_ID, item.ruleId)
            put(Db.COLUMN_SUCCEEDED, if(item.succeeded) 1 else 0)
            put(Db.COLUMN_TIME, item.time)
        }
    }
}