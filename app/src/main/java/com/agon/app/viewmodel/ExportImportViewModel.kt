package com.agon.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Export/Import operations.
 * Handles background I/O for blocklists.
 */
class ExportImportViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError = _isError.asStateFlow()

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val websites = repo.getBlocklist("blacklist", "websites")
                    val keywords = repo.getBlocklist("blacklist", "keywords")
                    val apps = repo.getFullBlocklist("blocked_apps")

                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val writer = os.bufferedWriter()
                        writer.write("# GuardSoul Blocklist Export\n")
                        writer.write("# Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")
                        
                        writer.write("# Websites\n")
                        websites.forEach { writer.write("${it.value}\n") }
                        
                        writer.write("\n# Keywords\n")
                        keywords.forEach { writer.write("kw:${it.value}\n") }
                        
                        writer.write("\n# Apps\n")
                        apps.forEach { writer.write("app:${it.value}\n") }
                        
                        writer.flush()
                    }
                    true
                } catch (e: Exception) {
                    AppLogger.e(e, "Export operation failed")
                    false
                }
            }
            _isError.value = !result
            _statusMessage.value = if (result) {
                getApplication<Application>().getString(R.string.export_success)
            } else {
                getApplication<Application>().getString(R.string.export_failed)
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            val importedCount = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val lines = inputStream.bufferedReader().readLines()
                        var count = 0
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
                            
                            when {
                                trimmed.startsWith("kw:", ignoreCase = true) -> {
                                    val kw = trimmed.removePrefix("kw:").trim()
                                    repo.addBlocklistItem("blacklist", "keywords", kw)
                                    count++
                                }
                                trimmed.startsWith("app:", ignoreCase = true) -> {
                                    val app = trimmed.removePrefix("app:").trim()
                                    repo.addBlocklistItem("blacklist", "apps", app)
                                    count++
                                }
                                else -> {
                                    repo.addBlocklistItem("blacklist", "websites", trimmed)
                                    count++
                                }
                            }
                        }
                        count
                    } ?: 0
                } catch (e: Exception) {
                    AppLogger.e(e, "Import operation failed")
                    -1
                }
            }
            
            if (importedCount >= 0) {
                _isError.value = false
                _statusMessage.value = getApplication<Application>().getString(R.string.import_success_count, importedCount)
            } else {
                _isError.value = true
                _statusMessage.value = getApplication<Application>().getString(R.string.import_failed)
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
