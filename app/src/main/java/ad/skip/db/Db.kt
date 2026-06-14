package ad.skip.db

import ad.skip.util.logi
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db private constructor(
    val ctx: Context
) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 1
        const val DB_NAME = "ad_skip.db"

        // ---- snapshot table ----
        const val TABLE_SNAPSHOT = "snapshots"

        const val COLUMN_ID = "id"
        const val COLUMN_DESC = "description"

        const val COLUMN_PKG_NAME = "package_name"
        const val COLUMN_ACTIVITY = "activity"
        const val COLUMN_SCREEN_WIDTH = "screen_width"
        const val COLUMN_SCREEN_HEIGHT = "screen_height"
        const val COLUMN_SCREENSHOT = "screenshot"
        const val COLUMN_ROOT = "root"

        // ---- rule table ----
        const val TABLE_RULE = "rules"
        const val COLUMN_QUERY_PATH = "query_path"
        const val COLUMN_ACTION = "actions" // `action` is a keyword?

        // ---- history table ----
        const val TABLE_HISTORY = "spam"
        const val COLUMN_TIME = "time"
        const val COLUMN_RULE_ID = "rule_id"
        const val COLUMN_SUCCEEDED = "succeeded"


        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Db? = null

        fun getInstance(context: Context): Db {
            return instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {

        // snapshot database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_SNAPSHOT (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_PKG_NAME TEXT, " +
                    "$COLUMN_ACTIVITY TEXT, " +
                    "$COLUMN_SCREEN_WIDTH INTEGER, " +
                    "$COLUMN_SCREEN_HEIGHT INTEGER, " +
                    "$COLUMN_SCREENSHOT TEXT, " +
                    "$COLUMN_ROOT TEXT" +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_snapshot_pkg_name ON $TABLE_SNAPSHOT($COLUMN_PKG_NAME)")

        // rule database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_RULE (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_PKG_NAME TEXT, " +
                    "$COLUMN_ACTIVITY TEXT, " +
                    "$COLUMN_QUERY_PATH TEXT," +
                    "$COLUMN_ACTION TEXT" +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_pkg_name ON $TABLE_RULE($COLUMN_PKG_NAME)")

        // history database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PKG_NAME TEXT, " +
                    "$COLUMN_RULE_ID INTEGER, " +
                    "$COLUMN_SUCCEEDED INTEGER, " +
                    "$COLUMN_TIME INTEGER " +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_pkg_name ON $TABLE_HISTORY($COLUMN_PKG_NAME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logi("upgrading db $oldVersion -> $newVersion")

    }
}