package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ServerSecretsStore
import com.example.data.firebase.MindGptRepository
import com.example.data.model.ServerConnectionMode
import com.example.data.model.ServerProfile
import com.example.data.server.MinecraftServerClient
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServerViewModel(
    private val repository: MindGptRepository,
    private val serverClient: MinecraftServerClient,
    private val secretsStore: ServerSecretsStore,
    private val currentUserId: String,
) : ViewModel() {

    private companion object {
        const val TAG = "ServerViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }

    val serversState: StateFlow<DataUiState<List<ServerProfile>>> =
        repository.observeServers(currentUserId)
            .map<List<ServerProfile>, DataUiState<List<ServerProfile>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Failed to observe servers", root)
                emit(DataUiState.Error(root.localizedMessage ?: "Failed to load servers."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataUiState.Loading
            )

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    fun selectServer(serverId: String?) {
        _selectedServerId.value = serverId
    }

    fun getLocalSecret(serverId: String): String = secretsStore.getSecret(serverId)

    fun saveServerProfile(
        existingId: String?,
        name: String,
        host: String,
        gamePort: Long,
        rconPort: Long,
        panelUrl: String,
        mode: ServerConnectionMode,
        localSecret: String,
    ) {
        if (name.isBlank() || host.isBlank()) {
            _errorBanner.value = "Server name and host are required."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveServer(
                existingId = existingId,
                name = name,
                host = host,
                gamePort = gamePort,
                rconPort = rconPort,
                panelUrl = panelUrl,
                connectionMode = mode.key,
                lastStatus = "Configured"
            ).onSuccess { savedId ->
                if (localSecret.isNotBlank()) {
                    secretsStore.saveSecret(savedId, localSecret.trim())
                }
                _selectedServerId.value = savedId
                appendLog("Saved server profile '$name' ($host:$gamePort). Credentials stored locally on device.")
                _statusBanner.value = "Server saved securely"
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to save server profile."
            }
            _isBusy.value = false
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            secretsStore.deleteSecret(serverId)
            repository.deleteServer(serverId)
                .onSuccess {
                    if (_selectedServerId.value == serverId) _selectedServerId.value = null
                    _statusBanner.value = "Server removed"
                }
                .onFailure { _errorBanner.value = it.localizedMessage ?: "Failed to remove server." }
        }
    }

    fun testServerConnection(server: ServerProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null
            appendLog("Testing connection to ${server.host}:${server.gamePort}...")
            serverClient.pingMinecraftServer(server.host, server.gamePort.toInt())
                .onSuccess { ping ->
                    appendLog("Ping OK: ${ping.summary} — ${ping.details}")
                    repository.updateServerStatus(server.id, ping.summary)
                    _statusBanner.value = ping.summary
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Connection unreachable"
                    appendLog("Ping failed: $msg")
                    repository.updateServerStatus(server.id, "Unreachable")
                    _errorBanner.value = msg
                }

            val mode = ServerConnectionMode.fromRaw(server.connectionMode)
            val secret = secretsStore.getSecret(server.id)
            if (mode == ServerConnectionMode.PANEL_HTTPS && server.panelUrl.isNotBlank() && secret.isNotBlank()) {
                serverClient.fetchPanelResourcesAndLogs(server.panelUrl, secret)
                    .onSuccess { metrics ->
                        appendLog("Panel Telemetry: $metrics")
                    }
                    .onFailure { err ->
                        appendLog("Panel check failed: ${err.localizedMessage}")
                    }
            }
            _isBusy.value = false
        }
    }

    fun runRconCommand(server: ServerProfile, command: String) {
        val secret = secretsStore.getSecret(server.id)
        if (secret.isBlank()) {
            _errorBanner.value = "No RCON password saved on this device. Edit the server profile to add it."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            appendLog("> /$command")
            serverClient.executeRconCommand(server.host, server.rconPort.toInt(), secret, command)
                .onSuccess { output ->
                    appendLog(output)
                    _statusBanner.value = "Command executed"
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "RCON failed"
                    appendLog("RCON Error: $msg")
                    _errorBanner.value = msg
                }
            _isBusy.value = false
        }
    }

    fun refreshPanelStatus(server: ServerProfile) {
        val secret = secretsStore.getSecret(server.id)
        if (server.panelUrl.isBlank() || secret.isBlank()) {
            _errorBanner.value = "HTTPS Panel URL and local API token are required to query panel logs/resources."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            serverClient.fetchPanelResourcesAndLogs(server.panelUrl, secret)
                .onSuccess { info ->
                    appendLog("Panel Status: $info")
                    repository.updateServerStatus(server.id, info.take(60))
                    _statusBanner.value = "Telemetry updated"
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Panel request failed"
                    appendLog("Panel Error: $msg")
                    _errorBanner.value = msg
                }
            _isBusy.value = false
        }
    }

    fun restartServer(server: ServerProfile) {
        val mode = ServerConnectionMode.fromRaw(server.connectionMode)
        val secret = secretsStore.getSecret(server.id)
        viewModelScope.launch {
            _isBusy.value = true
            when (mode) {
                ServerConnectionMode.PANEL_HTTPS -> {
                    serverClient.sendPanelPowerAction(server.panelUrl, secret, "restart")
                        .onSuccess { msg ->
                            appendLog(msg)
                            _statusBanner.value = msg
                        }
                        .onFailure { err ->
                            appendLog("Restart failed: ${err.localizedMessage}")
                            _errorBanner.value = err.localizedMessage ?: "Restart failed."
                        }
                }
                ServerConnectionMode.RCON_TCP -> {
                    serverClient.executeRconCommand(server.host, server.rconPort.toInt(), secret, "restart")
                        .onSuccess { out ->
                            appendLog("RCON restart: $out")
                            _statusBanner.value = "Restart command sent via RCON"
                        }
                        .onFailure { err ->
                            appendLog("RCON restart failed: ${err.localizedMessage}")
                            _errorBanner.value = err.localizedMessage ?: "RCON restart failed."
                        }
                }
                ServerConnectionMode.PING_ONLY -> {
                    _errorBanner.value = "Restart requires HTTPS Panel API or RCON TCP mode."
                }
            }
            _isBusy.value = false
        }
    }

    fun backupWorld(server: ServerProfile) {
        val mode = ServerConnectionMode.fromRaw(server.connectionMode)
        val secret = secretsStore.getSecret(server.id)
        viewModelScope.launch {
            _isBusy.value = true
            when (mode) {
                ServerConnectionMode.PANEL_HTTPS -> {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    serverClient.createPanelBackup(server.panelUrl, secret, "MindGPT-$stamp")
                        .onSuccess { msg ->
                            appendLog(msg)
                            _statusBanner.value = msg
                        }
                        .onFailure { err ->
                            appendLog("Backup failed: ${err.localizedMessage}")
                            _errorBanner.value = err.localizedMessage ?: "Backup failed."
                        }
                }
                ServerConnectionMode.RCON_TCP -> {
                    serverClient.executeRconCommand(server.host, server.rconPort.toInt(), secret, "save-all flush")
                        .onSuccess { out ->
                            appendLog("World save triggered via RCON: $out")
                            _statusBanner.value = "World save-all flush executed"
                        }
                        .onFailure { err ->
                            appendLog("World save failed: ${err.localizedMessage}")
                            _errorBanner.value = err.localizedMessage ?: "World save failed."
                        }
                }
                ServerConnectionMode.PING_ONLY -> {
                    _errorBanner.value = "World backup requires HTTPS Panel API or RCON TCP mode."
                }
            }
            _isBusy.value = false
        }
    }

    fun uploadFile(context: Context, server: ServerProfile, targetDirectory: String, uri: Uri) {
        val secret = secretsStore.getSecret(server.id)
        if (server.panelUrl.isBlank() || secret.isBlank()) {
            _errorBanner.value = "File uploads require an HTTPS Panel API URL and API token."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            appendLog("Uploading file to $targetDirectory...")
            serverClient.uploadFileToPanel(context, server.panelUrl, secret, targetDirectory, uri)
                .onSuccess { msg ->
                    appendLog(msg)
                    _statusBanner.value = msg
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Upload failed"
                    appendLog("Upload error: $msg")
                    _errorBanner.value = msg
                }
            _isBusy.value = false
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun clearBanners() {
        _statusBanner.value = null
        _errorBanner.value = null
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.value = (_logs.value + "[$time] $line").takeLast(120)
    }
}
