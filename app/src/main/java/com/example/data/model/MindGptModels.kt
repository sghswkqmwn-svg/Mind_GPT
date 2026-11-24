package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val lastMessagePreview: String = "",
    val category: String = "general",
    val pinned: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val content: String = "",
    val feedback: String = "none",
    val toolTag: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class MinecraftProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val toolType: String = "worldedit",
    val minecraftEdition: String = "Java",
    val minecraftVersion: String = "1.21.4",
    val promptSummary: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ServerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val host: String = "",
    val gamePort: Long = 25565L,
    val rconPort: Long = 25575L,
    val panelUrl: String = "",
    val connectionMode: String = "PING_ONLY",
    val lastStatus: String = "Not checked",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class ServerConnectionMode(val key: String, val label: String) {
    PANEL_HTTPS("PANEL_HTTPS", "HTTPS Panel API"),
    RCON_TCP("RCON_TCP", "RCON TCP"),
    PING_ONLY("PING_ONLY", "Status Ping Only");

    companion object {
        fun fromRaw(raw: String): ServerConnectionMode =
            entries.firstOrNull { it.key == raw } ?: PING_ONLY
    }
}

enum class MinecraftToolCategory(val label: String) {
    BUILDERS("Builders & Worlds"),
    TECHNICAL("Commands, Packs & Code"),
    HELPERS("Mechanics & Q&A"),
}

