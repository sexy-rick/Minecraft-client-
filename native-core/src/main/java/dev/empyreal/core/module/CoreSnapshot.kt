package dev.empyreal.core.module

data class StateFrame(
    val type: String = "state",
    val pid: Int = 0,
    val tick: Long = 0,
    val modules: List<String> = emptyList(),
    val values: Map<String, String> = emptyMap(),
) {
    companion object {
        const val TYPE_STATE = "state"
        const val TYPE_HELLO = "hello"
    }
}
