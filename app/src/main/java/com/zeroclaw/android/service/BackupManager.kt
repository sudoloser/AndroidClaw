package com.zeroclaw.android.service

import android.content.Context
import android.util.Log
import com.zeroclaw.android.model.ActivityEvent
import com.zeroclaw.android.model.ActivityType
import com.zeroclaw.android.model.Agent
import com.zeroclaw.android.model.ApiKey
import com.zeroclaw.android.model.AppSettings
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.ConnectedChannel
import com.zeroclaw.android.model.KeyStatus
import com.zeroclaw.android.model.LogEntry
import com.zeroclaw.android.model.LogSeverity
import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.data.repository.AgentRepository
import com.zeroclaw.android.data.repository.ApiKeyRepository
import com.zeroclaw.android.data.repository.ChannelConfigRepository
import com.zeroclaw.android.data.repository.LogRepository
import com.zeroclaw.android.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupOptions(
    val includeSettings: Boolean = true,
    val includeApiKeys: Boolean = true,
    val includeChannels: Boolean = true,
    val includeAgents: Boolean = true,
    val includeConfigOverride: Boolean = true,
    val includeActivity: Boolean = false,
    val includeLogs: Boolean = false,
)

data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val sections: List<String> = emptyList(),
)

object BackupManager {
    private const val TAG = "BackupManager"

    suspend fun createBackup(
        context: Context,
        settingsRepo: SettingsRepository,
        apiKeyRepo: ApiKeyRepository,
        channelRepo: ChannelConfigRepository,
        agentRepo: AgentRepository,
        activityRepo: ActivityRepository,
        logRepo: LogRepository,
        options: BackupOptions,
    ): File = withContext(Dispatchers.IO) {
        val datePart = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val backupFile = File(context.filesDir, "zeroclaw-android-$datePart.backup")
        val sections = mutableListOf<String>()

        ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
            if (options.includeSettings) {
                val settings = settingsRepo.settings.first()
                val json = serializeSettings(settings)
                addEntry(zos, "settings.json", json.toString(2))
                sections.add("settings")
            }

            if (options.includeApiKeys) {
                val keys = apiKeyRepo.keys.first()
                val json = serializeApiKeys(keys)
                addEntry(zos, "api_keys.json", json.toString(2))
                sections.add("api_keys")
            }

            if (options.includeChannels) {
                val channels = channelRepo.channels.first()
                val channelsWithSecrets = channels.map { ch ->
                    val secrets = channelRepo.getSecrets(ch.id)
                    ch to secrets
                }
                val json = serializeChannels(channelsWithSecrets)
                addEntry(zos, "channels.json", json.toString(2))
                sections.add("channels")
            }

            if (options.includeAgents) {
                val agents = agentRepo.agents.first()
                val json = serializeAgents(agents)
                addEntry(zos, "agents.json", json.toString(2))
                sections.add("agents")
            }

            if (options.includeConfigOverride) {
                val overrideFile = File(context.filesDir, "config_override.toml")
                if (overrideFile.exists()) {
                    addEntry(zos, "config_override.toml", overrideFile.readText())
                    sections.add("config_override")
                }
            }

            if (options.includeActivity) {
                val events = activityRepo.events.first()
                val json = serializeActivity(events)
                addEntry(zos, "activity.json", json.toString(2))
                sections.add("activity")
            }

            if (options.includeLogs) {
                val logs = logRepo.entries.first()
                val json = serializeLogs(logs)
                addEntry(zos, "logs.json", json.toString(2))
                sections.add("logs")
            }

            val manifest = BackupManifest(sections = sections)
            addEntry(zos, "manifest.json", serializeManifest(manifest).toString(2))
        }

        Log.i(TAG, "Backup created: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
        backupFile
    }

