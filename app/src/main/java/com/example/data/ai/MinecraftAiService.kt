package com.example.data.ai

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.MinecraftTool
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeout

private const val TAG = "MinecraftAiService"
private const val STREAM_IDLE_TIMEOUT_MS = 60_000L
private const val STREAM_MAX_TOTAL_MS = 180_000L

class PartialStreamTimeoutException(
    val partialText: String,
    message: String,
) : IllegalStateException(message)

class MinecraftAiService {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are MindGPT, The Ultimate Minecraft AI Assistant created by Loyal LP.
                    You are an expert across Minecraft Java Edition and Bedrock Edition (including 1.21+ data components, WorldEdit/FAWE, Axiom, Paper/Spigot/Purpur plugins, Fabric/NeoForge mods, datapacks, resource packs, redstone engineering, server administration, and architectural design).
                    Format responses using clean Markdown:
                    - Use concise headings, bullet lists, and bold highlights for block palettes and steps.
                    - Wrap all commands, WorldEdit sequences, mcfunction files, JSON, and Java/Kotlin code in fenced Markdown code blocks with the language label (e.g. ```mcfunction, ```bash, ```json, ```java).
                    - Always specify Java vs Bedrock differences when relevant.
                    - Be accurate, practical, and immediately usable in-game or on a server.
                    """.trimIndent()
                )
            }
        )
    }

    private val titleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text("Generate a concise 2-5 word title for a Minecraft AI chat. Output ONLY the plain title with no quotes or punctuation.")
            }
        )
    }

    fun streamChatReply(
        history: List<ChatMessage>,
        userPrompt: String,
    ): Flow<String> = flow {
        val contents = buildHistoryContents(history, userPrompt)
        collectWithIdleTimeout(
            upstream = chatModel.generateContentStream(contents),
            emptyErrorMessage = "MindGPT returned an empty response. Please try again.",
            timeoutLogLabel = "Streaming chat reply timed out",
            timeoutActionHint = "Response paused after 60s of inactivity. Partial reply saved — tap Continue to finish.",
            noDataTimeoutMessage = "MindGPT took too long to respond (60s timeout). Please tap Retry."
        ).collect { emit(it) }
    }

    fun streamContinueReply(
        history: List<ChatMessage>,
        existingAssistantText: String,
    ): Flow<String> = flow {
        val continuePrompt =
            "Continue seamlessly from where you stopped without repeating previous text. Finish any unfinished code blocks, steps, or explanations."
        val normalizedHistory = buildNormalizedTurns(
            history.takeLast(10).map { msg ->
                val role = if (msg.role == "model") "model" else "user"
                role to msg.content
            } + buildList {
                if (history.lastOrNull()?.role != "model" && existingAssistantText.isNotBlank()) {
                    add("model" to existingAssistantText)
                }
                add("user" to continuePrompt)
            }
        )
        collectWithIdleTimeout(
            upstream = chatModel.generateContentStream(normalizedHistory),
            emptyErrorMessage = "No additional continuation text was generated.",
            timeoutLogLabel = "Continue reply timed out",
            timeoutActionHint = "Continuation paused after 60s of inactivity. Tap Continue again to finish.",
            noDataTimeoutMessage = "Continuation timed out after 60 seconds. Please try again."
        ).collect { emit(it) }
    }

    fun streamMinecraftTool(
        tool: MinecraftTool,
        edition: String,
        version: String,
        styleOrScale: String,
        userRequest: String,
    ): Flow<String> = flow {
        val prompt = buildToolPrompt(tool, edition, version, styleOrScale, userRequest)
        collectWithIdleTimeout(
            upstream = chatModel.generateContentStream(prompt),
            emptyErrorMessage = "Generator returned an empty output.",
            timeoutLogLabel = "Minecraft tool generation timed out",
            timeoutActionHint = "Generation paused after 60s of inactivity. Partial output preserved.",
            noDataTimeoutMessage = "Generation timed out after 60 seconds. Try narrowing your prompt or tapping Generate again."
        ).collect { emit(it) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun collectWithIdleTimeout(
        upstream: Flow<com.google.firebase.ai.type.GenerateContentResponse>,
        emptyErrorMessage: String,
        timeoutLogLabel: String,
        timeoutActionHint: String,
        noDataTimeoutMessage: String,
    ): Flow<String> = flow {
        val accumulated = StringBuilder()
        val startTime = System.currentTimeMillis()
        try {
            coroutineScope {
                val channel = upstream.produceIn(this)
                try {
                    while (true) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingTotal = (STREAM_MAX_TOTAL_MS - elapsed).coerceAtLeast(1_000L)
                        val stepTimeout = minOf(STREAM_IDLE_TIMEOUT_MS, remainingTotal)
                        val result = withTimeout(stepTimeout) {
                            channel.receiveCatching()
                        }
                        if (result.isClosed) {
                            result.exceptionOrNull()?.let { throw it }
                            break
                        }
                        val chunkText = result.getOrThrow().text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            emit(accumulated.toString())
                        }
                    }
                } finally {
                    channel.cancel()
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException(emptyErrorMessage)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, timeoutLogLabel, e)
            val partial = accumulated.toString().trim()
            if (partial.isNotBlank()) {
                throw PartialStreamTimeoutException(partial, timeoutActionHint)
            } else {
                throw IllegalStateException(noDataTimeoutMessage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$timeoutLogLabel (error)", e)
            throw e
        }
    }

    suspend fun suggestChatTitle(firstPrompt: String): String {
        return try {
            withTimeout(15_000L) {
                val res = titleModel.generateContent("Summarize into a short title: ${firstPrompt.take(300)}")
                res.text?.trim()?.replace("\"", "")?.take(60)?.ifBlank { null }
                    ?: firstPrompt.trim().take(40)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Title suggestion fallback used", e)
            firstPrompt.trim().take(40).ifBlank { "Minecraft Chat" }
        }
    }

    private fun buildHistoryContents(history: List<ChatMessage>, newPrompt: String): List<Content> {
        val rawTurns = buildList {
            history.takeLast(14).forEach { msg ->
                if (msg.content.isNotBlank()) {
                    val role = if (msg.role == "model") "model" else "user"
                    add(role to msg.content)
                }
            }
            add("user" to newPrompt)
        }
        return buildNormalizedTurns(rawTurns)
    }

    private fun buildNormalizedTurns(rawTurns: List<Pair<String, String>>): List<Content> {
        val merged = mutableListOf<Pair<String, String>>()
        for ((role, text) in rawTurns) {
            if (text.isBlank()) continue
            if (merged.isEmpty() && role == "model") continue
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                merged[merged.lastIndex] = role to "${last.second}\n\n$text"
            } else {
                merged.add(role to text)
            }
        }
        return merged.map { (role, text) ->
            content(role) { text(text) }
        }
    }

    private fun buildToolPrompt(
        tool: MinecraftTool,
        edition: String,
        version: String,
        styleOrScale: String,
        userRequest: String,
    ): String {
        val toolInstructions = when (tool) {
            MinecraftTool.WORLDEDIT_SCHEMATIC ->
                "Provide exact WorldEdit / FAWE commands (`//pos1`, `//pos2`, `//set`, `//replace`, `//br`, `//schem save`), dimensions, block palette percentages, and step-by-step build layers."
            MinecraftTool.SPAWN_DESIGNER ->
                "Design a complete server spawn layout with coordinate zones (safezone, portals, NPCs, crates, rules board), block palette, lighting, and WorldEdit/command setup tips."
            MinecraftTool.LOBBY_DESIGNER ->
                "Create a minigame/hub lobby blueprint with player flow, queue portals, parkour route, cosmetic areas, and decoration palette."
            MinecraftTool.PVP_ARENA ->
                "Design a balanced PvP arena with exact dimensions, symmetry, cover geometry, hazard placement, spawn points, and command/WorldGuard region flags."
            MinecraftTool.HOUSE_GENERATOR ->
                "Generate a detailed Minecraft house build guide with footprint grid, material list with counts, layer-by-layer exterior/roof instructions, and interior layout."
            MinecraftTool.CASTLE_GENERATOR ->
                "Create a multi-section castle blueprint (gatehouse, curtain walls, towers, courtyard, keep) with block gradients, depth techniques, and WorldEdit shortcuts."
            MinecraftTool.TERRAIN_GENERATOR ->
                "Provide a terraforming workflow (WorldEdit brushes/masks, Axiom/WorldPainter tips, block gradient layers, custom trees/rocks, and water features)."
            MinecraftTool.BLOCK_PALETTE ->
                "Suggest 3 curated block palette variations (Primary walls, Gradient transitions, Trim/roof, Flooring, Lighting, Foliage) with exact block IDs and contrast notes."
            MinecraftTool.COMMAND_GENERATOR ->
                "Provide exact, copy-paste-ready commands verified for $edition $version (including 1.21+ item components if Java 1.20.5+), command block types (Impulse/Chain/Repeat), and testing steps."
            MinecraftTool.DATAPACK_GENERATOR ->
                "Output the complete datapack folder tree, `pack.mcmeta` with the exact `pack_format` for $version, `data/minecraft/tags/function/load.json` & `tick.json` (or `functions` if <1.21), and all `.mcfunction` files."
            MinecraftTool.PLUGIN_GENERATOR ->
                "Provide production-clean Paper/Spigot Java plugin code (`plugin.yml`, main plugin class, command executor, event listener, config notes, and Maven/Gradle dependency snippet)."
            MinecraftTool.REDSTONE_HELPER ->
                "Design the redstone contraption with exact dimensions, complete component list, tick timings, block-by-block wiring steps, and troubleshooting tips."
            MinecraftTool.RESOURCE_PACK_HELPER ->
                "Provide the resource pack folder structure, `pack.mcmeta` with exact `pack_format` for $version, JSON model/blockstate/CustomModelData definitions, and testing checklist."
            MinecraftTool.MOD_HELPER ->
                "Provide clean Fabric or NeoForge modding code/JSON for $version, registration classes, asset/data JSON paths, and build/debugging guidance."
            MinecraftTool.MINECRAFT_QA ->
                "Answer thoroughly with exact $edition $version mechanics, formulas/rates, edge cases, and practical in-game recommendations."
        }
        return """
            Tool: ${tool.title}
            Target Edition: $edition Edition
            Minecraft Version: $version
            Style / Scale / Options: ${styleOrScale.ifBlank { tool.defaultSpecs }}
            User Request: $userRequest

            Instructions: $toolInstructions
        """.trimIndent()
    }
}
