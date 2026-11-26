package dev.empyreal.client.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

enum class ModuleCategory(val label: String, val icon: ImageVector) {
    COMBAT("Combat", Icons.Rounded.Bolt),
    MOVEMENT("Movement", Icons.Rounded.DirectionsRun),
    PLAYER("Player", Icons.Rounded.Person),
    RENDER("Render", Icons.Rounded.Visibility),
    WORLD("World", Icons.Rounded.Public),
    UTILITY("Utility", Icons.Rounded.Build),
}

enum class Implementation {

    ANDROID,

    NATIVE_CORE,
}

enum class RiskLevel(val label: String) {
    NONE("Safe"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}
