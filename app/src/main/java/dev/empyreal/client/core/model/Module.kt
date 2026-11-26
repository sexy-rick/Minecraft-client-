package dev.empyreal.client.core.model

import androidx.compose.ui.graphics.vector.ImageVector

data class Module(
    val id: String,
    val name: String,
    val description: String,
    val category: ModuleCategory,
    val icon: ImageVector,
    val settings: List<Setting> = emptyList(),
    val implementation: Implementation = Implementation.ANDROID,
    val risk: RiskLevel = RiskLevel.NONE,
    val tags: List<String> = emptyList(),

    val hudLabel: Boolean = false,
    val defaultEnabled: Boolean = false,

    val controls: Boolean = false,
) {
    val isAvailable: Boolean
        get() = implementation == Implementation.ANDROID
}
