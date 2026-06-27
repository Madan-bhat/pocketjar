package com.mchost.data

import com.mchost.runtime.LogBus
import com.mchost.util.EssentialLogFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class LogRepository(scope: CoroutineScope) {
    private val maxEntries = 500
    private val nextId = AtomicLong(0)
    private val ring = ArrayDeque<LogEntry>(maxEntries + 1)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _verbose = MutableStateFlow(false)
    val verbose: StateFlow<Boolean> = _verbose.asStateFlow()

    fun setVerbose(enabled: Boolean) {
        if (_verbose.value == enabled) return
        _verbose.value = enabled
        publish()
    }

    init {
        scope.launch(Dispatchers.Default) {
            LogBus.lines.collect { line -> appendLine(line) }
        }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(200)
                publish()
            }
        }
    }

    private fun appendLine(line: String) {
        val level = when {
            line.contains("[ERROR]", ignoreCase = true) -> LogLevel.ERROR
            line.contains("[WARN]", ignoreCase = true) -> LogLevel.WARN
            line.contains("[DEBUG]", ignoreCase = true) -> LogLevel.DEBUG
            else -> LogLevel.INFO
        }
        synchronized(ring) {
            if (ring.size >= maxEntries) ring.removeFirst()
            ring.addLast(LogEntry(id = nextId.incrementAndGet(), level = level, message = line))
        }
    }

    private fun publish() {
        val snapshot = synchronized(ring) { ring.toList() }
        val visible = if (_verbose.value) {
            snapshot
        } else {
            snapshot.filter { EssentialLogFilter.isEssential(it.message, it.level) }
        }
        if (visible != _entries.value) {
            _entries.value = visible
        }
    }

    fun addEntry(entry: LogEntry) {
        synchronized(ring) {
            if (ring.size >= maxEntries) ring.removeFirst()
            ring.addLast(entry.copy(id = nextId.incrementAndGet()))
        }
        publish()
    }

    fun clear() {
        synchronized(ring) { ring.clear() }
        nextId.set(0)
        _entries.value = emptyList()
    }

    fun filtered(level: LogLevel?): List<LogEntry> {
        val snapshot = _entries.value
        return if (level == null) snapshot else snapshot.filter { it.level == level }
    }
}
