package com.gnsh.batterystats.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.roundToInt

/** A shape that morphs between two normalized MaterialShapes polygons. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

enum class HeroMood { Charging, Discharging, Low }

/**
 * The big battery level: a slowly spinning, shape-morphing container
 * holding a wavy ring that ripples while charging.
 */
@Composable
fun BatteryHero(level: Int, mood: HeroMood, modifier: Modifier = Modifier, size: Dp = 288.dp) {
    val charging = mood == HeroMood.Charging
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(if (charging) 1f else 0f, motion.slowSpatialSpec(), label = "morph")
    val container by animateColorAsState(
        when (mood) {
            HeroMood.Charging -> colors.primaryContainer
            HeroMood.Discharging -> colors.secondaryContainer
            HeroMood.Low -> colors.errorContainer
        }, motion.slowEffectsSpec(), label = "container",
    )
    val onContainer by animateColorAsState(
        when (mood) {
            HeroMood.Charging -> colors.onPrimaryContainer
            HeroMood.Discharging -> colors.onSecondaryContainer
            HeroMood.Low -> colors.onErrorContainer
        }, motion.slowEffectsSpec(), label = "onContainer",
    )
    val ring by animateColorAsState(
        when (mood) {
            HeroMood.Charging -> colors.primary
            HeroMood.Discharging -> colors.secondary
            HeroMood.Low -> colors.error
        }, motion.slowEffectsSpec(), label = "ring",
    )
    val shownLevel by animateFloatAsState(level / 100f, motion.slowSpatialSpec(), label = "level")
    val amplitude by animateFloatAsState(if (charging) 1f else 0f, motion.slowEffectsSpec(), label = "amp")

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(charging) {
        if (charging) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(24_000, easing = LinearEasing))
        } else {
            // Settle on the nearest symmetric angle of the 12-sided cookie.
            val target = (rotation.value / 30f).roundToInt() * 30f
            rotation.animateTo(target, motion.slowSpatialSpec())
        }
    }

    val stroke = with(LocalDensity.current) { Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value }
                .clip(MorphShape(morph, morphProgress))
                .background(container),
        )
        CircularWavyProgressIndicator(
            progress = { shownLevel },
            modifier = Modifier.size(size * 0.72f),
            color = ring,
            trackColor = onContainer.copy(alpha = 0.14f),
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { amplitude },
            wavelength = 36.dp,
            waveSpeed = 18.dp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$level",
                style = MaterialTheme.typography.displayLargeEmphasized,
                color = onContainer,
            )
            Text(
                "%",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = onContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
            )
        }
    }
}

/** An icon sitting in an expressive MaterialShapes container. */
@Composable
fun ShapedIcon(
    icon: ImageVector,
    polygon: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** A compact metric card: shaped icon, value, label. */
@Composable
fun StatTile(
    icon: ImageVector,
    polygon: RoundedPolygon,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    accent: Color = MaterialTheme.colorScheme.tertiaryContainer,
    onAccent: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    caption: String? = null,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = container) {
        Column(Modifier.padding(16.dp)) {
            ShapedIcon(icon, polygon, accent, onAccent)
            Spacer(Modifier.height(14.dp))
            Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Friendly empty state with a big expressive shape. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapedIcon(
            icon, MaterialShapes.Flower,
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
            size = 112.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
