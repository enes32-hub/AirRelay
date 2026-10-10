package io.github.jqssun.airplay.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Time of receipt, independent of when the IO coroutine next drains the queue. */
internal data class DiagnosticEvent(
    val message: String,
    val wallTimeMillis: Long,
    val monotonicTimeNanos: Long,
) {
    fun format(clock: SimpleDateFormat): String =
        "${clock.format(Date(wallTimeMillis))} [mono_ms=${monotonicTimeNanos / 1_000_000}] $message"
}

/** A short memory-only critical section keeps a batch and its omission count consistent. */
internal class DiagnosticIngressQueue(
    private val capacity: Int = 256,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val monotonicTimeNanos: () -> Long = System::nanoTime,
) {
    init { require(capacity > 0) }
    private val pending = java.util.ArrayDeque<DiagnosticEvent>()
    private var dropped = 0L
    private var firstDropped: DiagnosticEvent? = null

    fun offer(message: String) {
        val wall = wallTimeMillis()
        val monotonic = monotonicTimeNanos()
        // Sensitive text never enters the pending queue or the dropped-event record.
        val event = DiagnosticEvent(LogRedactor.redact(message), wall, monotonic)
        synchronized(this) {
            if (pending.size < capacity) pending.addLast(event)
            else {
                if (dropped < Long.MAX_VALUE) dropped++
                if (firstDropped == null) firstDropped = event.copy(message = "")
            }
        }
    }

    @Synchronized
    fun drain(): List<DiagnosticEvent> {
        val batch = ArrayList<DiagnosticEvent>(pending.size + 1)
        while (pending.isNotEmpty()) batch.add(pending.removeFirst())
        firstDropped?.let {
            batch.add(it.copy(message = "[diagnostics rate limited: $dropped messages omitted]"))
        }
        dropped = 0L
        firstDropped = null
        return batch
    }

    @Synchronized
    fun clear() {
        pending.clear()
        dropped = 0L
        firstDropped = null
    }
}

/** One process-wide sink, bounded at ingress, in memory and on disk. No media-thread disk I/O. */
class DiagnosticStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "airrelay_diagnostics.txt")
    private val previous = File(app.filesDir, "airrelay_diagnostics.previous.txt")
    private val queue = DiagnosticIngressQueue()
    private val lock = Mutex()
    private val buffer = BoundedLogBuffer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()
    private val _storageError = MutableStateFlow(false)
    val storageError = _storageError.asStateFlow()
    init {
        scope.launch { clear() }
        // Drain the bounded ingress queue into the bounded in-memory buffer. Ingest stays cheap
        // and never touches disk; the UI observes the snapshot flow. Without this the reachable
        // Logs screen could never show anything, because events were silently discarded.
        scope.launch {
            while (true) {
                delay(DRAIN_INTERVAL_MS)
                val batch = queue.drain()
                if (batch.isEmpty()) continue
                lock.withLock {
                    batch.forEach { buffer.append(it.format(clock)) }
                    _lines.value = buffer.snapshot()
                }
            }
        }
    }

    /** Redacted, bounded, in-memory only. Never performs disk I/O on the caller's thread. */
    fun offer(message: String) = queue.offer(message)
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            queue.clear()
            buffer.clear()
            _lines.value = emptyList()
            val ok = listOf(file, previous, File(app.cacheDir, "airrelay_support.txt"),
                File(app.filesDir, "airplay_logs.txt"), File(app.cacheDir, "airplay_logs.txt"))
                .map { !it.exists() || it.delete() }.all { it }
            _storageError.value = !ok
        }
    }

    /** Writes the current bounded snapshot into the shared cache directory for FileProvider. */
    suspend fun export(): File = withContext(Dispatchers.IO) {
        lock.withLock {
            val target = File(app.cacheDir, "airrelay_support.txt")
            val bytes = buffer.snapshot().joinToString("\n").toByteArray(Charsets.UTF_8)
            val capped = if (bytes.size > MAX_FILE_BYTES)
                bytes.copyOfRange((bytes.size - MAX_FILE_BYTES).toInt(), bytes.size) else bytes
            target.writeBytes(capped)
            target
        }
    }
    companion object {
        private const val MAX_FILE_BYTES = 262_144L
        private const val DRAIN_INTERVAL_MS = 400L
        @Volatile private var instance: DiagnosticStore? = null
        fun get(context: Context): DiagnosticStore = instance ?: synchronized(this) {
            instance ?: DiagnosticStore(context).also { instance = it }
        }
    }
}
