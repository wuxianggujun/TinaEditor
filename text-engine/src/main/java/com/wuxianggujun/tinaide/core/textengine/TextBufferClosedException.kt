package com.wuxianggujun.tinaide.core.textengine

/** Lets consumers abandon stale work without swallowing unrelated state errors. */
class TextBufferClosedException(message: String = "TextBuffer is already closed") : IllegalStateException(message)
