package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ServerSecretsStore
import com.example.core.ThemeMode
import com.example.core.ThemePreferences
import com.example.core.TtsHelper
import com.example.data.ai.MinecraftAiService
import com.example.data.firebase.MindGptRepository
import com.example.data.server.MinecraftServerClient
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.HomeChatScreen
import com.example.ui.screens.MinecraftStudioScreen
import com.example.ui.screens.ProjectsCloudScreen
import com.example.ui.screens.ServerManagerScreen
import com.example.ui.screens.SettingsAppearanceSheet
import com.example.ui.splash.SplashScreen
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ServerViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class MindGptDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    CHAT("chat", "Chat", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    STUDIO("studio", "Studio", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    PROJECTS("projects", "Projects", Icons.Rounded.FolderSpecial, Icons.Outlined.FolderSpecial),
    SERVERS("servers", "Servers", Icons.Rounded.Dns, Icons.Outlined.Dns),
}

@Composable
fun MindGptRoot(
    themePrefs: ThemePreferences,
    themeMode: ThemeMode,
    autoScrollEnabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var credentialManager by remember { mutableStateOf<CredentialManager?>(null) }

    var showSplash by remember { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(null) }

    DisposableEffect(Unit) {
        val auth = Firebase.auth
        currentUser = auth.currentUser
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        yield()
        val cm = CredentialManager.create(context)
        credentialManager = cm
        attemptAutoSignIn(
            context = context,
            credentialManager = cm,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = {},
            scope = scope
        )
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "splash_transition"
    ) { splashVisible ->
        if (splashVisible) {
            SplashScreen(onFinished = { showSplash = false })
        } else {
            val user = currentUser
            if (user == null) {
                AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
            } else {
                key(user.uid) {
                    AuthenticatedMindGptShell(
                        currentUserId = user.uid,
                        userDisplayName = user.displayName ?: "Minecraft Builder",
                        userEmail = user.email.orEmpty(),
                        themeMode = themeMode,
                        autoScrollEnabled = autoScrollEnabled,
                        onSelectTheme = { mode -> scope.launch { themePrefs.setThemeMode(mode) } },
                        onToggleAutoScroll = { enabled -> scope.launch { themePrefs.setAutoScroll(enabled) } },
                        onSignOut = {
                            val cm = credentialManager ?: CredentialManager.create(context)
                            signOut(cm, scope) {
                                currentUser = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedMindGptShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    themeMode: ThemeMode,
    autoScrollEnabled: Boolean,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleAutoScroll: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val isOnline = rememberNetworkAvailability(context)

    val ttsHelper = remember(context) { TtsHelper(context) }
    DisposableEffect(ttsHelper) {
        onDispose { ttsHelper.shutdown() }
    }

    val repository = remember(context) { MindGptRepository(context) }
    val aiService = remember { MinecraftAiService() }
    val serverClient = remember { MinecraftServerClient() }
    val secretsStore = remember(context) { ServerSecretsStore(context) }

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(repository, aiService, currentUserId, userDisplayName)
            }
        }
    )

    val studioViewModel: StudioViewModel = viewModel(
        key = "studio_$currentUserId",
        factory = viewModelFactory {
            initializer {
                StudioViewModel(repository, aiService, currentUserId)
            }
        }
    )

    val serverViewModel: ServerViewModel = viewModel(
        key = "server_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ServerViewModel(repository, serverClient, secretsStore, currentUserId)
            }
        }
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MindGptDestination.CHAT.route

    var showSettingsSheet by remember { mutableStateOf(false) }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(MindGptDestination.CHAT.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MindGptDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MindGptDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = MindGptDestination.CHAT.route
                    ) {
                        composable(MindGptDestination.CHAT.route) {
                            HomeChatScreen(
                                viewModel = chatViewModel,
                                ttsHelper = ttsHelper,
                                userDisplayName = userDisplayName,
                                autoScrollEnabled = autoScrollEnabled,
                                isOnline = isOnline,
                                onOpenSettings = { showSettingsSheet = true },
                                onOpenStudioShortcut = { navigateToTab(MindGptDestination.STUDIO.route) }
                            )
                        }
                        composable(MindGptDestination.STUDIO.route) {
                            MinecraftStudioScreen(
                                viewModel = studioViewModel,
                                ttsHelper = ttsHelper,
                                onContinueInChat = { title, category, prompt, output ->
                                    chatViewModel.openPromptInNewChat(title, category, prompt, output)
                                    navigateToTab(MindGptDestination.CHAT.route)
                                }
                            )
                        }
                        composable(MindGptDestination.PROJECTS.route) {
                            ProjectsCloudScreen(
                                viewModel = studioViewModel,
                                onOpenInChat = { title, category, prompt, output ->
                                    chatViewModel.openPromptInNewChat(title, category, prompt, output)
                                    navigateToTab(MindGptDestination.CHAT.route)
                                },
                                onOpenStudio = { navigateToTab(MindGptDestination.STUDIO.route) }
                            )
                        }
                        composable(MindGptDestination.SERVERS.route) {
                            ServerManagerScreen(viewModel = serverViewModel)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsAppearanceSheet(
            userName = userDisplayName,
            userEmail = userEmail,
            themeMode = themeMode,
            autoScrollEnabled = autoScrollEnabled,
            onSelectTheme = onSelectTheme,
            onToggleAutoScroll = onToggleAutoScroll,
            onSignOut = {
                showSettingsSheet = false
                onSignOut()
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun rememberNetworkAvailability(context: Context): Boolean {
    val cm = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var isConnected by remember {
        mutableStateOf(
            cm?.activeNetwork?.let { net ->
                cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } ?: true
        )
    }

    DisposableEffect(cm) {
        if (cm == null) return@DisposableEffect onDispose {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
            }
            override fun onLost(network: Network) {
                val active = cm.activeNetwork
                isConnected = active != null &&
                    cm.getNetworkCapabilities(active)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(req, callback)
        } catch (_: Exception) {}
        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }
    return isConnected
}
