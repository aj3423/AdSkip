package ad.skip.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class spf { // for namespace only

    open class SharedPref(ctx: Context) {
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val PREFS_NAME = "settings"
        }

        fun str(
            key: String,
            defaultValue: String = ""
        ) : ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String {
                return prefs.getString(key, defaultValue) ?: defaultValue
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
                prefs.edit { putString(key, value) }
            }
        }

        fun int(
            key: String,
            defaultValue: Int = 0
        ) : ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                return prefs.getInt(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                prefs.edit { putInt(key, value) }
            }
        }

        fun long(
            key: String,
            defaultValue: Long = 0
        ) : ReadWriteProperty<Any?, Long> = object : ReadWriteProperty<Any?, Long> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Long {
                return prefs.getLong(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
                prefs.edit { putLong(key, value) }
            }
        }

        fun bool(
            key: String,
            defaultValue: Boolean = false
        ) : ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
                return prefs.getBoolean(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
                prefs.edit { putBoolean(key, value) }
            }
        }

        // Write nothing, wait for all async operations to complete.
        // Use this before restarting the app process.
        fun flush() {
            // Simulate flush by calling commit() on a new editor
            prefs.edit(commit = true) {
            }
        }

        fun clear() {
            prefs.edit() {
                clear()
            }
        }
    }

    class QueryFieldBan(ctx: Context) : SharedPref(ctx) {
        fun get(packageName: String): Set<String> =
            prefs.getStringSet(key(packageName), emptySet()).orEmpty().toSet()

        fun set(packageName: String, fields: Set<String>) {
            prefs.edit { putStringSet(key(packageName), fields.toSet()) }
        }

        private fun key(packageName: String): String =
            "query_banned_fields_$packageName"
    }
}
