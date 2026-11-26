package dev.empyreal.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.state.HudPlacement
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.theme.LocalSpacing

private data class HudElement(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val fixed: Boolean = false,
)

private val hudElements = listOf(
    HudElement("crosshair", "Crosshair", Icons.Rounded.GpsFixed, fixed = true),
    HudElement("fps", "FPS", Icons.Rounded.Timeline),
    HudElement("cps", "CPS", Icons.Rounded.TouchApp),
    HudElement("clock", "Clock", Icons.Rounded.Schedule),
    HudElement("coords", "Coordinates", Icons.Rounded.GpsFixed),
    HudElement("speedrun", "Speedrun", Icons.Rounded.Timer),
)

@Composable
fun HudScreen() {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val store = EmpyrealApp.instance.hudLayout
    val layout by store.layout.collectAsState()
    var selected by remember { mutableStateOf("fps") }
    var previewPx by remember { mutableIntStateOf(1) }
    var previewPy by remember { mutableIntStateOf(1) }

    val placement = layout[selected] ?: HudPlacement()
    val selectedElement = hudElements.firstOrNull { it.id == selected }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.lg, end = spacing.lg, top = spacing.xl, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "HUD editor",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Drag the widgets. The layout is saved into your config.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Surface(
                onClick = { store.resetAll() },
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reset all",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0B0F0C))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
                .onSizeChanged {
                    previewPx = it.width
                    previewPy = it.height
                },
        ) {
            val previewWidth = maxWidth
            val previewHeight = maxHeight

            Column(Modifier.fillMaxSize()) {
                repeat(3) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(0.5.dp, Color(0x22FFFFFF)),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = previewHeight / 2)
                    .background(Color(0x22FFFFFF)),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .offset(x = previewWidth / 2)
                    .background(Color(0x22FFFFFF)),
            )

            hudElements.forEach { element ->
                val p = layout[element.id] ?: HudPlacement()
                if (!p.visible) return@forEach
                val isSelected = selected == element.id

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = p.x * previewPx - size.width / 2f
                            translationY = p.y * previewPy - size.height / 2f
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0x338CD6A9) else Color(0x22000000))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF8CD6A9) else Color(0x55FFFFFF),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .pointerInput(element.id, element.fixed) {
                            if (element.fixed) return@pointerInput
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val w = previewPx.toFloat()
                                val h = previewPy.toFloat()
                                if (w > 0f && h > 0f) {
                                    val current = store.placement(element.id)
                                    store.update(
                                        element.id,
                                        current.copy(
                                            x = (current.x + dragAmount.x / w).coerceIn(0.02f, 0.98f),
                                            y = (current.y + dragAmount.y / h).coerceIn(0.02f, 0.98f),
                                        ),
                                    )
                                }
                            }
                        }
                        .clickable(enabled = !element.fixed) { selected = element.id }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            element.icon,
                            contentDescription = element.label,
                            tint = if (isSelected) Color(0xFF8CD6A9) else Color(0xFFB9C4BC),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = element.label,
                            color = if (isSelected) Color(0xFFEAF6EE) else Color(0xFFB9C4BC),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Text(
            text = "Preview is 16:9, exactly like the game. Drag a widget to move it.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )

        ExpressiveCard {
            Column {
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(spacing.sm))
                hudElements.forEach { element ->
                    val p = layout[element.id] ?: HudPlacement()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { selected = element.id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected == element.id) colors.primaryContainer else Color.Transparent,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    element.icon,
                                    contentDescription = null,
                                    tint = if (selected == element.id) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = element.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected == element.id) colors.onPrimaryContainer else colors.onSurface,
                                    fontWeight = if (selected == element.id) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (element.fixed) {
                            Text(
                                text = "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        } else {
                            Switch(
                                checked = p.visible,
                                onCheckedChange = { store.update(element.id, p.copy(visible = it)) },
                                modifier = Modifier.size(width = 46.dp, height = 26.dp),
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = colors.primary,
                                    checkedThumbColor = colors.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (selectedElement != null) {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Scale",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", placement.scale) + "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = placement.scale,
                        onValueChange = { store.update(selected, placement.copy(scale = it)) },
                        valueRange = 0.6f..2.2f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                        ),
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Surface(
                        onClick = { store.reset(selected) },
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Reset ${selectedElement.label.lowercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
