package com.minion.mockserver.core.data.repository

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.common.dispatcher.ApplicationScope
import com.minion.mockserver.core.domain.model.TrafficEntry
import com.minion.mockserver.core.domain.repository.TrafficRepository
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The in-memory traffic log (SPEC §7.1).
 *
 * Nothing here touches Room. Sustained writes on the request path would add disk I/O to every
 * response, the data has a lifespan of one debugging session, and a phone should not accumulate
 * megabytes of somebody else's request bodies.
 *
 * ### The hand-off, and why it is shaped like this
 *
 * [offer] is called from the request path and must never block or allocate much. It pushes onto a
 * lock-free queue and pokes a conflated channel; a coroutine on `Dispatchers.Default` drains the
 * queue into the ring buffer and publishes once per batch.
 *
 * Overflow drops the **oldest** queued entry, not the newest, and counts it. That combination is
 * why this is a `ConcurrentLinkedQueue` with its own size counter rather than
 * `Channel(onBufferOverflow = DROP_OLDEST)`: the channel would give the right eviction policy but
 * no way to observe how many it evicted, and a log that silently loses entries is a log that
 * quietly lies. The count is surfaced in the UI so the loss is visible instead.
 *
 * Publishing once per drained batch rather than per entry matters at the sustained 50 req/s
 * SPEC §12 targets: per-entry publication would copy a 500-element list fifty times a second.
 */
@Singleton
internal class TrafficRepositoryImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) : TrafficRepository {

    private val _entries = MutableStateFlow<List<TrafficEntry>>(emptyList())

    /** Newest first, which is the order the log renders and the order it is filtered in. */
    override val entries: StateFlow<List<TrafficEntry>> = _entries.asStateFlow()

    private val _droppedCount = MutableStateFlow(0)
    override val droppedCount: StateFlow<Int> = _droppedCount.asStateFlow()

    /** The hand-off queue. Lock-free, so the request path never waits on the drain. */
    private val pending = ConcurrentLinkedQueue<TrafficEntry>()
    private val pendingSize = AtomicInteger(0)

    /** A poke, not a payload. Conflated: one pending wake-up is as good as ten. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Guards the ring buffer, which only the drain and the two admin calls touch. */
    private val bufferMutex = Mutex()
    private val buffer = ArrayDeque<TrafficEntry>()

    @Volatile
    private var capacity = AppConstants.Traffic.DEFAULT_LOG_CAPACITY

    init {
        scope.launch(Dispatchers.Default) {
            for (ignored in wake) {
                drain()
            }
        }
    }

    override fun offer(entry: TrafficEntry) {
        pending.add(entry)
        if (pendingSize.incrementAndGet() > AppConstants.Traffic.LOG_CHANNEL_CAPACITY) {
            // Evict the oldest queued entry rather than refusing the newest: under load the
            // recent requests are the ones being debugged.
            if (pending.poll() != null) {
                pendingSize.decrementAndGet()
                _droppedCount.value = _droppedCount.value + 1
            }
        }
        wake.trySend(Unit)
    }

    override suspend fun clear() {
        bufferMutex.withLock {
            pending.clear()
            pendingSize.set(0)
            buffer.clear()
            _entries.value = emptyList()
            _droppedCount.value = 0
        }
    }

    override suspend fun setCapacity(capacity: Int) {
        val bounded = capacity.coerceIn(
            AppConstants.Traffic.MIN_LOG_CAPACITY,
            AppConstants.Traffic.MAX_LOG_CAPACITY,
        )
        bufferMutex.withLock {
            this.capacity = bounded
            // Shrinking trims immediately rather than waiting for the next request, so the memory
            // the user just asked to give back is given back.
            var changed = false
            while (buffer.size > bounded) {
                buffer.removeLast()
                changed = true
            }
            if (changed) _entries.value = buffer.toList()
        }
    }

    private suspend fun drain() {
        var drained = 0
        bufferMutex.withLock {
            while (true) {
                val entry = pending.poll() ?: break
                pendingSize.decrementAndGet()
                // addFirst, so the buffer is newest-first and the UI needs no reversal.
                buffer.addFirst(entry)
                if (buffer.size > capacity) buffer.removeLast()
                drained++
            }
            if (drained > 0) _entries.value = buffer.toList()
        }
    }
}
