package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.MinecraftProject
import com.example.data.model.ServerProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MindGptRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    companion object {
        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(dbId)
            try {
                instance.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings {})
                }
            } catch (_: IllegalStateException) {
                // Settings already applied on this instance before first use.
            }
            return instance
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(displayName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "Minecraft Builder" }.take(100)
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- CHATS ---

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        if (uid != userId) return@flow
        val path = "users/$uid/chats"
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<ChatThread> { it.pinned }
                            .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        if (uid != userId || chatId.isBlank()) return@flow
        val path = "users/$uid/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChat(
        title: String,
        category: String = "general",
        preview: String = "",
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("chats").document()
            docPath = docRef.path
            val safeTitle = title.trim().ifBlank { "New Chat" }.take(120)
            val safeCategory = category.trim().ifBlank { "general" }.take(40)
            val safePreview = preview.trim().take(300)
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "lastMessagePreview" to safePreview,
                "category" to safeCategory,
                "pinned" to false,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameChat(chatId: String, newTitle: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID required" }
            val safeTitle = newTitle.trim().ifBlank { "Untitled Chat" }.take(120)
            val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePinChat(chatId: String, pinned: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID required" }
            val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateChatSummary(chatId: String, preview: String, messageCount: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "lastMessagePreview" to preview.trim().take(300),
                    "messageCount" to messageCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID required" }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = chatRef.path
            val messagesSnap = chatRef.collection("messages").get().await()
            val docs = messagesSnap.documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        chatId: String,
        role: String,
        content: String,
        toolTag: String? = null,
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID required" }
            val safeRole = if (role == "model") "model" else "user"
            val docRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "chatId" to chatId,
                "role" to safeRole,
                "content" to content.take(50000),
                "feedback" to "none",
                "toolTag" to toolTag?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMessageContent(chatId: String, messageId: String, content: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "content" to content.take(50000),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMessageFeedback(chatId: String, messageId: String, feedback: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val safeFeedback = if (feedback in listOf("none", "like", "dislike")) feedback else "none"
            val docRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "feedback" to safeFeedback,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- CLOUD MINECRAFT PROJECTS ---

    fun observeProjects(userId: String): Flow<List<MinecraftProject>> = flow {
        val uid = requireUserId()
        if (uid != userId) return@flow
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MinecraftProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<MinecraftProject> { it.pinned }
                            .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProject(
        title: String,
        toolType: String,
        minecraftEdition: String,
        minecraftVersion: String,
        promptSummary: String,
        content: String,
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/projects"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("projects").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Untitled Project" }.take(120),
                "toolType" to toolType.trim().ifBlank { "general" }.take(60),
                "minecraftEdition" to minecraftEdition.trim().ifBlank { "Java" }.take(30),
                "minecraftVersion" to minecraftVersion.trim().ifBlank { "1.21.4" }.take(30),
                "promptSummary" to promptSummary.trim().take(500),
                "content" to content.trim().ifBlank { "Empty" }.take(50000),
                "pinned" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePinProject(projectId: String, pinned: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- MINECRAFT SERVER PROFILES ---

    fun observeServers(userId: String): Flow<List<ServerProfile>> = flow {
        val uid = requireUserId()
        if (uid != userId) return@flow
        val path = "users/$uid/servers"
        emitAll(
            db.collection("users").document(uid).collection("servers")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ServerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveServer(
        existingId: String?,
        name: String,
        host: String,
        gamePort: Long,
        rconPort: Long,
        panelUrl: String,
        connectionMode: String,
        lastStatus: String,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeMode = if (connectionMode in listOf("PANEL_HTTPS", "RCON_TCP", "PING_ONLY")) {
            connectionMode
        } else {
            "PING_ONLY"
        }
        val safeGamePort = gamePort.coerceIn(1L, 65535L)
        val safeRconPort = rconPort.coerceIn(1L, 65535L)
        val safeName = name.trim().ifBlank { "My Minecraft Server" }.take(80)
        val safeHost = host.trim().take(255)
        val safePanelUrl = panelUrl.trim().take(300)
        val safeStatus = lastStatus.trim().take(60)

        return try {
            if (existingId.isNullOrBlank()) {
                val docRef = db.collection("users").document(uid).collection("servers").document()
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to safeName,
                        "host" to safeHost,
                        "gamePort" to safeGamePort,
                        "rconPort" to safeRconPort,
                        "panelUrl" to safePanelUrl,
                        "connectionMode" to safeMode,
                        "lastStatus" to safeStatus,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val docRef = db.collection("users").document(uid).collection("servers").document(existingId)
                docRef.update(
                    mapOf(
                        "name" to safeName,
                        "host" to safeHost,
                        "gamePort" to safeGamePort,
                        "rconPort" to safeRconPort,
                        "panelUrl" to safePanelUrl,
                        "connectionMode" to safeMode,
                        "lastStatus" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/servers")
            Result.failure(e)
        }
    }

    suspend fun updateServerStatus(serverId: String, status: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/servers/$serverId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("servers").document(serverId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "lastStatus" to status.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteServer(serverId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/servers/$serverId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("servers").document(serverId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
