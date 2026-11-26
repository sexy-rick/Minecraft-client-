package dev.empyreal.client.core.registry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import dev.empyreal.client.core.model.Implementation
import dev.empyreal.client.core.model.Module
import dev.empyreal.client.core.model.ModuleCategory
import dev.empyreal.client.core.model.RiskLevel
import dev.empyreal.client.core.model.Setting

object ModuleRegistry {

    val all: List<Module> = listOf(

        Module(
            id = "autoclicker",
            name = "AutoClicker",
            description = "Taps a point on the screen for you with human-like timing. Dispatched inside the game, so it works with no extra permission.",
            category = ModuleCategory.COMBAT,
            icon = Icons.Rounded.TouchApp,
            implementation = Implementation.NATIVE_CORE,
            risk = RiskLevel.LOW,
            tags = listOf("cps", "legit"),
            hudLabel = false,
            settings = listOf(
                Setting.Slider("cps_min", "Min CPS", "Lowest clicks per second.", 1f, 25f, 1f, 8f, " CPS"),
                Setting.Slider("cps_max", "Max CPS", "Highest clicks per second.", 1f, 30f, 1f, 12f, " CPS"),
                Setting.Slider("tap_x", "Tap X", "Horizontal tap position, as a percentage of screen width.", 0f, 100f, 1f, 50f, "%"),
                Setting.Slider("tap_y", "Tap Y", "Vertical tap position, as a percentage of screen height.", 0f, 100f, 1f, 72f, "%"),
                Setting.Toggle("jitter", "Jitter", "Randomise the position by a few pixels so the tap is not robotic.", true),
                Setting.Toggle("hold", "Hold to run", "Only click while you hold the screen.", false),
            ),
        ),

        Module(
            id = "crosshair",
            name = "Crosshair",
            description = "A clean crosshair drawn in the middle of the game.",
            category = ModuleCategory.RENDER,
            icon = Icons.Rounded.CenterFocusStrong,
            implementation = Implementation.NATIVE_CORE,
            defaultEnabled = true,
            hudLabel = true,
            settings = listOf(
                Setting.Slider("scale", "Size", "Crosshair size.", 0.6f, 2.5f, 0.1f, 1f, "×"),
            ),
        ),
        Module(
            id = "fps",
            name = "FPS",
            description = "Frames per second, measured from inside the game's own render loop.",
            category = ModuleCategory.RENDER,
            icon = Icons.Rounded.Speed,
            implementation = Implementation.NATIVE_CORE,
            defaultEnabled = true,
            hudLabel = true,
        ),
        Module(
            id = "cps",
            name = "CPS",
            description = "Your clicks per second, counted from the touches you actually make.",
            category = ModuleCategory.RENDER,
            icon = Icons.Rounded.TouchApp,
            implementation = Implementation.NATIVE_CORE,
            defaultEnabled = true,
            hudLabel = true,
        ),
        Module(
            id = "clock",
            name = "Clock",
            description = "The current time, always visible.",
            category = ModuleCategory.RENDER,
            icon = Icons.Rounded.Schedule,
            implementation = Implementation.NATIVE_CORE,
            hudLabel = true,
        ),
        Module(
            id = "coords",
            name = "Coordinates",
            description = "Your X, Y and Z position, read from the game's memory. Calibrate the offsets for your version first.",
            category = ModuleCategory.RENDER,
            icon = Icons.Rounded.Visibility,
            implementation = Implementation.NATIVE_CORE,
            hudLabel = true,
            settings = listOf(
                Setting.Slider("decimals", "Decimals", "Digits after the decimal point.", 0f, 3f, 1f, 1f, ""),
                Setting.Toggle("show_axis", "Axis names", "Prefix each value with X, Y, Z.", true),
            ),
        ),

        Module(
            id = "speedrun",
            name = "Speedrun Timer",
            description = "A speedrun timer with splits, drawn in-game. It only ever reads the clock — it never touches the game's time.",
            category = ModuleCategory.UTILITY,
            icon = Icons.Rounded.Timer,
            implementation = Implementation.NATIVE_CORE,
            defaultEnabled = true,
            hudLabel = true,
            tags = listOf("timer", "utility", "legit"),
            controls = true,
            settings = listOf(
                Setting.Toggle("auto_start", "Auto start", "Start the timer as soon as the game comes to the front.", true),
                Setting.Toggle("tenths", "Tenths", "Show tenths of a second.", true),
            ),
        ),
    )

    fun byId(id: String): Module? = all.firstOrNull { it.id == id }

    fun byCategory(category: ModuleCategory): List<Module> = all.filter { it.category == category }

    val categories: List<ModuleCategory> = ModuleCategory.entries.toList()

    fun search(query: String): List<Module> {
        if (query.isBlank()) return all
        val q = query.trim().lowercase()
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.tags.any { t -> t.contains(q) } ||
                it.category.label.lowercase().contains(q)
        }
    }
}
