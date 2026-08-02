package io.github.joelkanyi.peek.runtime

import android.content.Context
import android.content.SharedPreferences
import io.github.joelkanyi.peek.wire.Message
import io.github.joelkanyi.peek.wire.StoreInfo
import io.github.joelkanyi.peek.wire.StoreKind
import io.github.joelkanyi.peek.wire.WireEntry
import io.github.joelkanyi.peek.wire.WireValue
import java.io.File

/**
 * Serves the app's live SharedPreferences: enumerate, read, edit, and notify on
 * change. Edits go through the real [SharedPreferences.Editor], so the app's own
 * listeners fire and it reacts without a restart.
 */
internal class SharedPreferencesSource(private val context: Context) {

    private val open = HashMap<String, SharedPreferences>()
    private val listeners = HashMap<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    @Volatile
    var onChanged: ((storeId: String) -> Unit)? = null

    fun listStores(): List<StoreInfo> {
        val dir = File(context.dataDir, "shared_prefs")
        val names = dir.listFiles { file -> file.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            .orEmpty()
        return names.map { name ->
            ensureListening(name)
            StoreInfo(id = "sp:$name", displayName = "$name.xml", kind = StoreKind.SHARED_PREFERENCES)
        }
    }

    fun readStore(storeId: String): Message {
        val prefs = prefsFor(storeId) ?: return Message.Err("unknown store $storeId")
        val entries = prefs.all.mapNotNull { (key, value) -> value?.let { WireEntry(key, toWire(it)) } }
        return Message.StoreData(storeId, entries)
    }

    fun put(storeId: String, key: String, value: WireValue): Message {
        val editor = prefsFor(storeId)?.edit() ?: return Message.Err("unknown store $storeId")
        when (value) {
            is WireValue.BoolValue -> editor.putBoolean(key, value.value)
            is WireValue.IntValue -> editor.putInt(key, value.value)
            is WireValue.LongValue -> editor.putLong(key, value.value)
            is WireValue.FloatValue -> editor.putFloat(key, value.value)
            is WireValue.StringValue -> editor.putString(key, value.value)
            is WireValue.StringSetValue -> editor.putStringSet(key, value.values.toSet())
            is WireValue.DoubleValue, is WireValue.BytesValue ->
                return Message.Err("SharedPreferences cannot hold that type")
        }
        editor.apply()
        return Message.Ok
    }

    fun remove(storeId: String, key: String): Message {
        val prefs = prefsFor(storeId) ?: return Message.Err("unknown store $storeId")
        prefs.edit().remove(key).apply()
        return Message.Ok
    }

    private fun prefsFor(storeId: String): SharedPreferences? {
        if (!storeId.startsWith("sp:")) return null
        val name = storeId.removePrefix("sp:")
        return open.getOrPut(name) { context.getSharedPreferences(name, Context.MODE_PRIVATE) }
    }

    private fun ensureListening(name: String) {
        val prefs = open.getOrPut(name) { context.getSharedPreferences(name, Context.MODE_PRIVATE) }
        if (name !in listeners) {
            // Strong ref kept in the map (prefs holds only a weak reference).
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged?.invoke("sp:$name") }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            listeners[name] = listener
        }
    }

    private fun toWire(value: Any): WireValue = when (value) {
        is Boolean -> WireValue.BoolValue(value)
        is Int -> WireValue.IntValue(value)
        is Long -> WireValue.LongValue(value)
        is Float -> WireValue.FloatValue(value)
        is String -> WireValue.StringValue(value)
        is Set<*> -> WireValue.StringSetValue(value.filterIsInstance<String>())
        else -> WireValue.StringValue(value.toString())
    }
}
