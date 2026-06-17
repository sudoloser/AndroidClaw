package com.zeroclaw.android.ui.screen.settings

import android.widget.Toast
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
import androidx.compose.material3.ButtonDefaults
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
    var includeConfigOverride by remember { mutableStateOf(true) }
    var includeActivity by remember { mutableStateOf(false) }
    var includeLogs by remember { mutableStateOf(false) }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var backupFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showFilePicker by remember { mutableStateOf(false) }
    var selectedBackupFile by remember { mutableStateOf<File?>(null) }
    var restoreSections by remember { mutableStateOf<List<String>>(emptyList()) }

    fun updateSelectAll() {
        selectAll = includeSettings && includeApiKeys && includeChannels &&
            includeAgents && includeConfigOverride
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
                            includeConfigOverride = includeConfigOverride,
                            includeActivity = includeActivity,
                            includeLogs = includeLogs,
                        )
                        val file = withContext(Dispatchers.IO) {
                            BackupManager.createBackup(
                                context = context,
                                settingsRepo = app.settingsRepository,
                                apiKeyRepo = app.apiKeyRepository,
                                channelRepo = app.channelConfigRepository,
                                agentRepo = app.agentRepository,
                                activityRepo = app.activityRepository,
                                logRepo = app.logRepository,
                                options = options,
                            )
                        }
                        backupFiles = BackupManager.listBackupFiles(context)
                        statusMessage = context.getString(R.string.backup_success, file.name)
                    } catch (e: Exception) {
                        statusMessage = context.getString(R.string.backup_error) + ": " + e.message
                    } finally {
                        isBackingUp = false
                    }
                }
            },
            enabled = !isBackingUp && !isRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isBackingUp) "..." else context.getString(R.string.backup_action))
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

        val files = remember { BackupManager.listBackupFiles(context) }
        if (files.isNotEmpty()) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.backup_available_backups),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            files.forEach { file ->
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
                            selectedBackupFile = file
                            restoreSections = sections?.sections.orEmpty()
                            showFilePicker = true
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

    if (showFilePicker && selectedBackupFile != null) {
        val file = selectedBackupFile!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFilePicker = false; selectedBackupFile = null },
            title = { Text(context.getString(R.string.backup_restore_confirm_title)) },
            text = {
                Text(context.getString(R.string.backup_restore_confirm_message, file.name))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showFilePicker = false
                    scope.launch {
                        isRestoring = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                BackupManager.restoreBackup(
                                    context = context,
                                    backupFile = file,
                                    settingsRepo = app.settingsRepository,
                                    apiKeyRepo = app.apiKeyRepository,
                                    channelRepo = app.channelConfigRepository,
                                    agentRepo = app.agentRepository,
                                    activityRepo = app.activityRepository,
                                    logRepo = app.logRepository,
                                    sections = restoreSections,
                                )
                            }
                            statusMessage = result
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            statusMessage = context.getString(R.string.backup_restore_error) + ": " + e.message
                        } finally {
                            isRestoring = false
                            selectedBackupFile = null
                        }
                    }
                }) {
                    Text(context.getString(R.string.backup_restore_action))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showFilePicker = false
                    selectedBackupFile = null
                }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
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
