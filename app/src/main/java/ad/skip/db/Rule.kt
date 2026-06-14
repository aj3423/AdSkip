package ad.skip.db

import ad.skip.query.NodeQuery
import ad.skip.util.IAction
import ad.skip.util.parseActon
import ad.skip.util.serialize
import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import kotlinx.serialization.Serializable


@Serializable
data class Rule(
    val id: Long = 0,
    val desc: String,
    val packageName: String,
    val activityName: String? = null,
    val queryPath: String,
    val action: IAction
) {
    internal val parsedQuery by lazy(LazyThreadSafetyMode.NONE) {
        NodeQuery.parse(queryPath)
    }
}



object RuleTable : BasicTable<Rule>(Db.TABLE_RULE) {
    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): Rule {
        return Rule(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            desc = cursor.getString(cursor.getColumnIndex(Db.COLUMN_DESC)),
            packageName = cursor.getString(cursor.getColumnIndex(Db.COLUMN_PKG_NAME)),
            activityName = cursor.getString(cursor.getColumnIndex(Db.COLUMN_ACTIVITY)),
            queryPath = cursor.getString(cursor.getColumnIndex(Db.COLUMN_QUERY_PATH)),
            action = cursor.getString(cursor.getColumnIndex(Db.COLUMN_ACTION)).parseActon(),
        )
    }

    override fun toContentValues(
        item: Rule,
        includeId: Boolean
    ): ContentValues {
        return ContentValues().apply {
            if (includeId) put(Db.COLUMN_ID, item.id)
            put(Db.COLUMN_DESC, item.desc)
            put(Db.COLUMN_PKG_NAME, item.packageName)
            put(Db.COLUMN_ACTIVITY, item.activityName)
            put(Db.COLUMN_QUERY_PATH, item.queryPath)
            put(Db.COLUMN_ACTION, item.action.serialize())
        }
    }
}