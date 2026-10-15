package io.github.issisx.alienflow

/** Read-only local insertion anchors, never a whole document or another field. */
internal data class WritingContext(val before: String = "", val after: String = "") {
    fun bounded() = WritingContext(before.takeLast(512), after.take(256))
    val empty: Boolean get() = before.isEmpty() && after.isEmpty()
}