enum class MinecraftTool(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: MinecraftToolCategory,
    val icon: ImageVector,
    val promptHint: String,
    val defaultSpecs: String,
) {
    WORLDEDIT_SCHEMATIC(
        id = "worldedit",
        title = "WorldEdit Schematic",
        subtitle = "Step-by-step //pos, //set, //brush & schematic commands",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.Architecture,
        promptHint = "e.g., Floating wizard tower with spiral stairs and amethyst roof",
        defaultSpecs = "50x50 plot, detailed exterior + WorldEdit brush sequence"
    ),
    SPAWN_DESIGNER(
        id = "spawn",
        title = "Spawn Designer",
        subtitle = "SMP / Skyblock / Hub spawn layout, NPCs & portals",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.AccountBalance,
        promptHint = "e.g., Lush emerald SMP spawn with 4 cardinal portals and rules board",
        defaultSpecs = "100x100 circular plaza, central beacon, safezone layout"
    ),
    LOBBY_DESIGNER(
        id = "lobby",
        title = "Lobby Designer",
        subtitle = "Minigame waiting lobby, parkour route & leaderboard zones",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.MeetingRoom,
        promptHint = "e.g., Futuristic cyber hub with hologram pedestals and glass bridge",
        defaultSpecs = "Compact server lobby with queue portals and parkour loop"
    ),
    PVP_ARENA(
        id = "pvp_arena",
        title = "PvP Arena Generator",
        subtitle = "Balanced kit PvP / duel / KOTH arena layouts",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.SportsKabaddi,
        promptHint = "e.g., Volcanic nether colosseum with obsidian pillars and lava moats",
        defaultSpecs = "Symmetrical 64x64 duel arena with sightline cover"
    ),
    HOUSE_GENERATOR(
        id = "house",
        title = "House Generator",
        subtitle = "Starter bases, cottages, modern villas & interiors",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.Home,
        promptHint = "e.g., Cozy spruce & deepslate survival cabin with storage basement",
        defaultSpecs = "Survival-friendly material list, floorplan & roof layers"
    ),
    CASTLE_GENERATOR(
        id = "castle",
        title = "Castle Generator",
        subtitle = "Fortresses, gatehouses, towers & throne halls",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.Castle,
        promptHint = "e.g., Cliffside deepslate citadel with drawbridge and watchtowers",
        defaultSpecs = "Multi-stage medieval keep with curtain walls & battlements"
    ),
    TERRAIN_GENERATOR(
        id = "terrain",
        title = "Terrain Generator",
        subtitle = "WorldPainter / Axiom / WorldEdit terraforming workflows",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.Landscape,
        promptHint = "e.g., Alpine fjord valley with custom pine trees and waterfalls",
        defaultSpecs = "Brush sizes, noise masks, block gradients & foliage scatter"
    ),
    BLOCK_PALETTE(
        id = "palette",
        title = "Block Palette",
        subtitle = "Curated block gradients, trims, lighting & texturing",
        category = MinecraftToolCategory.BUILDERS,
        icon = Icons.Rounded.Palette,
        promptHint = "e.g., Oxidized copper steampunk factory with warm lantern accents",
        defaultSpecs = "Primary, secondary, accent, trim, and light source blocks"
    ),
    COMMAND_GENERATOR(
        id = "command",
        title = "Command Generator",
        subtitle = "Exact /give, /summon, /execute & command block chains",
        category = MinecraftToolCategory.TECHNICAL,
        icon = Icons.Rounded.Terminal,
        promptHint = "e.g., Custom diamond sword with Sharpness 10 and particle trail",
        defaultSpecs = "1.21+ data component syntax + repeating command chain"
    ),
    DATAPACK_GENERATOR(
        id = "datapack",
        title = "Datapack Generator",
        subtitle = "pack.mcmeta, mcfunction loops, recipes & custom advancements",
        category = MinecraftToolCategory.TECHNICAL,
        icon = Icons.Rounded.DataObject,
        promptHint = "e.g., Vein-miner datapack with sneak toggle and durability check",
        defaultSpecs = "Full folder tree + pack.mcmeta + load/tick mcfunctions"
    ),
    PLUGIN_GENERATOR(
        id = "plugin",
        title = "Plugin Generator",
        subtitle = "Paper / Spigot Java plugin classes, plugin.yml & listeners",
        category = MinecraftToolCategory.TECHNICAL,
        icon = Icons.Rounded.Extension,
        promptHint = "e.g., Paper 1.21 /home & /sethome plugin with cooldowns",
        defaultSpecs = "Java 21 Paper API, plugin.yml, command & listener code"
    ),
    REDSTONE_HELPER(
        id = "redstone",
        title = "Redstone Helper",
        subtitle = "Farms, piston doors, sorters, clocks & comparator logic",
        category = MinecraftToolCategory.HELPERS,
        icon = Icons.Rounded.ElectricBolt,
        promptHint = "e.g., Compact 3x3 flush piston door or auto-smelter super array",
        defaultSpecs = "Bill of materials, layer-by-layer build & timing ticks"
    ),
    RESOURCE_PACK_HELPER(
        id = "resource_pack",
        title = "Resource Pack Helper",
        subtitle = "CustomModelData, sounds.json, blockstates & textures",
        category = MinecraftToolCategory.TECHNICAL,
        icon = Icons.Rounded.AutoFixHigh,
        promptHint = "e.g., Custom emerald staff model override on carved_pumpkin",
        defaultSpecs = "pack.mcmeta pack_format, JSON model & directory layout"
    ),
    MOD_HELPER(
        id = "mod",
        title = "Mod Helper",
        subtitle = "Fabric / NeoForge items, blocks, mixins & mod debugging",
        category = MinecraftToolCategory.TECHNICAL,
        icon = Icons.Rounded.Code,
        promptHint = "e.g., Fabric 1.21 custom emerald armor set with night vision effect",
        defaultSpecs = "Fabric/NeoForge registration code, JSON assets & tips"
    ),
    MINECRAFT_QA(
        id = "qa",
        title = "Minecraft Q&A",
        subtitle = "Enchanting, villager trading, mob spawning & crash fixes",
        category = MinecraftToolCategory.HELPERS,
        icon = Icons.Rounded.HelpOutline,
        promptHint = "e.g., Best villager trading hall zombification setup in 1.21",
        defaultSpecs = "Accurate mechanics, exact numbers & pro tips"
    );

    companion object {
        fun fromId(id: String): MinecraftTool =
            entries.firstOrNull { it.id == id } ?: MINECRAFT_QA
    }
}
