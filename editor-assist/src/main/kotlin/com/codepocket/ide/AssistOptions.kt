package com.codepocket.ide

/** Editor-only options; no Android, persistence, project access or execution dependency. */
interface AssistOptionsContract {
    val tabWidth: Int
    val useSpaces: Boolean
    val completionTab: Boolean
    val completionEnter: Boolean
    val completionArrows: CompletionArrows
}

data class AssistOptions(
    override val tabWidth: Int = 4,
    override val useSpaces: Boolean = true,
    override val completionTab: Boolean = true,
    override val completionEnter: Boolean = false,
    override val completionArrows: CompletionArrows = CompletionArrows.WHEN_VISIBLE
) : AssistOptionsContract
