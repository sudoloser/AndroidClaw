package com.zeroclaw.android.ui.screen.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.R
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.service.BackupManager
import com.zeroclaw.android.service.BackupOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as ZeroClawApplication }
    val scope = rememberCoroutineScope()

    var selectAll by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(true) }
    var includeApiKeys by remember { mutableStateOf(true) }
    var includeChannels by remember { mutableStateOf(true) }
    var includeAgents by remember { mutableStateOf(true) }
    var includePlugins by remember { mutableStateOf(true) }
    var includeConfigOverride by remember { mutableStateOf(true) }
    var includeActivity by remember { mutableStateOf(false) }
    var includeLogs by remember { mutableStateOf(false) }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingBackupFile by remember { mutableStateOf<File?>(null) }
    var restoreConfirmUri by remember { mutableStateOf<Uri?>(null) }
    var restoreEntries by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var restoreSectionNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var backupFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshBackupFiles() {
        backupFiles = BackupManager.listBackupFiles(context)
    }

    refreshBackupFiles()

    fun updateSelectAll() {
        selectAll = includeSettings && includeApiKeys && includeChannels &&
            includeAgents && includePlugins && includeConfigOverride
    }

    val saveBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            val file = pendingBackupFile ?: return@rememberLauncherForActivityResult
            if (uri == null) {
                file.delete()
                pendingBackupFile = null
                isBackingUp = false
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        BackupManager.writeBackupToUri(context, file, uri)
                    }
                    statusMessage = context.getString(R.string.backup_success, file.name)
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    statusMessage = context.getString(R.string.backup_error) + ": " + e.message
                } finally {
                    file.delete()
                    pendingBackupFile = null
                    isBackingUp = false
                    refreshBackupFiles()
                }
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val entries = withContext(Dispatchers.IO) {
                        BackupManager.readZipEntriesFromUri(context, uri)
                    }
                    restoreSectionNames = deriveSections(entries)
                    restoreEntries = entries
                    restoreConfirmUri = uri
                } catch (e: Exception) {
                    statusMessage = context.getString(R.string.backup_restore_error) + ": " + e.message
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = context.getString(R.string.backup_restore_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = context.getString(R.string.backup_restore_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        CheckboxRow(
            label = context.getString(R.string.backup_select_all),
            checked = selectAll,
            onCheck = {
                selectAll = it
                includeSettings = it
                includeApiKeys = it
                includeChannels = it
                includeAgents = it
                includePlugins = it
                includeConfigOverride = it
                includeActivity = false
                includeLogs = false
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        CheckboxRow(
            label = context.getString(R.string.backup_settings),
            checked = includeSettings,
            onCheck = { includeSettings = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_api_keys),
            checked = includeApiKeys,
            onCheck = { includeApiKeys = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_channels),
            checked = includeChannels,
            onCheck = { includeChannels = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_agents),
            checked = includeAgents,
            onCheck = { includeAgents = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_plugins),
            checked = includePlugins,
            onCheck = { includePlugins = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_config_override),
            checked = includeConfigOverride,
            onCheck = { includeConfigOverride = it; updateSelectAll() },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_activity),
            checked = includeActivity,
            onCheck = { includeActivity = it },
        )
        CheckboxRow(
            label = context.getString(R.string.backup_logs),
            checked = includeLogs,
            onCheck = { includeLogs = it },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isBackingUp = true
                    statusMessage = null
                    try {
                        val options = BackupOptions(
                            includeSettings = includeSettings,
                            includeApiKeys = includeApiKeys,
                            includeChannels = includeChannels,
                            includeAgents = includeAgents,
                            includePlugins = includePlugins,
                            includeConfigOverride = includeConfigOverride,
                            includeActivity = includeActivity,
                            includeLogs = includeLogs,
                        )
                        val tempFile = withContext(Dispatchers.IO) {
                            BackupManager.createBackup(
                                context = context,
                                settingsRepo = app.settingsRepository,
                                apiKeyRepo = app.apiKeyRepository,
                                channelRepo = app.channelConfigRepository,
                                agentRepo = app.agentRepository,
                                pluginRepo = app.pluginRepository,
                                activityRepo = app.activityRepository,
                                logRepo = app.logRepository,
                                options = options,
                            )
                        }
                        pendingBackupFile = tempFile
                        refreshBackupFiles()
                        saveBackupLauncher.launch(BackupManager.suggestedBackupName())
                    } catch (e: Exception) {
                        isBackingUp = false
                        statusMessage = context.getString(R.string.backup_error) + ": " + e.message
                    }
                }
            },
            enabled = !isBackingUp && !isRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isBackingUp) "..." else context.getString(R.string.backup_action))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            enabled = !isBackingUp && !isRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(context.getString(R.string.backup_restore_from_file))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusMessage != null) {
            Text(
                text = statusMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (backupFiles.isNotEmpty()) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.backup_available_backups),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            backupFiles.forEach { file ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(
                    Date(file.lastModified()),
                )
                val sections = BackupManager.getBackupSections(file)
                val sectionList = sections?.sections?.joinToString(", ").orEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val entries = withContext(Dispatchers.IO) {
                                        BackupManager.readZipEntries(file)
                                    }
                                    restoreSectionNames = deriveSections(entries)
                                    restoreEntries = entries
                                    restoreConfirmUri = Uri.fromFile(file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isRestoring,
                    ) {
                        Text(context.getString(R.string.backup_restore_action))
                    }
                }
                Text(
                    text = dateStr + " — " + sectionList,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (restoreConfirmUri != null) {
        val fileName = restoreConfirmUri!!.lastPathSegment ?: "backup"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreConfirmUri = null },
            title = { Text(context.getString(R.string.backup_restore_confirm_title)) },
            text = {
                Text(context.getString(R.string.backup_restore_confirm_message, fileName))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val uri = restoreConfirmUri!!
                    restoreConfirmUri = null
                    scope.launch {
                        isRestoring = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                BackupManager.restoreFromEntries(
                                    context = context,
                                    entries = restoreEntries,
                                    settingsRepo = app.settingsRepository,
                                    apiKeyRepo = app.apiKeyRepository,
                                    channelRepo = app.channelConfigRepository,
                                    agentRepo = app.agentRepository,
                                    pluginRepo = app.pluginRepository,
                                    activityRepo = app.activityRepository,
                                    logRepo = app.logRepository,
                                    sections = restoreSectionNames,
                                )
                            }
                            statusMessage = result
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            statusMessage = context.getString(R.string.backup_restore_error) + ": " + e.message
                        } finally {
                            isRestoring = false
                        }
                    }
                }) {
                    Text(context.getString(R.string.backup_restore_action))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restoreConfirmUri = null }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }
}

private fun deriveSections(entries: Map<String, String>): List<String> {
    val manifestJson = entries["manifest.json"]
    if (manifestJson != null) {
        try {
            return org.json.JSONObject(manifestJson).optJSONArray("sections")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                .orEmpty()
        } catch (_: Exception) { }
    }
    return entries.keys.mapNotNull { key ->
        when (key) {
            "settings.json" -> "settings"
            "api_keys.json" -> "api_keys"
            "channels.json" -> "channels"
            "agents.json" -> "agents"
            "config_override.toml" -> "config_override"
            "activity.json" -> "activity"
            "logs.json" -> "logs"
            "plugins.json" -> "plugins"
            else -> null
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheck,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
