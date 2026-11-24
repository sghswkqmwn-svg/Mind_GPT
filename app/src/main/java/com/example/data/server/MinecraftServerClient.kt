package com.example.data.server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

data class ServerPingResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val summary: String,
    val details: String,
)

class MinecraftServerClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Performs a real TCP connection + Minecraft Server List Ping (SLP) handshake against host:port.
     */
    suspend fun pingMinecraftServer(host: String, port: Int): Result<ServerPingResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanHost = host.trim()
                require(cleanHost.isNotBlank()) { "Server host cannot be empty." }
                require(port in 1..65535) { "Port must be between 1 and 65535." }

                var slpSummary = "TCP Port Open"
                var slpDetails = "Connected to $cleanHost:$port"
                val elapsed = measureTimeMillis {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(cleanHost, port), 4500)
                        socket.soTimeout = 4500

                        // Attempt Minecraft Java SLP Status Handshake (0x00)
                        try {
                            val out = DataOutputStream(socket.getOutputStream())
                            val input = DataInputStream(socket.getInputStream())

                            val handshakePayload = ByteArrayOutputStream().apply {
                                writeVarInt(this, 0x00) // Packet ID
                                writeVarInt(this, 767)  // Protocol version (1.21+)
                                writeVarInt(this, cleanHost.toByteArray(Charsets.UTF_8).size)
                                write(cleanHost.toByteArray(Charsets.UTF_8))
                                write((port shr 8) and 0xFF)
                                write(port and 0xFF)
                                writeVarInt(this, 1)    // Next state: Status
                            }.toByteArray()

                            val handshakeFrame = ByteArrayOutputStream().apply {
                                writeVarInt(this, handshakePayload.size)
                                write(handshakePayload)
                            }.toByteArray()
                            out.write(handshakeFrame)

                            // Status Request (0x00)
                            out.write(byteArrayOf(0x01, 0x00))
                            out.flush()

                            val packetLen = readVarInt(input)
                            if (packetLen in 1..65536) {
                                val packetId = readVarInt(input)
                                if (packetId == 0x00) {
                                    val jsonLen = readVarInt(input)
                                    if (jsonLen in 2..65536) {
                                        val bytes = ByteArray(jsonLen)
                                        input.readFully(bytes)
                                        val json = JSONObject(String(bytes, Charsets.UTF_8))
                                        val versionName = json.optJSONObject("version")?.optString("name").orEmpty()
                                        val playersObj = json.optJSONObject("players")
                                        val online = playersObj?.optInt("online", -1) ?: -1
                                        val max = playersObj?.optInt("max", -1) ?: -1
                                        val desc = parseMotd(json.opt("description"))
                                        slpSummary = if (online >= 0 && max >= 0) {
                                            "Online ($online/$max players)"
                                        } else {
                                            "Online"
                                        }
                                        slpDetails = buildString {
                                            if (versionName.isNotBlank()) append("Version: $versionName")
                                            if (desc.isNotBlank()) {
                                                if (isNotEmpty()) append(" • ")
                                                append(desc.take(120))
                                            }
                                        }.ifBlank { "Minecraft server responded cleanly." }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // TCP port accepted connection, SLP parsing skipped or Bedrock/non-Java port
                        }
                    }
                }
                ServerPingResult(
                    reachable = true,
                    latencyMs = elapsed,
                    summary = "$slpSummary (${elapsed}ms)",
                    details = slpDetails
                )
            }
        }

    /**
     * Real Minecraft RCON TCP client (Source RCON packet framing).
     */
    suspend fun executeRconCommand(
        host: String,
        rconPort: Int,
        password: String,
        command: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHost = host.trim()
            require(cleanHost.isNotBlank()) { "Host is required." }
            require(rconPort in 1..65535) { "RCON port must be 1..65535." }
            require(password.isNotBlank()) { "RCON password is required (saved locally on device)." }
            val cleanCmd = command.trim().removePrefix("/")
            require(cleanCmd.isNotBlank()) { "Command cannot be empty." }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, rconPort), 5000)
                socket.soTimeout = 6000
                val out = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())

                // 1. Auth packet (type 3)
                sendRconPacket(out, requestId = 101, type = 3, payload = password)
                val authResp = readRconPacket(input)
                if (authResp.first == -1) {
                    error("RCON authentication failed: incorrect RCON password.")
                }

                // 2. Command packet (type 2)
                sendRconPacket(out, requestId = 102, type = 2, payload = cleanCmd)
                val cmdResp = readRconPacket(input)
                cmdResp.second.ifBlank { "(Command executed with empty output)" }
            }
        }
    }

    /**
     * Validates an HTTPS panel URL (Pterodactyl / compatible REST panel).
     */
    private fun validatePanelUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Panel URL must use https:// for secure encrypted connections."
        }
        return trimmed
    }

    suspend fun fetchPanelResourcesAndLogs(
        panelServerBaseUrl: String,
        apiToken: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validatePanelUrl(panelServerBaseUrl)
            require(apiToken.isNotBlank()) { "Panel API token is required." }

            val req = Request.Builder()
                .url("$baseUrl/resources")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Panel returned HTTP ${resp.code}: ${body.take(180)}")
                }
                val attrs = JSONObject(body).optJSONObject("attributes")
                val state = attrs?.optString("current_state", "unknown") ?: "unknown"
                val resources = attrs?.optJSONObject("resources")
                val cpu = resources?.optDouble("cpu_absolute", 0.0) ?: 0.0
                val memMb = (resources?.optLong("memory_bytes", 0L) ?: 0L) / (1024L * 1024L)
                val diskMb = (resources?.optLong("disk_bytes", 0L) ?: 0L) / (1024L * 1024L)
                val uptimeSec = (resources?.optLong("uptime", 0L) ?: 0L) / 1000L
                "State: ${state.uppercase()} | CPU: ${"%.1f".format(cpu)}% | RAM: ${memMb} MB | Disk: ${diskMb} MB | Uptime: ${uptimeSec}s"
            }
        }
    }

    suspend fun sendPanelPowerAction(
        panelServerBaseUrl: String,
        apiToken: String,
        signal: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validatePanelUrl(panelServerBaseUrl)
            require(apiToken.isNotBlank()) { "Panel API token is required." }
            require(signal in listOf("start", "restart", "stop", "kill")) { "Unsupported power signal" }

            val jsonBody = JSONObject().put("signal", signal).toString()
            val req = Request.Builder()
                .url("$baseUrl/power")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Accept", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    error("Power command failed (HTTP ${resp.code}): ${body.take(160)}")
                }
                "Server '$signal' signal sent successfully."
            }
        }
    }

    suspend fun createPanelBackup(
        panelServerBaseUrl: String,
        apiToken: String,
        backupName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validatePanelUrl(panelServerBaseUrl)
            require(apiToken.isNotBlank()) { "Panel API token is required." }

            val jsonBody = JSONObject().put("name", backupName.take(80)).toString()
            val req = Request.Builder()
                .url("$baseUrl/backups")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Accept", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Backup failed (HTTP ${resp.code}): ${body.take(160)}")
                }
                val uuid = JSONObject(body).optJSONObject("attributes")?.optString("uuid").orEmpty()
                if (uuid.isNotBlank()) "Backup created (ID: ${uuid.take(8)}...)" else "World backup started."
            }
        }
    }

    suspend fun uploadFileToPanel(
        context: Context,
        panelServerBaseUrl: String,
        apiToken: String,
        targetDirectory: String,
        fileUri: Uri,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = validatePanelUrl(panelServerBaseUrl)
            require(apiToken.isNotBlank()) { "Panel API token is required." }

            // Step 1: Request signed upload URL from panel
            val urlReq = Request.Builder()
                .url("$baseUrl/files/upload")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val signedUploadUrl = httpClient.newCall(urlReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Could not obtain upload URL (HTTP ${resp.code}): ${body.take(160)}")
                }
                JSONObject(body).optJSONObject("attributes")?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Panel response did not include an upload URL.")
            }

            // Step 2: Read file safely via ContentResolver (zero storage permission needed)
            val fileName = resolveFileName(context, fileUri)
            val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: error("Unable to read selected file.")
            require(bytes.size <= 50 * 1024 * 1024) { "File exceeds 50MB direct upload limit." }

            val uploadUrlWithDir = if (signedUploadUrl.contains("?")) {
                "$signedUploadUrl&directory=$targetDirectory"
            } else {
                "$signedUploadUrl?directory=$targetDirectory"
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "files",
                    fileName,
                    bytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val uploadReq = Request.Builder()
                .url(uploadUrlWithDir)
                .post(multipartBody)
                .build()

            httpClient.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    error("Upload failed (HTTP ${resp.code}): ${body.take(160)}")
                }
                "Uploaded '$fileName' (${bytes.size / 1024} KB) to $targetDirectory"
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "upload.bin" } ?: "upload.bin"
    }

    private fun sendRconPacket(out: DataOutputStream, requestId: Int, type: Int, payload: String) {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val bodyLen = 4 + 4 + payloadBytes.size + 2
        val buf = ByteBuffer.allocate(4 + bodyLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(bodyLen)
        buf.putInt(requestId)
        buf.putInt(type)
        buf.put(payloadBytes)
        buf.put(0)
        buf.put(0)
        out.write(buf.array())
        out.flush()
    }

    private fun readRconPacket(input: DataInputStream): Pair<Int, String> {
        val lenBytes = ByteArray(4)
        input.readFully(lenBytes)
        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        require(len in 10..65536) { "Invalid RCON packet length: $len" }
        val data = ByteArray(len)
        input.readFully(data)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val reqId = buf.int
        buf.int // type
        val payloadLen = (len - 10).coerceAtLeast(0)
        val payloadBytes = ByteArray(payloadLen)
        buf.get(payloadBytes)
        return reqId to String(payloadBytes, Charsets.UTF_8)
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.write(v)
                return
            }
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readVarInt(input: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            read = input.readUnsignedByte()
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) throw IllegalStateException("VarInt too big")
        } while ((read and 0x80) != 0)
        return result
    }

    private fun parseMotd(desc: Any?): String {
        return when (desc) {
            is String -> desc
            is JSONObject -> desc.optString("text").ifBlank {
                desc.optJSONArray("extra")?.let { arr ->
                    buildString {
                        for (i in 0 until arr.length()) {
                            append(arr.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }.orEmpty()
            }
            else -> ""
        }.replace(Regex("§."), "").trim()
    }
}
