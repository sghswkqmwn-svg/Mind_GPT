package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.MinecraftAiService
import com.example.data.ai.PartialStreamTimeoutException
import com.example.data.firebase.MindGptRepository
import com.example.data.model.MinecraftProject
import com.example.data.model.MinecraftTool
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudioViewModel(
    private val repository: MindGptRepository,
    private val aiService: MinecraftAiService,
    private val currentUserId: String,
) : ViewModel() {

    private companion object {
        const val TAG = "StudioViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }

    val projectsState: StateFlow<DataUiState<List<MinecraftProject>>> =
        repository.observeProjects(currentUserId)
            .map<List<MinecraftProject>, DataUiState<List<MinecraftProject>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Failed to observe projects", root)
                emit(DataUiState.Error(root.localizedMessage ?: "Failed to load saved projects."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataUiState.Loading
            )

    private val _activeTool = MutableStateFlow<MinecraftTool?>(null)
    val activeTool: StateFlow<MinecraftTool?> = _activeTool.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generatedOutput = MutableStateFlow("")
    val generatedOutput: StateFlow<String> = _generatedOutput.asStateFlow()

    private val _lastPromptSummary = MutableStateFlow("")
    val lastPromptSummary: StateFlow<String> = _lastPromptSummary.asStateFlow()

    private val _lastEdition = MutableStateFlow("Java")
    val lastEdition: StateFlow<String> = _lastEdition.asStateFlow()

    private val _lastVersion = MutableStateFlow("1.21.4")
    val lastVersion: StateFlow<String> = _lastVersion.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var genJob: Job? = null

    fun selectTool(tool: MinecraftTool?) {
        stopGeneration()
        _activeTool.value = tool
        _generatedOutput.value = ""
        _errorMessage.value = null
    }

    fun generate(
        tool: MinecraftTool,
        edition: String,
        version: String,
        styleOrScale: String,
        requestText: String,
    ) {
        val cleanRequest = requestText.trim()
        if (cleanRequest.isBlank() || _isGenerating.value) return

        _errorMessage.value = null
        _lastEdition.value = edition
        _lastVersion.value = version
        _lastPromptSummary.value = cleanRequest.take(400)

        genJob = viewModelScope.launch {
            _isGenerating.value = true
            _generatedOutput.value = ""
            try {
                aiService.streamMinecraftTool(tool, edition, version, styleOrScale, cleanRequest)
                    .collect { partial ->
                        _generatedOutput.value = partial
                    }
            } catch (e: PartialStreamTimeoutException) {
                Log.w(TAG, "Tool generation paused on timeout with partial output", e)
                if (e.partialText.isNotBlank()) {
                    _generatedOutput.value = e.partialText
                }
                _errorMessage.value = e.localizedMessage ?: "Generation timed out."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tool generation failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to generate ${tool.title}."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        genJob?.cancel()
        genJob = null
        _isGenerating.value = false
    }

    fun saveCurrentToCloud(customTitle: String) {
        val tool = _activeTool.value ?: return
        val content = _generatedOutput.value.trim()
        if (content.isBlank()) return
        val title = customTitle.trim().ifBlank { "${tool.title} Build" }

        viewModelScope.launch {
            repository.saveProject(
                title = title,
                toolType = tool.id,
                minecraftEdition = _lastEdition.value,
                minecraftVersion = _lastVersion.value,
                promptSummary = _lastPromptSummary.value,
                content = content
            ).onSuccess {
                _statusMessage.value = "Saved '$title' to Cloud Projects!"
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to save project."
            }
        }
    }

    fun togglePinProject(project: MinecraftProject) {
        viewModelScope.launch {
            repository.togglePinProject(project.id, !project.pinned)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Could not pin project." }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
                .onSuccess { _statusMessage.value = "Project deleted" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Could not delete project." }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }
}
