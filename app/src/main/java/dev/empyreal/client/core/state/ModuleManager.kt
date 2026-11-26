package dev.empyreal.client.core.state

import android.content.Context
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.model.Module
import dev.empyreal.client.core.model.Setting
import dev.empyreal.client.core.model.SettingValue
import dev.empyreal.client.core.registry.ModuleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModuleManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("empyreal_modules", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(loadEnabled())
    val enabled: StateFlow<Map<String, Boolean>> = _enabled.asStateFlow()

    private val _values = MutableStateFlow(loadValues())
    val values: StateFlow<Map<String, Map<String, SettingValue>>> = _values.asStateFlow()

    private val _raw = MutableStateFlow(loadRaw())
    val raw: StateFlow<Map<String, Map<String, String>>> = _raw.asStateFlow()

    fun isEnabled(moduleId: String): Boolean = _enabled.value[moduleId] ?: false

    fun enabledModules(): List<Module> =
        ModuleRegistry.all.filter { isEnabled(it.id) }

    fun value(moduleId: String, settingId: String): SettingValue {
        val module = ModuleRegistry.byId(moduleId) ?: return SettingValue.Bool(false)
        val setting = module.settings.firstOrNull { it.id == settingId } ?: return SettingValue.Bool(false)
        return _values.value[moduleId]?.get(settingId) ?: defaultOf(setting)
    }

    fun bool(moduleId: String, settingId: String): Boolean = (value(moduleId, settingId) as? SettingValue.Bool)?.value ?: false
    fun float(moduleId: String, settingId: String): Float = (value(moduleId, settingId) as? SettingValue.Float)?.value ?: 0f
    fun string(moduleId: String, settingId: String): String =
        (value(moduleId, settingId) as? SettingValue.String)?.value ?: ""
    fun color(moduleId: String, settingId: String): Long =
        (value(moduleId, settingId) as? SettingValue.Color)?.value ?: 0xFF8CD6A9

    fun toggle(moduleId: String) = setEnabled(moduleId, !isEnabled(moduleId))

    fun setEnabled(moduleId: String, on: Boolean) {
        _enabled.update { map -> map + (moduleId to on) }
        prefs.edit().putBoolean("e:$moduleId", on).apply()
        syncNow()
    }

    fun set(moduleId: String, settingId: String, value: SettingValue) {
        _values.update { map ->
            val inner = map[moduleId].orEmpty() + (settingId to value)
            map + (moduleId to inner)
        }
        val editor = prefs.edit()
        val key = "v:$moduleId:$settingId"
        when (value) {
            is SettingValue.Bool -> editor.putBoolean(key, value.value)
            is SettingValue.Float -> editor.putFloat(key, value.value)
            is SettingValue.String -> editor.putString(key, value.value)
            is SettingValue.Color -> editor.putLong(key, value.value)
        }
        editor.apply()
        syncNow()
    }

    fun reset(moduleId: String) {
        val module = ModuleRegistry.byId(moduleId) ?: return
        val defaults = module.settings.associate { s -> s.id to defaultOf(s) }
        _values.update { map -> map + (moduleId to defaults) }
        val editor = prefs.edit()
        module.settings.forEach { s -> editor.remove("v:$moduleId:${s.id}") }
        editor.apply()
        syncNow()
    }

    fun resetAll() {
        val defaultEnabled = ModuleRegistry.all.associate { it.id to it.defaultEnabled }
        val defaultValues = ModuleRegistry.all.associate { m ->
            m.id to m.settings.associate { s -> s.id to defaultOf(s) }
        }
        _enabled.value = defaultEnabled
        _values.value = defaultValues
        _raw.value = emptyMap()
        prefs.edit().clear().apply()
        syncNow()
    }

    fun syncNow() {
        pushToCore()
        HudSync.pushNow()
    }

    fun raw(moduleId: String, key: String): String? =
        _raw.value[moduleId]?.get(key) ?: prefs.getString("r:$moduleId:$key", null)

    fun setRaw(moduleId: String, settings: Map<String, String>) {
        persistRaw(moduleId, settings)
        CoreBridge.pushSettings(moduleId, rawSettings(moduleId))
    }

    private fun rawSettings(moduleId: String): Map<String, String> {
        val module = ModuleRegistry.byId(moduleId)
        val typed = module?.settings?.associate { s ->
            s.id to when (val v = value(moduleId, s.id)) {
                is SettingValue.Bool -> v.value.toString()
                is SettingValue.Float -> v.value.toString()
                is SettingValue.String -> v.value
                is SettingValue.Color -> v.value.toString()
            }
        }.orEmpty()
        return typed + _raw.value[moduleId].orEmpty()
    }

    private fun persistRaw(moduleId: String, settings: Map<String, String>) {
        _raw.update { map -> map + (moduleId to (map[moduleId].orEmpty() + settings)) }
        val editor = prefs.edit()
        settings.forEach { (key, value) -> editor.putString("r:$moduleId:$key", value) }
        editor.apply()
    }

    fun pushToCore() {
        val fullEnabled = ModuleRegistry.all.associate { m -> m.id to isEnabled(m.id) }
        val serializedValues = ModuleRegistry.all.associate { m -> m.id to rawSettings(m.id) }
        CoreBridge.sync(fullEnabled, serializedValues)
    }

    fun snapshot(): Snapshot = Snapshot(
        enabled = ModuleRegistry.all.associate { it.id to isEnabled(it.id) },
        values = ModuleRegistry.all.associate { m ->
            m.id to m.settings.associate { s -> s.id to value(m.id, s.id) }
        },
    )

    fun applySnapshot(snapshot: Snapshot) {
        val fullEnabled = ModuleRegistry.all.associate { m ->
            m.id to (snapshot.enabled[m.id] ?: m.defaultEnabled)
        }
        val fullValues = ModuleRegistry.all.associate { m ->
            m.id to m.settings.associate { s ->
                s.id to (snapshot.values[m.id]?.get(s.id) ?: defaultOf(s))
            }
        }
        _enabled.value = fullEnabled
        _values.value = fullValues

        val editor = prefs.edit().clear()
        fullEnabled.forEach { (id, on) -> editor.putBoolean("e:$id", on) }
        fullValues.forEach { (moduleId, inner) ->
            inner.forEach { (settingId, v) ->
                val key = "v:$moduleId:$settingId"
                when (v) {
                    is SettingValue.Bool -> editor.putBoolean(key, v.value)
                    is SettingValue.Float -> editor.putFloat(key, v.value)
                    is SettingValue.String -> editor.putString(key, v.value)
                    is SettingValue.Color -> editor.putLong(key, v.value)
                }
            }
        }
        // Calibration values are not part of a config, so keep them across a restore.
        _raw.value.forEach { (moduleId, inner) ->
            inner.forEach { (key, value) -> editor.putString("r:$moduleId:$key", value) }
        }
        editor.apply()
        syncNow()
    }

    data class Snapshot(
        val enabled: Map<String, Boolean>,
        val values: Map<String, Map<String, SettingValue>>,
    )

    private fun loadEnabled(): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        val raw = prefs.all
        ModuleRegistry.all.forEach { m ->
            val key = "e:${m.id}"
            map[m.id] = (raw[key] as? Boolean) ?: m.defaultEnabled
        }
        return map
    }

    private fun loadRaw(): Map<String, Map<String, String>> {
        val out = mutableMapOf<String, Map<String, String>>()
        prefs.all.forEach { (key, entry) ->
            if (!key.startsWith("r:")) return@forEach
            val rest = key.removePrefix("r:")
            val split = rest.indexOf(':')
            if (split <= 0) return@forEach
            val moduleId = rest.substring(0, split)
            val settingId = rest.substring(split + 1)
            val value = entry as? String ?: return@forEach
            out[moduleId] = out[moduleId].orEmpty() + (settingId to value)
        }
        return out
    }

    private fun loadValues(): Map<String, Map<String, SettingValue>> {
        val out = mutableMapOf<String, Map<String, SettingValue>>()
        val raw = prefs.all
        ModuleRegistry.all.forEach { m ->
            val inner = mutableMapOf<String, SettingValue>()
            m.settings.forEach { s ->
                val key = "v:${m.id}:${s.id}"
                val entry = raw[key]
                val stored = when (s) {
                    is Setting.Toggle -> (entry as? Boolean)?.let { SettingValue.Bool(it) }
                    is Setting.Slider -> (entry as? Number)?.let { SettingValue.Float(it.toFloat()) }
                    is Setting.Choice -> (entry as? String)?.let { SettingValue.String(it) }
                    is Setting.Keybind -> (entry as? String)?.let { SettingValue.String(it) }
                    is Setting.Color -> (entry as? Number)?.let { SettingValue.Color(it.toLong()) }
                }
                if (stored != null) inner[s.id] = stored
            }
            out[m.id] = inner
        }
        return out
    }

    companion object {
        fun defaultOf(setting: Setting): SettingValue = when (setting) {
            is Setting.Toggle -> SettingValue.Bool(setting.default)
            is Setting.Slider -> SettingValue.Float(setting.default)
            is Setting.Choice -> SettingValue.String(setting.default)
            is Setting.Keybind -> SettingValue.String(setting.default)
            is Setting.Color -> SettingValue.Color(setting.default)
        }
    }
}
