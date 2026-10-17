package com.codepocket.ide

data class SnippetNavigation(val session: SnippetSession?, val start: Int, val end: Int = start)

/** Ephemeral tab stops; each edit is accepted only inside the current placeholder. */
data class SnippetSession(val document: String, val revision: Long, val length: Int,
    val stops: List<SnippetStop>, val finalOffset: Int, val index: Int = 0) {
    init {
        require(document.isNotEmpty() && stops.isNotEmpty() && index in stops.indices)
        require(stops.all { it.start in 0..length && it.end in it.start..length })
        require(stops.zipWithNext().all { (a, b) -> a.end <= b.start })
        require(finalOffset in stops.last().end..length)
    }
    val active get() = stops[index]
    fun matches(document: String, revision: Long, start: Int, end: Int): Boolean =
        this.document == document && this.revision == revision &&
            minOf(start, end) >= active.start && maxOf(start, end) <= active.end

    /** Called from native text-change notifications, not used to rewrite text or IME composition. */
    fun edited(document: String, revision: Long, start: Int, removed: Int, inserted: Int): SnippetSession? {
        if (this.document != document || this.revision != revision || removed < 0 || inserted < 0 ||
            start < active.start || start.toLong() + removed > active.end) return null
        val delta = inserted.toLong() - removed
        val nextLength = length.toLong() + delta
        if (nextLength !in 0..200000) return null
        val amount = delta.toInt()
        val updated = stops.mapIndexed { at, stop -> when {
            at < index -> stop
            at == index -> stop.copy(end = stop.end + amount)
            else -> stop.copy(start = stop.start + amount, end = stop.end + amount)
        } }
        return copy(length = nextLength.toInt(), stops = updated, finalOffset = finalOffset + amount)
    }

    fun move(reverse: Boolean): SnippetNavigation {
        if (!reverse && index == stops.lastIndex) return SnippetNavigation(null, finalOffset)
        val next = copy(index = (index + if (reverse) -1 else 1).coerceIn(stops.indices))
        return SnippetNavigation(next, next.active.start, next.active.end)
    }
}

enum class SnippetKeyAction { PASS, NEXT, PREVIOUS, END }
object SnippetKeys {
    fun action(key: CompletionKey, reverse: Boolean, active: Boolean, completionHandled: Boolean,
        composing: Boolean, softKeyboard: Boolean, modified: Boolean): SnippetKeyAction {
        if (!active || completionHandled || composing || softKeyboard || modified) return SnippetKeyAction.PASS
        return when (key) {
            CompletionKey.TAB -> if (reverse) SnippetKeyAction.PREVIOUS else SnippetKeyAction.NEXT
            CompletionKey.ESC -> SnippetKeyAction.END
            else -> SnippetKeyAction.PASS
        }
    }
}
