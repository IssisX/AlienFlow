package io.github.issisx.alienflow

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Resolve current input focus. A touched/active window or a merely editable node is never a target. */
internal object EditorFocusDiscovery {
    data class Result(val node: AccessibilityNodeInfo?, val direct: Boolean,
                      val focusedWindow: Boolean, val visited: Int, val candidates: Int, val exhausted: Boolean)
    fun resolve(direct: AccessibilityNodeInfo?, windows: () -> List<AccessibilityWindowInfo>,
                eligible: (AccessibilityNodeInfo) -> Boolean,
                protected: (AccessibilityNodeInfo) -> Boolean): Result {
        if (direct != null && protected(direct)) return Result(null, false, false, 0, 0, false)
        if (direct != null && eligible(direct)) return Result(direct, true, false, 0, 1, false)
        val inputWindow = windows().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }.singleOrNull()
            ?: return Result(null, false, false, 0, 0, false)
        val root = inputWindow.root ?: return Result(null, false, true, 0, 0, false)
        // Some virtual providers do not answer findFocus until their semantics nodes
        // have been materialized. Inspect only the input-focused application window.
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val seen = HashSet<AccessibilityNodeInfo>()
        var candidate: AccessibilityNodeInfo? = null
        var candidates = 0
        var visited = 0
        while (pending.isNotEmpty() && visited < 256) {
            val node = pending.removeFirst()
            if (!seen.add(node)) continue
            visited++
            if (node.isFocused && protected(node)) return Result(null, false, true, visited, 0, false)
            if (node.isFocused && node.isVisibleToUser && node.isEnabled && eligible(node)) {
                candidates++
                if (candidates > 1) return Result(null, false, true, visited, candidates, false)
                candidate = node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { pending.addLast(it) }
        }
        val exhausted = pending.isNotEmpty()
        return Result(if (exhausted) null else candidate, false, true, visited, candidates, exhausted)
    }
}
