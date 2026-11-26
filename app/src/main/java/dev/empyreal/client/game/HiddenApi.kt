package dev.empyreal.client.game

import android.os.Build
import android.util.Log

object HiddenApi {
    private const val TAG = "EmpyrealGame"

    @Volatile
    private var done = false

    fun exempt() {
        if (done) return
        done = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L")
        }.onFailure { Log.w(TAG, "hidden API exemptions not applied: ${it.message}") }
    }
}