    suspend fun restoreBackup(
        context: Context,
        backupFile: File,
        settingsRepo: SettingsRepository,
        apiKeyRepo: ApiKeyRepository,
        channelRepo: ChannelConfigRepository,
        agentRepo: AgentRepository,
        activityRepo: ActivityRepository,
        logRepo: LogRepository,
        sections: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val entries = readZipEntries(backupFile)

        if ("settings" in sections && "settings.json" in entries) {
            try {
                val json = JSONObject(entries["settings.json"]!!)
                restoreSettings(settingsRepo, json)
                results.add("Settings restored")
            } catch (e: Exception) {
                results.add("Settings failed: ${e.message}")
            }
        }

        if ("api_keys" in sections && "api_keys.json" in entries) {
            try {
                val json = JSONArray(entries["api_keys.json"]!!)
                restoreApiKeys(apiKeyRepo, json)
                results.add("API keys restored")
            } catch (e: Exception) {
                results.add("API keys failed: ${e.message}")
            }
        }

        if ("channels" in sections && "channels.json" in entries) {
            try {
                val json = JSONArray(entries["channels.json"]!!)
                restoreChannels(channelRepo, json)
                results.add("Channels restored")
            } catch (e: Exception) {
                results.add("Channels failed: ${e.message}")
            }
        }

        if ("agents" in sections && "agents.json" in entries) {
            try {
                val json = JSONArray(entries["agents.json"]!!)
                restoreAgents(agentRepo, json)
                results.add("Agents restored")
            } catch (e: Exception) {
                results.add("Agents failed: ${e.message}")
            }
        }

        if ("config_override" in sections && "config_override.toml" in entries) {
            try {
                val content = entries["config_override.toml"]!!
                File(context.filesDir, "config_override.toml").writeText(content)
                results.add("Config override restored")
            } catch (e: Exception) {
                results.add("Config override failed: ${e.message}")
            }
        }

        if ("activity" in sections && "activity.json" in entries) {
            try {
                val json = JSONArray(entries["activity.json"]!!)
                restoreActivity(activityRepo, json)
                results.add("Activity restored")
            } catch (e: Exception) {
                results.add("Activity failed: ${e.message}")
            }
        }

        if ("logs" in sections && "logs.json" in entries) {
            try {
                val json = JSONArray(entries["logs.json"]!!)
                restoreLogs(logRepo, json)
                results.add("Logs restored")
            } catch (e: Exception) {
                results.add("Logs failed: ${e.message}")
            }
        }

        results.joinToString("\n")
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = context.filesDir
        return dir.listFiles()
            ?.filter { it.name.endsWith(".backup") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun getBackupSections(backupFile: File): BackupManifest? {
        return try {
            val entries = readZipEntries(backupFile)
            val manifestJson = entries["manifest.json"] ?: return null
            parseManifest(JSONObject(manifestJson))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup manifest: ${e.message}")
            null
        }
    }

    private fun addEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun readZipEntries(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    map[entry.name] = text
                }
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun serializeManifest(manifest: BackupManifest): JSONObject =
        JSONObject().apply {
            put("version", manifest.version)
            put("created_at", manifest.createdAt)
            put("sections", JSONArray(manifest.sections))
        }

    private fun parseManifest(json: JSONObject): BackupManifest =
        BackupManifest(
            version = json.optInt("version", 1),
            createdAt = json.optLong("created_at", 0L),
            sections = json.optJSONArray("sections")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                .orEmpty(),
        )

    private fun serializeSettings(settings: AppSettings): JSONObject {
        return JSONObject().apply {
            put("host", settings.host)
            put("port", settings.port)
            put("auto_start_on_boot", settings.autoStartOnBoot)
            put("default_provider", settings.defaultProvider)
            put("default_model", settings.defaultModel)
            put("default_temperature", settings.defaultTemperature.toDouble())
            put("compact_context", settings.compactContext)
            put("cost_enabled", settings.costEnabled)
            put("daily_limit_usd", settings.dailyLimitUsd.toDouble())
            put("monthly_limit_usd", settings.monthlyLimitUsd.toDouble())
            put("cost_warn_at_percent", settings.costWarnAtPercent)
            put("provider_retries", settings.providerRetries)
            put("fallback_providers", settings.fallbackProviders)
            put("memory_backend", settings.memoryBackend)
            put("memory_auto_save", settings.memoryAutoSave)
            put("identity_json", settings.identityJson)
            put("autonomy_level", settings.autonomyLevel)
            put("workspace_only", settings.workspaceOnly)
            put("allowed_commands", settings.allowedCommands)
            put("forbidden_paths", settings.forbiddenPaths)
            put("max_actions_per_hour", settings.maxActionsPerHour)
            put("max_cost_per_day_cents", settings.maxCostPerDayCents)
            put("require_approval_medium_risk", settings.requireApprovalMediumRisk)
            put("block_high_risk_commands", settings.blockHighRiskCommands)
            put("theme", settings.theme.name)
        }
    }

    @Suppress("LongMethod")
    private suspend fun restoreSettings(
        repo: SettingsRepository,
        json: JSONObject,
    ) {
        if (json.has("host")) repo.setHost(json.getString("host"))
        if (json.has("port")) repo.setPort(json.getInt("port"))
        if (json.has("auto_start_on_boot")) repo.setAutoStartOnBoot(json.getBoolean("auto_start_on_boot"))
        if (json.has("default_provider")) repo.setDefaultProvider(json.getString("default_provider"))
        if (json.has("default_model")) repo.setDefaultModel(json.getString("default_model"))
        if (json.has("default_temperature")) repo.setDefaultTemperature(json.getDouble("default_temperature").toFloat())
        if (json.has("compact_context")) repo.setCompactContext(json.getBoolean("compact_context"))
        if (json.has("cost_enabled")) repo.setCostEnabled(json.getBoolean("cost_enabled"))
        if (json.has("daily_limit_usd")) repo.setDailyLimitUsd(json.getDouble("daily_limit_usd").toFloat())
        if (json.has("monthly_limit_usd")) repo.setMonthlyLimitUsd(json.getDouble("monthly_limit_usd").toFloat())
        if (json.has("cost_warn_at_percent")) repo.setCostWarnAtPercent(json.getInt("cost_warn_at_percent"))
        if (json.has("provider_retries")) repo.setProviderRetries(json.getInt("provider_retries"))
        if (json.has("fallback_providers")) repo.setFallbackProviders(json.getString("fallback_providers"))
        if (json.has("memory_backend")) repo.setMemoryBackend(json.getString("memory_backend"))
        if (json.has("memory_auto_save")) repo.setMemoryAutoSave(json.getBoolean("memory_auto_save"))
        if (json.has("identity_json")) repo.setIdentityJson(json.getString("identity_json"))
        if (json.has("autonomy_level")) repo.setAutonomyLevel(json.getString("autonomy_level"))
        if (json.has("workspace_only")) repo.setWorkspaceOnly(json.getBoolean("workspace_only"))
        if (json.has("allowed_commands")) repo.setAllowedCommands(json.getString("allowed_commands"))
        if (json.has("forbidden_paths")) repo.setForbiddenPaths(json.getString("forbidden_paths"))
        if (json.has("max_actions_per_hour")) repo.setMaxActionsPerHour(json.getInt("max_actions_per_hour"))
        if (json.has("max_cost_per_day_cents")) repo.setMaxCostPerDayCents(json.getInt("max_cost_per_day_cents"))
        if (json.has("require_approval_medium_risk")) repo.setRequireApprovalMediumRisk(json.getBoolean("require_approval_medium_risk"))
        if (json.has("block_high_risk_commands")) repo.setBlockHighRiskCommands(json.getBoolean("block_high_risk_commands"))
        if (json.has("theme")) {
            try {
                repo.setTheme(com.zeroclaw.android.model.ThemeMode.valueOf(json.getString("theme")))
            } catch (_: Exception) { }
        }
    }

    private fun serializeApiKeys(keys: List<ApiKey>): JSONArray {
        return JSONArray(keys.map { key ->
            JSONObject().apply {
                put("id", key.id)
                put("provider", key.provider)
                put("key", key.key)
                put("base_url", key.baseUrl)
                put("created_at", key.createdAt)
                put("status", key.status.name)
                put("refresh_token", key.refreshToken)
                put("expires_at", key.expiresAt)
            }
        })
    }

    private suspend fun restoreApiKeys(
        repo: ApiKeyRepository,
        json: JSONArray,
    ) {
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val key = ApiKey(
                id = obj.getString("id"),
                provider = obj.getString("provider"),
                key = obj.getString("key"),
                baseUrl = obj.optString("base_url", ""),
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                status = try {
                    KeyStatus.valueOf(obj.optString("status", "ACTIVE"))
                } catch (_: Exception) { KeyStatus.ACTIVE },
                refreshToken = obj.optString("refresh_token", ""),
                expiresAt = obj.optLong("expires_at", 0L),
            )
            repo.save(key)
        }
    }

    private fun serializeChannels(
        channels: List<Pair<ConnectedChannel, Map<String, String>>>,
    ): JSONArray {
        return JSONArray(channels.map { (ch, secrets) ->
            JSONObject().apply {
                put("id", ch.id)
                put("type", ch.type.name)
                put("is_enabled", ch.isEnabled)
                put("config_values", JSONObject(ch.configValues))
                put("created_at", ch.createdAt)
                put("secrets", JSONObject(secrets))
            }
        })
    }

    private suspend fun restoreChannels(
        repo: ChannelConfigRepository,
        json: JSONArray,
    ) {
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val configValues = mutableMapOf<String, String>()
            obj.optJSONObject("config_values")?.keys()?.forEach { k ->
                configValues[k] = obj.getJSONObject("config_values").getString(k)
            }
            val secrets = mutableMapOf<String, String>()
            obj.optJSONObject("secrets")?.keys()?.forEach { k ->
                secrets[k] = obj.getJSONObject("secrets").getString(k)
            }
            val typeStr = obj.getString("type")
            val channelType = try {
                ChannelType.valueOf(typeStr)
            } catch (_: Exception) { null } ?: continue

            val channel = ConnectedChannel(
                id = obj.getString("id"),
                type = channelType,
                isEnabled = obj.optBoolean("is_enabled", true),
                configValues = configValues,
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
            )
            repo.save(channel, secrets)
        }
    }

