package io.github.issisx.alienflow

import java.io.InputStream

/** InputStream.readNBytes(int) needs Android 13; diagnostics also run on Android 11 and 12. */
internal object BoundedRead {
    fun bytes(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0) { "Negative read limit $limit" }
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val count = input.read(buffer, total, limit - total)
            if (count < 0) break
            total += count
        }
        return buffer.copyOf(total)
    }
}
