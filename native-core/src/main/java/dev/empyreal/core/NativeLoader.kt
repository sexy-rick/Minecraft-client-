package dev.empyreal.core

import android.util.Log

object NativeLoader {

    private const val LIB_NAME = "empyreal_core"
    private const val TAG = "EmpyrealCore"

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun load() {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            System.loadLibrary(LIB_NAME)
            isLoaded = true
            Log.i(TAG, "native library loaded")
        }
    }
}
