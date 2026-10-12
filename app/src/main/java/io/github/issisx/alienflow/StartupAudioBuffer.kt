package io.github.issisx.alienflow

/** Owned under the transport lock. Retains startup audio AND turn boundaries in capture order. */
internal class StartupAudioBuffer(
    private val limitBytes: Int = RealtimeProtocol.SAMPLE_RATE * 2 * 15,
) {
    sealed interface Entry {
        data class Audio(val pcm: ByteArray) : Entry
        data object Commit : Entry
    }
    private val entries = ArrayDeque<Entry>()
    var bytes = 0; private set
    private var turnBytes = 0

    fun append(pcm: ByteArray): Boolean {
        if (pcm.isEmpty()) return true
        if (pcm.size > limitBytes - bytes) return false
        entries.addLast(Entry.Audio(pcm.copyOf()))
        bytes += pcm.size
        turnBytes += pcm.size
        return true
    }
    fun commit(): Boolean {
        if (turnBytes == 0) return false
        entries.addLast(Entry.Commit)
        turnBytes = 0
        return true
    }
    /** Consume once; a send failure hands recovery to the complete private WAV, never replay. */
    fun drain(send: (Entry) -> Boolean): Boolean {
        while (entries.isNotEmpty()) {
            val entry = entries.removeFirst()
            if (entry is Entry.Audio) bytes -= entry.pcm.size
            if (!send(entry)) { clear(); return false }
        }
        turnBytes = 0
        return true
    }
    fun clear() { entries.clear(); bytes = 0; turnBytes = 0 }
}
