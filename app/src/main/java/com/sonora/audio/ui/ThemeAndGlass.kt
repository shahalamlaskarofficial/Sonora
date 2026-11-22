package com.sonora.audio.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SonoraPalette {
    val Cyan = Color(0xFF00E5FF)
    val Violet = Color(0xFF7C4DFF)
    val Mint = Color(0xFF00FFA3)
    val Amber = Color(0xFFFFB74D)
    val Coral = Color(0xFFFF5252)

    val DarkBg = Color(0xFF07090F)
    val DarkSurface = Color(0xFF101626)
    val LightBg = Color(0xFFF2F5FC)
    val LightSurface = Color(0xFFFFFFFF)
}

@Composable
fun SonoraTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = SonoraPalette.Cyan,
            secondary = SonoraPalette.Violet,
            tertiary = SonoraPalette.Mint,
            background = SonoraPalette.DarkBg,
            surface = SonoraPalette.DarkSurface,
            onBackground = Color(0xFFF3F6FF),
            onSurface = Color(0xFFF3F6FF),
            onSurfaceVariant = Color(0xFF9AA7C0)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0066FF),
            secondary = SonoraPalette.Violet,
            tertiary = Color(0xFF00B87C),
            background = SonoraPalette.LightBg,
            surface = SonoraPalette.LightSurface,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF52607A)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AuroraBackground(darkTheme: Boolean, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) SonoraPalette.DarkBg else SonoraPalette.LightBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val alphaBoost = if (darkTheme) 0.24f else 0.14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SonoraPalette.Cyan.copy(alpha = alphaBoost), Color.Transparent),
                    center = Offset(size.width * (0.15f + 0.12f * drift), size.height * 0.12f),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * (0.15f + 0.12f * drift), size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SonoraPalette.Violet.copy(alpha = alphaBoost), Color.Transparent),
                    center = Offset(size.width * (0.85f - 0.12f * drift), size.height * 0.38f),
                    radius = size.width * 0.68f
                ),
                radius = size.width * 0.68f,
                center = Offset(size.width * (0.85f - 0.12f * drift), size.height * 0.38f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SonoraPalette.Mint.copy(alpha = alphaBoost * 0.7f), Color.Transparent),
                    center = Offset(size.width * 0.45f, size.height * (0.88f - 0.06f * drift)),
                    radius = size.width * 0.60f
                ),
                radius = size.width * 0.60f,
                center = Offset(size.width * 0.45f, size.height * (0.88f - 0.06f * drift))
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    highlightAccent: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = if (darkTheme) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (highlightAccent) 0.13f else 0.08f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.88f),
                Color.White.copy(alpha = 0.65f)
            )
        )
    }
    val borderBrush = Brush.linearGradient(
        if (highlightAccent) {
            listOf(SonoraPalette.Cyan.copy(alpha = 0.65f), SonoraPalette.Violet.copy(alpha = 0.45f))
        } else if (darkTheme) {
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        } else {
            listOf(Color.White, Color(0xFFD6E0F5))
        }
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillBrush)
            .border(1.dp, borderBrush, shape)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun AnimatedBatteryRing(
    percent: Int?,
    label: String,
    charging: Boolean = false,
    sizeDp: Dp = 74.dp,
    darkTheme: Boolean = true
) {
    val targetSweep = ((percent ?: 0).coerceIn(0, 100) / 100f) * 360f
    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "batterySweep"
    )
    val ringColor = when {
        percent == null -> Color.Gray.copy(alpha = 0.45f)
        charging -> SonoraPalette.Cyan
        percent <= 20 -> SonoraPalette.Coral
        percent <= 45 -> SonoraPalette.Amber
        else -> SonoraPalette.Mint
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sizeDp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (percent != null) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(ringColor, SonoraPalette.Cyan, ringColor)),
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percent?.let { "$it%" } ?: "--",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (charging) {
                    Text(
                        text = "CHG",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SonoraPalette.Cyan
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EqualizerCurveVisualizer(
    bandsDb: List<Float>,
    enabled: Boolean,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val safeBands = if (bandsDb.size == 5) bandsDb else listOf(0f, 0f, 0f, 0f, 0f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
    ) {
        val w = size.width
        val h = size.height
        val padX = 20f
        val usableW = w - padX * 2f
        val midY = h / 2f

        // Subtle horizontal dB grid lines
        val gridColor = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        for (step in listOf(0.18f, 0.5f, 0.82f)) {
            drawLine(gridColor, Offset(0f, h * step), Offset(w, h * step), strokeWidth = 1.5f)
        }

        val points = safeBands.mapIndexed { idx, db ->
            val x = padX + (usableW * idx / 4f)
            val norm = (db.coerceIn(-12f, 12f) / 12f)
            val y = midY - (norm * (h * 0.38f))
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        val lineBrush = if (enabled) {
            Brush.horizontalGradient(listOf(SonoraPalette.Cyan, SonoraPalette.Violet, SonoraPalette.Mint))
        } else {
            Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(
                    (if (enabled) SonoraPalette.Cyan else Color.Gray).copy(alpha = 0.28f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = curvePath,
            brush = lineBrush,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(
                color = if (enabled) SonoraPalette.Cyan else Color.Gray,
                radius = 9f,
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = pt
            )
        }
    }
}
