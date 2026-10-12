package io.github.issisx.alienflow

/** Termux consumes commits as terminal input and immediately clears its IME buffer.
 * It cannot corroborate or revise a document span. Send the finished text once. */
object TerminalDictation {
    fun supported(className: String?, nodePackage: String?, editorPackage: String?, inputType: Int): Boolean =
        !nodePackage.isNullOrBlank() && nodePackage == editorPackage &&
            (className == "com.termux.view.TerminalView" ||
                (nodePackage == "com.termux" && className == "android.view.View" && inputType and 15 == 0))

    // In a terminal, newline/control characters are actions, not document formatting.
    // Preserve the words and punctuation, but never send Enter, Tab, Escape or Ctrl keys.
    fun text(value: String): String = value.map {
        if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it
    }.joinToString("")
}

class TerminalInsertion {
    var sent = false; private set
    fun send(value: String, sameTarget: Boolean, commit: (String) -> Unit): Boolean {
        if (sent || !sameTarget || value.isBlank()) return false
        // Set before the external operation: an uncertain return must never cause a duplicate.
        sent = true
        commit(TerminalDictation.text(value))
        return true
    }
}

object CompletionFeedback {
    fun inlineDelivered(finalText: String, confirmedText: String): Boolean =
        finalText.isNotBlank() && finalText == confirmedText

    fun bubble(message: String): String? = when {
        message.startsWith("Complete •") || message.startsWith("Sent to Termux •") -> null
        message.startsWith("Result saved • inline editing unavailable") -> "Text saved in AlienFlow"
        else -> message
    }
}
