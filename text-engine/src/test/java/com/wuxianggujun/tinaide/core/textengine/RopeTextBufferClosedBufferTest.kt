package com.wuxianggujun.tinaide.core.textengine

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RopeTextBufferClosedBufferTest {
    @Test
    fun isClosedRemainsReadableAndCloseIsIdempotent() {
        val buffer = RopeTextBuffer("content")
        assertThat(buffer.isClosed).isFalse()

        buffer.close()
        assertThat(buffer.isClosed).isTrue()
        buffer.close()
        assertThat(buffer.isClosed).isTrue()
    }

    @Test
    fun closedContentAccessKeepsFailFastContractWithSpecificException() {
        val buffer = RopeTextBuffer("hello\nworld")
        buffer.close()

        val accesses = listOf<() -> Any?>(
            { buffer.lineCount },
            { buffer.length },
            { buffer.toString() },
            { buffer.getLine(0) },
            { buffer.substring(0, 1) },
            { buffer.insert(0, "x") },
            { buffer.delete(0, 1) },
            { buffer.replace(0, 1, "x") }
        )
        accesses.forEach { access ->
            val failure = assertThrows(TextBufferClosedException::class.java) { access() }
            assertThat(failure).isInstanceOf(IllegalStateException::class.java)
            assertThat(failure).hasMessageThat().isEqualTo("RopeTextBuffer is already closed")
        }
    }
}
