package dev.empyreal.client.core.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class Favorites(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("empyreal_favorites", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun isFavorite(id: String): Boolean = id in _ids.value

    fun toggle(id: String) {
        _ids.update { set -> if (id in set) set - id else set + id }
        prefs.edit().putStringSet(KEY, _ids.value).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
