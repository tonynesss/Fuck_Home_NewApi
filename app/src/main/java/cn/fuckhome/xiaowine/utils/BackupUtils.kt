package cn.fuckhome.xiaowine.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Exports / imports the module configuration as JSON through the Storage Access Framework.
 * `Float` values are encoded as `Float:<int>` because JSON has no exact float round trip here.
 */
object BackupUtils {

    /** Serialises [prefs] to [uri]. Returns an error message, or `null` on success. */
    fun write(context: Context, uri: Uri, prefs: SharedPreferences): String? = try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            BufferedWriter(OutputStreamWriter(stream)).use { writer ->
                writer.write(
                    JSONObject().also { json ->
                        for ((key, value) in prefs.all) {
                            when (value) {
                                is Float -> json.put(key, "Float:" + (value * 1000).toInt().toString())
                                else -> json.put(key, value)
                            }
                        }
                    }.toString()
                )
            }
        }
        null
    } catch (e: Throwable) {
        e.toString()
    }

    /** Restores [prefs] from [uri]. Returns an error message, or `null` on success. */
    fun read(context: Context, uri: Uri, prefs: SharedPreferences): String? = try {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader -> reader.readText() }
        } ?: throw IllegalStateException("无法读取所选文件")

        val editor = prefs.edit()
        JSONObject(content).apply {
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = get(key)) {
                    is String -> {
                        if (value.startsWith(FLOAT_PREFIX)) {
                            editor.putFloat(key, value.substring(FLOAT_PREFIX.length).toFloat() / 1000)
                        } else {
                            editor.putString(key, value)
                        }
                    }

                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                }
            }
        }
        editor.apply()
        null
    } catch (e: Throwable) {
        e.toString()
    }

    private const val FLOAT_PREFIX = "Float:"
}
