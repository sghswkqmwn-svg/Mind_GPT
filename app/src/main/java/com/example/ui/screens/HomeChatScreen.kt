package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ExportHelper
import com.example.core.TtsHelper
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.ui.components.MarkdownText
import com.example.ui.components.MindGptFooter
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.VoxelTypingIndicator
import com.example.ui.components.timeBasedGreeting
import com.example.ui.theme.PixelDisplayFont
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.DataUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeChatScreen(
    viewModel: ChatViewModel,
    ttsHelper: TtsHelper,
    userDisplayName: String,
    autoScrollEnabled: Boolean,
    isOnline: Boolean,
    onOpenSettings: () -> Unit,
    onOpenStudioShortcut: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingChatId by viewModel.streamingChatId.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
    val speakingMessageId by ttsHelper.speakingMessageId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val chats = (chatsState as? DataUiState.Success)?.data.orEmpty()
    val activeChat: ChatThread? = chats.firstOrNull { it.id == selectedChatId }
    val messages = (messagesState as? DataUiState.Success)?.data.orEmpty()

    var inputText by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editedPromptDraft by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // SAF Document launchers for saving TXT and PDF locally (zero permissions needed)
    val saveTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val title = activeChat?.title ?: "MindGPT Chat"
            ExportHelper.writeTxtToUri(context, uri, title, messages)
                .onSuccess { Toast.makeText(context, "Saved TXT export", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            val title = activeChat?.title ?: "MindGPT Chat"
            ExportHelper.writePdfToUri(context, uri, title, messages)
                .onSuccess { Toast.makeText(context, "Saved PDF export", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "PDF export failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(infoMessage) {
        if (!infoMessage.isNullOrBlank()) {
            Toast.makeText(context, infoMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearInfo()
        }
    }

    LaunchedEffect(messages.size, streamingText, autoScrollEnabled) {
        if (autoScrollEnabled && (messages.isNotEmpty() || streamingText.isNotEmpty())) {
            val targetIdx = (messages.size + if (isGenerating) 1 else 0).coerceAtLeast(0)
            if (targetIdx > 0) {
                listState.animateScrollToItem(targetIdx - 1)
            }
        }
    }

    val firstName = remember(userDisplayName) {
        userDisplayName.trim().substringBefore(" ").ifBlank { "Builder" }
    }
    val greeting = remember { timeBasedGreeting() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Persistent Top Header always displaying "Welcome to MindGPT" + Greeting
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_launcher_foreground),
                            contentDescription = "MindGPT Icon",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Welcome to MindGPT",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$greeting, $firstName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showHistorySheet = true },
                            modifier = Modifier.testTag("chat_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Chat history"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.startNewChat() },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddComment,
                                contentDescription = "New chat"
                            )
                        }
                        if (messages.isNotEmpty()) {
                            Box {
                                IconButton(
                                    onClick = { showExportMenu = true },
                                    modifier = Modifier.testTag("export_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Export chat"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Save as TXT") },
                                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                        onClick = {
                                            showExportMenu = false
                                            val name = (activeChat?.title ?: "mindgpt_chat")
                                                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                            saveTxtLauncher.launch("$name.txt")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save as PDF") },
                                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                                        onClick = {
                                            showExportMenu = false
                                            val name = (activeChat?.title ?: "mindgpt_chat")
                                                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                            savePdfLauncher.launch("$name.pdf")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share TXT") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = {
                                            showExportMenu = false
                                            ExportHelper.shareChatAsTxt(
                                                context,
                                                activeChat?.title ?: "MindGPT Chat",
                                                messages
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share PDF") },
                                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                                        onClick = {
                                            showExportMenu = false
                                            ExportHelper.shareChatAsPdf(
                                                context,
                                                activeChat?.title ?: "MindGPT Chat",
                                                messages
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = "Appearance & Settings"
                            )
                        }
                    }
                }

                if (activeChat != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeChat.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!isOnline) {
            OfflineStatusBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        if (!actionError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = actionError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Chat Body or New Chat ("Hi!" / "Hello!") Welcome State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (messages.isEmpty() && !isGenerating) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "NEW CHAT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelDisplayFont),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Hi! $greeting, $firstName!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hello! Ask MindGPT anything about Minecraft builds, WorldEdit schematics, redstone farms, 1.21+ commands, datapacks, Paper plugins, or server setup.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onOpenStudioShortcut,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open 15 Minecraft Tools")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Quick Starters",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val starters = listOf(
                            "Design a medieval emerald SMP spawn with WorldEdit commands",
                            "Give me a 1.21.4 /give command for a custom mythic trident",
                            "Build a compact 1.21 automatic bamboo & bone meal farm",
                            "Suggest a dark deepslate + oxidized copper castle palette",
                            "Create a Paper 1.21 /fly toggle plugin in Java"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            starters.forEach { prompt ->
                                AssistChip(
                                    onClick = { viewModel.sendMessage(prompt) },
                                    label = { Text(prompt, maxLines = 2) }
                                )
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MindGptFooter()
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isLastModelMsg = msg.role == "model" &&
                            messages.lastOrNull { it.role == "model" }?.id == msg.id
                        ChatMessageBubble(
                            message = msg,
                            isLastModelMessage = isLastModelMsg,
                            isGenerating = isGenerating,
                            isSpeaking = speakingMessageId == msg.id,
                            onCopy = {
                                clipboard.setText(AnnotatedString(msg.content))
                                Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                            },
                            onSpeakToggle = { ttsHelper.toggleSpeak(msg.id, msg.content) },
                            onShare = { ExportHelper.sharePlainText(context, "MindGPT Answer", msg.content) },
                            onLike = {
                                val next = if (msg.feedback == "like") "none" else "like"
                                viewModel.setMessageFeedback(msg.id, next)
                            },
                            onDislike = {
                                val next = if (msg.feedback == "dislike") "none" else "dislike"
                                viewModel.setMessageFeedback(msg.id, next)
                            },
                            onRegenerate = { viewModel.regenerateResponse(msg) },
                            onContinue = { viewModel.continueResponse(msg) },
                            onEditPrompt = {
                                editingMessage = msg
                                editedPromptDraft = msg.content
                            },
                            onCodeCopied = { toast ->
                                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (isGenerating && (streamingChatId == selectedChatId || selectedChatId == null)) {
                        item(key = "streaming_bubble") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VoxelTypingIndicator()
                                    if (streamingText.isNotBlank()) {
                                        MarkdownText(
                                            markdown = streamingText,
                                            onCodeCopied = {
                                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer Bar with Stop Generating button
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { viewModel.stopGenerating() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("stop_generating_button")
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Generating")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask MindGPT anything about Minecraft...") },
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    FilledIconButton(
                        onClick = {
                            val prompt = inputText
                            inputText = ""
                            viewModel.sendMessage(prompt)
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send prompt"
                        )
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            chats = chats,
            selectedChatId = selectedChatId,
            searchQuery = searchQuery,
            onSearchChange = viewModel::updateSearchQuery,
            onSelectChat = {
                viewModel.selectChat(it.id)
                showHistorySheet = false
            },
            onNewChat = {
                viewModel.startNewChat()
                showHistorySheet = false
            },
            onPinToggle = viewModel::togglePinChat,
            onRename = viewModel::renameChat,
            onDelete = { viewModel.deleteChat(it.id) },
            onDismiss = { showHistorySheet = false }
        )
    }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Prompt") },
            text = {
                OutlinedTextField(
                    value = editedPromptDraft,
                    onValueChange = { editedPromptDraft = it },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = editingMessage
                        editingMessage = null
                        if (target != null && editedPromptDraft.isNotBlank()) {
                            viewModel.editUserPromptAndRegenerate(target, editedPromptDraft)
                        }
                    }
                ) {
                    Text("Save & Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isLastModelMessage: Boolean,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onSpeakToggle: () -> Unit,
    onShare: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onEditPrompt: () -> Unit,
    onCodeCopied: (String) -> Unit,
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.widthIn(max = if (isUser) 520.dp else 740.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else "MINDGPT",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelDisplayFont),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                    if (isUser && !isGenerating) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit prompt",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onEditPrompt() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onCodeCopied = onCodeCopied
                    )
                }
            }
        }

        if (!isUser) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "Copy text", modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onSpeakToggle, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop reading aloud" else "Read aloud",
                        tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Share, "Share response", modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onLike, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = "Like response",
                        tint = if (message.feedback == "like") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDislike, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbDown,
                        contentDescription = "Dislike response",
                        tint = if (message.feedback == "dislike") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                if (!isGenerating) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Refresh, "Regenerate response", modifier = Modifier.size(18.dp))
                    }
                    if (isLastModelMessage) {
                        TextButton(
                            onClick = onContinue,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.FastForward, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Continue", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
