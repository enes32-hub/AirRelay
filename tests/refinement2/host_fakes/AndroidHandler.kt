package android.os

/** Deterministic virtual clock. No Android Looper, real timers or background execution. */
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    fun postDelayed(runnable: Runnable, delay: Long): Boolean { tasks[runnable] = now + delay; return true }
    fun removeCallbacks(runnable: Runnable) { tasks.remove(runnable) }
    companion object {
        private var now = 0L
        private val tasks = linkedMapOf<Runnable, Long>()
        val pending: Int get() = tasks.size
        fun reset() { now = 0L; tasks.clear() }
        fun advanceBy(delta: Long) {
            val until = now + delta
            while (true) {
                val next = tasks.entries.filter { it.value <= until }.minByOrNull { it.value } ?: break
                now = next.value; val runnable = next.key; tasks.remove(runnable); runnable.run()
            }
            now = until
        }
    }
}
