package dev.empyreal.client.core.model

sealed class Setting {
    abstract val id: String
    abstract val title: String
    abstract val description: String

    data class Toggle(
        override val id: String,
        override val title: String,
        override val description: String = "",
        val default: Boolean = false,
    ) : Setting()

    data class Slider(
        override val id: String,
        override val title: String,
        override val description: String = "",
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val default: Float,
        val unit: String = "",
        val decimals: Int = 0,
    ) : Setting()

    data class Choice(
        override val id: String,
        override val title: String,
        override val description: String = "",
        val options: List<String>,
        val default: String,
    ) : Setting()

    data class Keybind(
        override val id: String,
        override val title: String,
        override val description: String = "",
        val default: String = "NONE",
    ) : Setting()

    data class Color(
        override val id: String,
        override val title: String,
        override val description: String = "",
        val default: Long = 0xFF8CD6A9,
        val options: List<Long> = emptyList(),
    ) : Setting()
}

sealed class SettingValue {
    data class Bool(val value: kotlin.Boolean) : SettingValue()
    data class Float(val value: kotlin.Float) : SettingValue()
    data class String(val value: kotlin.String) : SettingValue()
    data class Color(val value: kotlin.Long) : SettingValue()
}
