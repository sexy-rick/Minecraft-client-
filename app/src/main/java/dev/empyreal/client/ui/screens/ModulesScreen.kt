package dev.empyreal.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.model.Implementation
import dev.empyreal.client.core.model.Module
import dev.empyreal.client.core.model.ModuleCategory
import dev.empyreal.client.core.registry.ModuleRegistry
import dev.empyreal.client.ui.components.ImplementationBadge
import dev.empyreal.client.ui.theme.LocalSpacing

@Composable
fun ModulesScreen(onOpenModule: (String) -> Unit) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val modules = EmpyrealApp.instance.modules
    val enabled by modules.enabled.collectAsState()
    val core by CoreBridge.state.collectAsState()
    val favorites by EmpyrealApp.instance.favorites.ids.collectAsState()
    val favoriteStore = EmpyrealApp.instance.favorites

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(TAB_ALL) }

    val filtered = remember(query, category, tab, enabled, favorites) {
        val base = ModuleRegistry.search(query).let { list ->
            when (tab) {
                TAB_FAVORITES -> list.filter { it.id in favorites }
                TAB_ENABLED -> list.filter { enabled[it.id] == true }
                else -> list
            }
        }
        if (category == null) base else base.filter { it.category.name == category }
    }
    val grouped = remember(filtered) {
        ModuleCategory.entries.map { cat -> cat to filtered.filter { it.category == cat } }
            .filter { it.second.isNotEmpty() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.xl,
                bottom = spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item {
                Column {
                    Text(
                        text = "Modules",
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.onBackground,
                    )
                    Text(
                        text = "${ModuleRegistry.all.size} modules · ${enabled.count { it.value }} active · ${favorites.size} starred",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    contentPadding = PaddingValues(vertical = spacing.xs),
                ) {
                    item {
                        CategoryChip(
                            label = "All",
                            selected = tab == TAB_ALL,
                            icon = Icons.Rounded.Dashboard,
                            onClick = { tab = TAB_ALL },
                        )
                    }
                    item {
                        CategoryChip(
                            label = "Favorites",
                            selected = tab == TAB_FAVORITES,
                            icon = Icons.Rounded.Star,
                            onClick = { tab = if (tab == TAB_FAVORITES) TAB_ALL else TAB_FAVORITES },
                        )
                    }
                    item {
                        CategoryChip(
                            label = "Enabled",
                            selected = tab == TAB_ENABLED,
                            icon = Icons.Rounded.Check,
                            onClick = { tab = if (tab == TAB_ENABLED) TAB_ALL else TAB_ENABLED },
                        )
                    }
                }
            }

            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    placeholder = { Text("Search modules") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    contentPadding = PaddingValues(vertical = spacing.xs),
                ) {
                    item {
                        CategoryChip(
                            label = "All",
                            selected = category == null,
                            icon = null,
                            onClick = { category = null },
                        )
                    }
                    items(ModuleCategory.entries) { cat ->
                        CategoryChip(
                            label = cat.label,
                            selected = category == cat.name,
                            icon = cat.icon,
                            onClick = { category = if (category == cat.name) null else cat.name },
                        )
                    }
                }
            }

            grouped.forEach { (cat, list) ->
                item(key = "header_${cat.name}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.md, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(cat.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${list.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }) { module ->
                    ModuleRow(
                        module = module,
                        checked = enabled[module.id] == true,
                        favorite = module.id in favorites,
                        onFavorite = { favoriteStore.toggle(module.id) },
                        onToggle = { modules.setEnabled(module.id, it) },
                        onClick = { onOpenModule(module.id) },
                        coreAttached = core.attached,
                    )
                }
            }
        }

    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(100.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerHigh,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
        ),
        border = null,
    )
}

private const val TAB_ALL = "all"
private const val TAB_FAVORITES = "fav"
private const val TAB_ENABLED = "on"

@Composable
private fun ModuleRow(
    module: Module,
    checked: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    coreAttached: Boolean = false,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val pendingLaunch = module.implementation == Implementation.NATIVE_CORE && !coreAttached && checked

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (checked) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (checked) colors.primaryContainer else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    module.icon,
                    contentDescription = null,
                    tint = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    if (pendingLaunch) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = "Ready for next launch",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (module.implementation == Implementation.NATIVE_CORE && checked) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (coreAttached) "Active in-game" else "Ready for next launch",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (coreAttached) colors.primary else colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImplementationBadge(module.implementation)
                    if (module.risk != dev.empyreal.client.core.model.RiskLevel.NONE) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.errorContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = module.risk.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(spacing.xs))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                    uncheckedTrackColor = colors.surfaceContainerHighest,
                    uncheckedThumbColor = colors.outline,
                    uncheckedBorderColor = colors.outline,
                ),
            )
        }
    }
}
