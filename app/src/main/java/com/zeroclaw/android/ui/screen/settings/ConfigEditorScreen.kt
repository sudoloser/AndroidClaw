package com.zeroclaw.android.ui.screen.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.R
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.ServiceState
import com.zeroclaw.android.service.ZeroClawDaemonService
import com.zeroclaw.ffi.validateConfig
import com.zeroclaw.ffi.writeConfigFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONFIG_FIELD_MIN_LINES = 16
private const val MIN_TOUCH_TARGET_DP = 48
private const val SECTION_SPACING_DP = 16

@Composable
fun ConfigEditorScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as ZeroClawApplication }
    val bridge = remember { app.daemonBridge }
    val scope = rememberCoroutineScope()
    val saveDesc = context.getString(R.string.config_editor_save_tooltip)

    var configText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    if (!isLoaded) {
        isLoaded = true
        scope.launch {
            try {
                configText = bridge.fetchRunningConfig()
            } catch (e: Exception) {
                configText = "# " + context.getString(R.string.config_editor_load_error) + "\n# " + e.message
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = edgeMargin)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
        Text(
            text = context.getString(R.string.config_editor_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
        Text(
            text = context.getString(R.string.config_editor_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        OutlinedTextField(
            value = configText,
            onValueChange = { newText ->
                configText = newText
                validationError = null
            },
            label = { Text("TOML Config") },
            isError = validationError != null,
            supportingText = validationError?.let { msg -> { Text(msg) } },
            minLines = CONFIG_FIELD_MIN_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))

        FilledTonalButton(
            onClick = {
                scope.launch {
                    isSaving = true
                    try {
                        withContext(Dispatchers.IO) {
                            validateConfig(configText)
                        }.let { err ->
                            if (err.isNotEmpty()) {
                                validationError = err
                                return@launch
                            }
                        }
                        val dataDir = context.filesDir.absolutePath
                        withContext(Dispatchers.IO) {
                            writeConfigFile(dataDir, configText)
                        }
                        val stopIntent =
                            Intent(context, ZeroClawDaemonService::class.java).apply {
                                action = ZeroClawDaemonService.ACTION_STOP
                            }
                        context.startService(stopIntent)
                        app.daemonBridge.serviceState.first {
                            it == ServiceState.STOPPED || it == ServiceState.ERROR
                        }
                        val startIntent =
                            Intent(context, ZeroClawDaemonService::class.java).apply {
                                action = ZeroClawDaemonService.ACTION_START
                            }
                        context.startForegroundService(startIntent)
                        Toast.makeText(
                            context,
                            context.getString(R.string.config_editor_save_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (e: Exception) {
                        if (validationError == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.config_editor_save_error) + ": " + e.message,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving && validationError == null && configText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MIN_TOUCH_TARGET_DP.dp)
                .semantics { contentDescription = saveDesc },
        ) {
            Text(
                if (isSaving) "..."
                else context.getString(R.string.config_editor_save)
            )
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
    }
}
