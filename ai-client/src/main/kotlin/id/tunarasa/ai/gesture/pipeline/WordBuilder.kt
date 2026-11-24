package id.tunarasa.ai.gesture.pipeline

import id.tunarasa.ai.gesture.CommittedLetter
import id.tunarasa.ai.gesture.WordState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Accumulates committed letters. Thread-safe. */
class WordBuilder(private val maxLength: Int = 50) {
    private val _state = MutableStateFlow(WordState())
    val state: StateFlow<WordState> = _state.asStateFlow()

    /** Returns false when the word is already full. */
    fun append(letter: CommittedLetter): Boolean {
        while (true) {
            val current = _state.value
            if (current.letters.size >= maxLength) return false
            if (_state.compareAndSet(current, WordState(current.letters + letter))) return true
        }
    }

    /** Ignored at the start and after another space. */
    fun appendSpace(timestampMillis: Long) = _state.update { current ->
        val last = current.letters.lastOrNull()
        if (last == null || last.letter == WordState.SPACE || current.letters.size >= maxLength) {
            current
        } else {
            WordState(current.letters + CommittedLetter(WordState.SPACE, 1f, timestampMillis))
        }
    }

    fun removeLast() = _state.update { WordState(it.letters.dropLast(1)) }

    fun clear() = _state.update { WordState() }
}