    private fun serializeAgents(agents: List<Agent>): JSONArray {
        return JSONArray(agents.map { agent ->
            JSONObject().apply {
                put("id", agent.id)
                put("name", agent.name)
                put("provider", agent.provider)
                put("model_name", agent.modelName)
                put("is_enabled", agent.isEnabled)
                put("system_prompt", agent.systemPrompt)
                put("temperature", agent.temperature ?: JSONObject.NULL)
                put("max_depth", agent.maxDepth)
                put("channels", JSONArray(agent.channels.map { ch ->
                    JSONObject().apply {
                        put("type", ch.type)
                        put("endpoint", ch.endpoint)
                    }
                }))
            }
        })
    }

    private suspend fun restoreAgents(
        repo: AgentRepository,
        json: JSONArray,
    ) {
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val channelsArr = obj.optJSONArray("channels")
            val channels = if (channelsArr != null) {
                (0 until channelsArr.length()).map { idx ->
                    val chObj = channelsArr.getJSONObject(idx)
                    Agent.ChannelConfig(
                        type = chObj.getString("type"),
                        endpoint = chObj.getString("endpoint"),
                    )
                }
            } else emptyList()

            val temp = if (obj.isNull("temperature")) null else obj.optDouble("temperature", -1.0).let {
                if (it < 0) null else it.toFloat()
            }

            val agent = Agent(
                id = obj.getString("id"),
                name = obj.getString("name"),
                provider = obj.getString("provider"),
                modelName = obj.getString("model_name"),
                isEnabled = obj.optBoolean("is_enabled", true),
                systemPrompt = obj.optString("system_prompt", ""),
                channels = channels,
                temperature = temp,
                maxDepth = obj.optInt("max_depth", 3),
            )
            repo.save(agent)
        }
    }

    private fun serializeActivity(events: List<ActivityEvent>): JSONArray {
        return JSONArray(events.map { ev ->
            JSONObject().apply {
                put("id", ev.id)
                put("timestamp", ev.timestamp)
                put("type", ev.type.name)
                put("message", ev.message)
            }
        })
    }

    private suspend fun restoreActivity(
        repo: ActivityRepository,
        json: JSONArray,
    ) {
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val type = try {
                ActivityType.valueOf(obj.getString("type"))
            } catch (_: Exception) { continue }
            repo.record(type, obj.getString("message"))
        }
    }

    private fun serializeLogs(entries: List<LogEntry>): JSONArray {
        return JSONArray(entries.map { entry ->
            JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("severity", entry.severity.name)
                put("tag", entry.tag)
                put("message", entry.message)
            }
        })
    }

    private suspend fun restoreLogs(
        repo: LogRepository,
        json: JSONArray,
    ) {
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val severity = try {
                LogSeverity.valueOf(obj.getString("severity"))
            } catch (_: Exception) { continue }
            repo.append(severity, obj.getString("tag"), obj.getString("message"))
        }
    }
}
