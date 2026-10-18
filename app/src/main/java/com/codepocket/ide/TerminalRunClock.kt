package com.codepocket.ide

/** Monotonic run deadline. Replayed state messages never extend a wait or active execution budget. */
class TerminalRunClock(private val began: Long, private val activeLimit: Long = 10000, private val inputLimit: Long = 300000) {
    enum class State { STARTING, RUNNING, WAITING }
    var state = State.STARTING; private set
    private var lastSequence = 0L
    private var remainingActive = activeLimit
    private var changed = began
    fun remaining(now: Long): Long = when(state) {
        State.STARTING -> 30000 - (now-began)
        State.RUNNING -> remainingActive - (now-changed)
        State.WAITING -> inputLimit - (now-changed)
    }
    fun update(next: State, sequence: Long, now: Long): Boolean {
        if(sequence<=lastSequence)return false
        lastSequence=sequence
        if(next==state)return true
        if(state==State.RUNNING)remainingActive=(remainingActive-(now-changed)).coerceAtLeast(0)
        state=next;changed=now;return true
    }
}
