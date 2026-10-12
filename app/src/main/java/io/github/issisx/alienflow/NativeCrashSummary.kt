package io.github.issisx.alienflow

/** Reads only signal and crash-thread code locations from Android's tombstone.proto.
 * Never returns memory dumps, command lines, file descriptors, log buffers or abort messages.
 * Schema: platform/system/core/debuggerd/proto/tombstone.proto (AOSP).
 */
object NativeCrashSummary {
    const val MAX_BYTES = 4 * 1024 * 1024
    private data class Field(val id: Int, val number: Long = 0, val start: Int = -1, val end: Int = -1)
    private class Reader(val data: ByteArray) {
        fun fields(start: Int = 0, end: Int = data.size): List<Field> {
            var p = start
            fun variable(): Long {
                var result = 0L
                for (i in 0..9) {
                    require(p < end) { "truncated" }
                    val b = data[p++].toInt() and 255
                    require(i < 9 || b <= 1) { "overflow" }
                    result = result or ((b and 127).toLong() shl (i * 7))
                    if (b and 128 == 0) return result
                }
                error("varint")
            }
            val out = ArrayList<Field>()
            while (p < end) {
                require(out.size < 20000) { "field limit" }
                val key = variable(); val id = (key ushr 3).toInt()
                require(id > 0) { "field zero" }
                when ((key and 7).toInt()) {
                    0 -> out.add(Field(id, variable()))
                    1 -> { require(end - p >= 8); p += 8 }
                    2 -> {
                        val count = variable()
                        require(count >= 0 && count <= end - p)
                        val finish = p + count.toInt()
                        out.add(Field(id, start = p, end = finish)); p = finish
                    }
                    5 -> { require(end - p >= 4); p += 4 }
                    else -> error("unsupported wire")
                }
            }
            return out
        }
        fun nested(field: Field?) = if (field != null && field.start >= 0) fields(field.start, field.end) else emptyList()
        fun text(field: Field?): String = if (field == null || field.start < 0) "" else
            String(data, field.start, minOf(field.end - field.start, 512), Charsets.UTF_8)
    }
    private fun List<Field>.one(id: Int) = firstOrNull { it.id == id }
    private fun symbol(value: String) = value.take(160).replace(Regex("[^A-Za-z0-9_.:$<>~,* ()&+!/-]"), "_")
    fun render(bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) return "Native trace exceeds the 4 MiB inspection limit; not included."
        return try {
            val reader = Reader(bytes)
            val root = reader.fields()
            val tid = root.one(6)?.number ?: 0L
            val signal = reader.nested(root.one(10))
            val crashThread = root.filter { it.id == 16 }.asSequence().map { reader.nested(it) }
                .firstOrNull { it.one(1)?.number == tid && tid > 0 }
            val thread = reader.nested(crashThread?.one(2))
            val frames = thread.filter { it.id == 4 }.take(24)
            buildString {
                appendLine("Native signal=${signal.one(1)?.number ?: 0} code=${signal.one(3)?.number ?: 0}")
                if (frames.isEmpty()) appendLine("No crashing-thread code frames available.")
                frames.forEachIndexed { index, frame ->
                    val f = reader.nested(frame)
                    val file = reader.text(f.one(6)).substringAfterLast('/').substringAfterLast('\\')
                    val pc = java.lang.Long.toUnsignedString(f.one(1)?.number ?: 0L, 16)
                    val build = reader.text(f.one(8)).takeIf { it.matches(Regex("[a-fA-F0-9]{1,80}")) }.orEmpty()
                    appendLine("#$index pc=$pc ${symbol(file)} ${symbol(reader.text(f.one(4)))} build=$build")
                }
                appendLine("Raw memory, log buffers and abort-message content excluded.")
            }
        } catch (_: Exception) { "Native trace was present but its metadata could not be decoded; raw trace not included." }
    }
}
