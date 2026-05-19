package com.agon.app.utils

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object AssetLoader {
    fun loadScript(context: Context, fileName: String = "blocker.js"): String {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
