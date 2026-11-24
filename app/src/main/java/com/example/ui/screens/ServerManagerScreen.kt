package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ServerConnectionMode
import com.example.data.model.ServerProfile
import com.example.ui.components.MindGptFooter
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PixelDisplayFont
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.ServerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerManagerScreen(
    viewModel: ServerViewModel,
) {
    val context = LocalContext.current
    val serversState by viewModel.serversState.collectAsStateWithLifecycle()
    val selectedServerId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()

    var showServerEditor by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerProfile?>(null) }
    var rconCommandInput by remember { mutableStateOf("") }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showPluginPermissionDialog by remember { mutableStateOf(false) }
    var pendingUploadDir by remember { mutableStateOf("/plugins") }

    val servers = (serversState as? DataUiState.Success)?.data.orEmpty()
    val activeServer = servers.firstOrNull { it.id == selectedServerId } ?: servers.firstOrNull()

    // Zero-permission SAF document picker for plugins (.jar), worlds (.zip/.mcworld), schematics (.schem)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && activeServer != null) {
            viewModel.uploadFile(context, activeServer, pendingUploadDir, uri)
        }
    }

    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            Toast.makeText(context, statusBanner, Toast.LENGTH_SHORT).show()
            viewModel.clearBanners()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Server Manager",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Secure RCON, Server Ping, HTTPS Panel, Backups & Uploads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingServer = null
                                showServerEditor = true
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("add_server_button")
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Server")
                        }
                    }

                    if (isBusy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (!errorBanner.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBanners() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when (serversState) {
                is DataUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DataUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = (serversState as DataUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is DataUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (servers.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "Connect Your Minecraft Server",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "Add a Minecraft server using Status Ping (25565), RCON TCP (25575), or an HTTPS Panel API (Pterodactyl). Passwords & API keys stay encrypted locally on your device.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                editingServer = null
                                                showServerEditor = true
                                            }
                                        ) {
                                            Text("Connect Server Securely")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Server Selector List
                            items(servers, key = { it.id }) { srv ->
                                val isSelected = activeServer?.id == srv.id
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectServer(srv.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Dns,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = srv.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "${srv.host}:${srv.gamePort} • ${ServerConnectionMode.fromRaw(srv.connectionMode).label}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Status: ${srv.lastStatus}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = {
                                            editingServer = srv
                                            showServerEditor = true
                                        }) {
                                            Icon(Icons.Rounded.Edit, "Edit server")
                                        }
                                        IconButton(onClick = { viewModel.deleteServer(srv.id) }) {
                                            Icon(Icons.Rounded.Delete, "Delete server", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }

                            // Active Server Control Panel
                            if (activeServer != null) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Controls: ${activeServer.name}",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Rounded.Security, null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("LOCAL SECRET", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { viewModel.testServerConnection(activeServer) },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.NetworkPing, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Ping Server")
                                                }

                                                OutlinedButton(
                                                    onClick = { viewModel.refreshPanelStatus(activeServer) },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Panel Telemetry")
                                                }

                                                OutlinedButton(
                                                    onClick = { viewModel.backupWorld(activeServer) },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.Backup, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Backup Worlds")
                                                }

                                                Button(
                                                    onClick = { showRestartConfirm = true },
                                                    enabled = !isBusy,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Restart Server")
                                                }
                                            }

                                            // Uploads Section (Plugins with permission, Worlds, Schematics)
                                            Text(
                                                text = "Server Uploads (via HTTPS Panel API)",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { showPluginPermissionDialog = true },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(17.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Upload Plugin (.jar)")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        pendingUploadDir = "/world"
                                                        filePickerLauncher.launch(arrayOf("*/*"))
                                                    },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.Public, null, modifier = Modifier.size(17.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Upload World")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        pendingUploadDir = "/plugins/WorldEdit/schematics"
                                                        filePickerLauncher.launch(arrayOf("*/*"))
                                                    },
                                                    enabled = !isBusy
                                                ) {
                                                    Icon(Icons.Rounded.Layers, null, modifier = Modifier.size(17.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Upload Schematic")
                                                }
                                            }

                                            // RCON Console Input
                                            Text(
                                                text = "RCON Command Console",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = rconCommandInput,
                                                    onValueChange = { rconCommandInput = it },
                                                    placeholder = { Text("e.g., list, tps, say Hello!") },
                                                    leadingIcon = { Icon(Icons.Rounded.Terminal, null) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = {
                                                        val cmd = rconCommandInput
                                                        rconCommandInput = ""
                                                        viewModel.runRconCommand(activeServer, cmd)
                                                    },
                                                    enabled = rconCommandInput.isNotBlank() && !isBusy,
                                                    modifier = Modifier.height(52.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.Send, "Run RCON")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // View Logs Terminal Card
                            item {
                                Surface(
                                    color = Color(0xFF0A120E),
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, Color(0xFF2E4A3B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "SERVER LOGS & OUTPUT",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelDisplayFont),
                                                color = Color(0xFF34D399)
                                            )
                                            TextButton(onClick = { viewModel.clearLogs() }) {
                                                Text("Clear", color = Color(0xFF9CAFA5))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (logs.isEmpty()) {
                                            Text(
                                                text = "No logs yet. Tap 'Ping Server', 'Panel Telemetry', or run an RCON command to view live output.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                                                color = Color(0xFF9CAFA5)
                                            )
                                        } else {
                                            logs.forEach { line ->
                                                Text(
                                                    text = line,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = JetBrainsMonoFont,
                                                        fontSize = 12.sp,
                                                        lineHeight = 18.sp
                                                    ),
                                                    color = Color(0xFFECFDF5)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MindGptFooter()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showServerEditor) {
        ServerEditorDialog(
            existing = editingServer,
            initialSecret = editingServer?.let { viewModel.getLocalSecret(it.id) }.orEmpty(),
            onSave = { name, host, gamePort, rconPort, panelUrl, mode, secret ->
                showServerEditor = false
                viewModel.saveServerProfile(
                    existingId = editingServer?.id,
                    name = name,
                    host = host,
                    gamePort = gamePort,
                    rconPort = rconPort,
                    panelUrl = panelUrl,
                    mode = mode,
                    localSecret = secret
                )
            },
            onDismiss = { showServerEditor = false }
        )
    }

    if (showRestartConfirm && activeServer != null) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Restart ${activeServer.name}?") },
            text = {
                Text("This will send a live restart command to your Minecraft server. Connected players may be disconnected momentarily.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirm = false
                        viewModel.restartServer(activeServer)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showPluginPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPluginPermissionDialog = false },
            icon = { Icon(Icons.Rounded.CloudUpload, null) },
            title = { Text("Permission to Upload Plugin") },
            text = {
                Text("Plugins (.jar) execute server-side code on your Minecraft server. Confirm that you trust this plugin file and authorize uploading it to /plugins.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPluginPermissionDialog = false
                        pendingUploadDir = "/plugins"
                        filePickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                    }
                ) {
                    Text("Allow & Select File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPluginPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerEditorDialog(
    existing: ServerProfile?,
    initialSecret: String,
    onSave: (
        name: String,
        host: String,
        gamePort: Long,
        rconPort: Long,
        panelUrl: String,
        mode: ServerConnectionMode,
        secret: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var gamePortText by remember { mutableStateOf((existing?.gamePort ?: 25565L).toString()) }
    var rconPortText by remember { mutableStateOf((existing?.rconPort ?: 25575L).toString()) }
    var panelUrl by remember { mutableStateOf(existing?.panelUrl.orEmpty()) }
    var mode by remember {
        mutableStateOf(ServerConnectionMode.fromRaw(existing?.connectionMode ?: "PING_ONLY"))
    }
    var secret by remember { mutableStateOf(initialSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Connect Minecraft Server" else "Edit Server") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g., Loyal SMP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP") },
                    placeholder = { Text("play.example.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gamePortText,
                        onValueChange = { gamePortText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Game Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rconPortText,
                        onValueChange = { rconPortText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("RCON Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Connection Mode", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ServerConnectionMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) }
                        )
                    }
                }

                if (mode == ServerConnectionMode.PANEL_HTTPS) {
                    OutlinedTextField(
                        value = panelUrl,
                        onValueChange = { panelUrl = it },
                        label = { Text("HTTPS Panel Server URL") },
                        placeholder = { Text("https://panel.host.com/api/client/servers/ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mode != ServerConnectionMode.PING_ONLY) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = {
                            Text(if (mode == ServerConnectionMode.PANEL_HTTPS) "Panel API Token (Local Only)" else "RCON Password (Local Only)")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gPort = gamePortText.toLongOrNull() ?: 25565L
                    val rPort = rconPortText.toLongOrNull() ?: 25575L
                    onSave(name, host, gPort, rPort, panelUrl, mode, secret)
                },
                enabled = name.isNotBlank() && host.isNotBlank()
            ) {
                Text("Save Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
