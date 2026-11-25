package com.example.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.PixelDisplayFont
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var fadingOut by remember { mutableStateOf(false) }

    val contentAlpha by animateFloatAsState(
        targetValue = if (fadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "splash_fade"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "voxel_bg")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voxel_drift"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    LaunchedEffect(Unit) {
        delay(1500L)
        fadingOut = true
        delay(450L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09160F))
            .alpha(contentAlpha)
            .testTag("splash_screen")
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.drawable.img_splash_world)
                .crossfade(true)
                .build(),
            contentDescription = "Minecraft animated voxel landscape",
            contentScale = ContentScale.Crop,
            alpha = 0.65f,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle animated Minecraft voxel particles & beacon beam
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Emerald beacon light beam
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        EmeraldGlow.copy(alpha = 0.25f * pulse),
                        DiamondCyan.copy(alpha = 0.08f * pulse),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(w * 0.44f, 0f),
                size = Size(w * 0.12f, h * 0.72f)
            )

            // Floating animated voxel squares
            val seeds = listOf(
                Triple(0.14f, 0.22f, 22f),
                Triple(0.82f, 0.18f, 28f),
                Triple(0.24f, 0.68f, 18f),
                Triple(0.76f, 0.62f, 24f),
                Triple(0.12f, 0.45f, 14f),
                Triple(0.88f, 0.40f, 16f),
            )
            seeds.forEachIndexed { idx, (xFrac, yBase, blockDp) ->
                val yOffset = ((drift + idx * 0.17f) % 1f) * (h * 0.25f)
                val yPos = (h * yBase) - yOffset
                val color = if (idx % 2 == 0) EmeraldGlow else DiamondCyan
                val blockSize = blockDp.dp.toPx()
                drawRect(
                    color = color.copy(alpha = 0.22f * pulse),
                    topLeft = Offset(w * xFrac, yPos),
                    size = Size(blockSize, blockSize)
                )
                drawRect(
                    color = color.copy(alpha = 0.55f),
                    topLeft = Offset(w * xFrac, yPos),
                    size = Size(blockSize, blockSize),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x7709160F),
                            Color(0xAA09160F),
                            Color(0xEE09160F)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.img_launcher_foreground)
                        .crossfade(true)
                        .build(),
                    contentDescription = "MindGPT Logo",
                    modifier = Modifier
                        .size(112.dp)
                        .clip(MaterialTheme.shapes.large)
                )

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Welcome to MindGPT",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFFECFDF5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "The Ultimate Minecraft AI Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    color = DiamondCyan,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                color = Color(0xFF101C16).copy(alpha = 0.88f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Created by Loyal LP",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = PixelDisplayFont),
                    color = EmeraldGlow,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
