package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.MinecraftAiService
import com.example.data.firebase.MindGptRepository
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

class ChatViewModel(
    private val repository: MindGptRepository,
    private val aiService: MinecraftAiService,
    private val currentUserId: String,
    userDisplayName: String,
) : ViewModel() {

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(userDisplayName)
        }
    }

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _streamingChatId = MutableStateFlow<String?>(null)
    val streamingChatId: StateFlow<String?> = _streamingChatId.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    private var generationJob: Job? = null

    val chatsState: StateFlow<DataUiState<List<ChatThread>>> = repository.observeChats(currentUserId)
        .map<List<ChatThread>, DataUiState<List<ChatThread>>> { DataUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Failed to observe chats", root)
            emit(DataUiState.Error(root.localizedMessage ?: "Failed to load chat history."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<DataUiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(DataUiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, chatId)
                    .map<List<ChatMessage>, DataUiState<List<ChatMessage>>> { DataUiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Failed to observe messages", root)
                        emit(DataUiState.Error(root.localizedMessage ?: "Failed to load messages."))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Success(emptyList())
        )

    fun startNewChat() {
        stopGenerating()
        _selectedChatId.value = null
        _actionError.value = null
    }

    fun selectChat(chatId: String) {
        if (_selectedChatId.value != chatId) {
            stopGenerating()
            _selectedChatId.value = chatId
            _actionError.value = null
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _actionError.value = null
    }

    fun clearInfo() {
        _infoMessage.value = null
    }

    fun sendMessage(prompt: String, toolTag: String? = null) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _isGenerating.value) return
        _actionError.value = null

        val currentMessages = (messagesState.value as? DataUiState.Success)?.data.orEmpty()

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingText.value = ""
            var activeChatId = _selectedChatId.value

            try {
                if (activeChatId.isNullOrBlank()) {
                    val createRes = repository.createChat(
                        title = cleanPrompt.take(48),
                        category = toolTag ?: "general",
                        preview = cleanPrompt.take(120)
                    )
                    activeChatId = createRes.getOrElse { err ->
                        _actionError.value = err.localizedMessage ?: "Failed to create chat."
                        return@launch
                    }
                    _selectedChatId.value = activeChatId

                    // Generate a crisp AI title in parallel/afterward
                    launch {
                        val suggested = aiService.suggestChatTitle(cleanPrompt)
                        if (suggested.isNotBlank()) {
                            repository.renameChat(activeChatId, suggested)
                        }
                    }
                }

                _streamingChatId.value = activeChatId
                repository.addMessage(activeChatId, "user", cleanPrompt, toolTag)
                    .onFailure { err ->
                        _actionError.value = err.localizedMessage ?: "Failed to save prompt."
                        return@launch
                    }

                val fullReply = StringBuilder()
                aiService.streamChatReply(currentMessages, cleanPrompt).collect { partial ->
                    fullReply.clear()
                    fullReply.append(partial)
                    _streamingText.value = partial
                }

                val finalText = fullReply.toString().trim()
                if (finalText.isNotEmpty()) {
                    repository.addMessage(activeChatId, "model", finalText, toolTag)
                    repository.updateChatSummary(
                        chatId = activeChatId,
                        preview = finalText.take(180),
                        messageCount = (currentMessages.size + 2).toLong()
                    )
                }
            } catch (e: CancellationException) {
                val partial = _streamingText.value.trim()
                if (partial.isNotEmpty() && !activeChatId.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        repository.addMessage(activeChatId, "model", "$partial\n\n*(Stopped)*", toolTag)
                        repository.updateChatSummary(
                            chatId = activeChatId,
                            preview = partial.take(180),
                            messageCount = (currentMessages.size + 2).toLong()
                        )
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                _actionError.value = e.localizedMessage ?: "MindGPT could not generate a reply."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                _streamingChatId.value = null
            }
        }
    }

    fun stopGenerating() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
    }

    fun continueResponse(assistantMessage: ChatMessage) {
        val chatId = _selectedChatId.value ?: return
        if (_isGenerating.value) return
        val currentMessages = (messagesState.value as? DataUiState.Success)?.data.orEmpty()

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingChatId.value = chatId
            _streamingText.value = ""
            try {
                val prefix = assistantMessage.content.trimEnd()
                val continuation = StringBuilder()
                aiService.streamContinueReply(currentMessages, prefix).collect { partial ->
                    continuation.clear()
                    continuation.append(partial)
                    _streamingText.value = "$prefix\n$partial"
                }
                val merged = "$prefix\n${continuation.toString().trim()}"
                repository.updateMessageContent(chatId, assistantMessage.id, merged)
                repository.updateChatSummary(chatId, merged.take(180), currentMessages.size.toLong())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Continue failed", e)
                _actionError.value = e.localizedMessage ?: "Failed to continue response."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                _streamingChatId.value = null
            }
        }
    }

    fun regenerateResponse(assistantMessage: ChatMessage) {
        val chatId = _selectedChatId.value ?: return
        if (_isGenerating.value) return
        val messages = (messagesState.value as? DataUiState.Success)?.data.orEmpty()
        val idx = messages.indexOfFirst { it.id == assistantMessage.id }
        if (idx < 0) return

        // Find preceding user message
        val precedingUser = messages.subList(0, idx).lastOrNull { it.role == "user" } ?: return
        val historyBeforeUser = messages.subList(0, messages.indexOf(precedingUser))

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingChatId.value = chatId
            _streamingText.value = ""
            try {
                val regenerated = StringBuilder()
                aiService.streamChatReply(historyBeforeUser, precedingUser.content).collect { partial ->
                    regenerated.clear()
                    regenerated.append(partial)
                    _streamingText.value = partial
                }
                val finalText = regenerated.toString().trim()
                if (finalText.isNotEmpty()) {
                    repository.updateMessageContent(chatId, assistantMessage.id, finalText)
                    repository.updateMessageFeedback(chatId, assistantMessage.id, "none")
                    repository.updateChatSummary(chatId, finalText.take(180), messages.size.toLong())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                _actionError.value = e.localizedMessage ?: "Failed to regenerate response."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                _streamingChatId.value = null
            }
        }
    }

    fun editUserPromptAndRegenerate(userMessage: ChatMessage, newPromptText: String) {
        val chatId = _selectedChatId.value ?: return
        val clean = newPromptText.trim()
        if (clean.isBlank() || _isGenerating.value) return
        val messages = (messagesState.value as? DataUiState.Success)?.data.orEmpty()
        val userIdx = messages.indexOfFirst { it.id == userMessage.id }
        if (userIdx < 0) return

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingChatId.value = chatId
            _streamingText.value = ""
            try {
                repository.updateMessageContent(chatId, userMessage.id, clean)
                // Remove messages after the edited prompt so conversation stays coherent
                val trailing = messages.drop(userIdx + 1)
                trailing.forEach { repository.deleteMessage(chatId, it.id) }

                val historyBefore = messages.take(userIdx)
                val fullReply = StringBuilder()
                aiService.streamChatReply(historyBefore, clean).collect { partial ->
                    fullReply.clear()
                    fullReply.append(partial)
                    _streamingText.value = partial
                }
                val finalText = fullReply.toString().trim()
                if (finalText.isNotEmpty()) {
                    repository.addMessage(chatId, "model", finalText, userMessage.toolTag)
                    repository.updateChatSummary(chatId, finalText.take(180), (historyBefore.size + 2).toLong())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Edit prompt failed", e)
                _actionError.value = e.localizedMessage ?: "Failed to regenerate from edited prompt."
            } finally {
                _isGenerating.value = false
                _streamingText.value = ""
                _streamingChatId.value = null
            }
        }
    }

    fun setMessageFeedback(messageId: String, feedback: String) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.updateMessageFeedback(chatId, messageId, feedback)
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameChat(chatId, newTitle)
                .onSuccess { _infoMessage.value = "Chat renamed" }
                .onFailure { _actionError.value = it.localizedMessage ?: "Could not rename chat." }
        }
    }

    fun togglePinChat(chat: ChatThread) {
        viewModelScope.launch {
            repository.togglePinChat(chat.id, !chat.pinned)
                .onSuccess { _infoMessage.value = if (!chat.pinned) "Chat pinned" else "Chat unpinned" }
                .onFailure { _actionError.value = it.localizedMessage ?: "Could not update pin state." }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            if (_selectedChatId.value == chatId) {
                startNewChat()
            }
            repository.deleteChat(chatId)
                .onSuccess { _infoMessage.value = "Chat deleted" }
                .onFailure { _actionError.value = it.localizedMessage ?: "Could not delete chat." }
        }
    }

    fun openPromptInNewChat(title: String, category: String, prompt: String, assistantContent: String) {
        viewModelScope.launch {
            val chatRes = repository.createChat(title, category, assistantContent.take(180))
            chatRes.onSuccess { newId ->
                repository.addMessage(newId, "user", prompt, category)
                repository.addMessage(newId, "model", assistantContent, category)
                repository.updateChatSummary(newId, assistantContent.take(180), 2L)
                _selectedChatId.value = newId
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Could not open project in chat."
            }
        }
    }
}
