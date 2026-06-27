package com.mchost.runtime

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object LogBus {
    private const val TAG = "MCHost"

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    fun emit(line: String) {
        _lines.tryEmit(line)
        Log.println(Log.INFO, TAG, line)
    }

    fun emit(level: com.mchost.data.LogLevel, message: String) {
        emit("[${level.name}] $message")
    }
}
