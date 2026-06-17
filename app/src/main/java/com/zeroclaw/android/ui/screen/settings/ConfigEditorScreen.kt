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
import androidx.compose.runtime.LaunchedEffect
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
import java.io.File

private const val CONFIG_FIELD_MIN_LINES = 16
private const val MIN_TOUCH_TARGET_DP = 48
private const val SECTION_SPACING_DP = 16

private val DEFAULT_TEMPLATE = """
# ZeroClaw configuration override
# Uncomment and modify fields as needed.
# This file overrides settings when the daemon starts (if non-empty).

# [gateway]
# host = "127.0.0.1"
# port = 8080

# [provider]
# model = "gpt-4o"
""".trimIndent()

/**
 * Full-screen editor for the daemon's TOML configuration.
 *
 * Loads the live config from the FFI layer (if running), otherwise
 * reads [config_override.toml] from disk or shows a default template.
 * Validates with [validateConfig] and saves to config_override.toml,
 * optionally restarting the daemon on save.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
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
    var isDaemonRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isLoaded) return@LaunchedEffect
        isLoaded = true
        isDaemonRunning = bridge.serviceState.value == ServiceState.RUNNING
        try {
            configText = bridge.fetchRunningConfig()
        } catch (_: Exception) {
            val overrideFile = File(context.filesDir, "config_override.toml")
            configText =
                if (overrideFile.exists()) {
                    try {
                        overrideFile.readText()
                    } catch (_: Exception) {
                        "# " + context.getString(R.string.config_editor_load_error)
                    }
                } else {
                    DEFAULT_TEMPLATE
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
                        if (isDaemonRunning) {
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
                        }
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.config_editor_save_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                    } catch (e: Exception) {
                        if (validationError == null) {
                            Toast
                                .makeText(
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MIN_TOUCH_TARGET_DP.dp)
                    .semantics { contentDescription = saveDesc },
        ) {
            Text(
                if (isSaving) {
                    "..."
                } else {
                    context.getString(R.string.config_editor_save)
                },
            )
        }
        Spacer(modifier = Modifier.height(SECTION_SPACING_DP.dp))
    }
}
