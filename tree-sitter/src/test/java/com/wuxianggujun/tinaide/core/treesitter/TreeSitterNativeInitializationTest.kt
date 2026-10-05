package com.wuxianggujun.tinaide.core.treesitter

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertThrows
import org.junit.Test

class TreeSitterNativeInitializationTest {
    @Test
    fun construction_shouldNotLoadNativeLibrary() {
        val calls = AtomicInteger()

        TreeSitterNativeInitialization { calls.incrementAndGet() }

        assertThat(calls.get()).isEqualTo(0)
    }

    @Test
    fun ensureInitialized_shouldReuseSuccessfulLoad() {
        val calls = AtomicInteger()
        val initialization = TreeSitterNativeInitialization { calls.incrementAndGet() }

        repeat(3) { initialization.ensureInitialized() }

        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun ensureInitialized_shouldPropagateFailureAndAllowRetry() {
        val calls = AtomicInteger()
        val failure = UnsatisfiedLinkError("core library unavailable")
        val initialization = TreeSitterNativeInitialization {
            if (calls.incrementAndGet() == 1) throw failure
        }

        val thrown = assertThrows(UnsatisfiedLinkError::class.java) {
            initialization.ensureInitialized()
        }
        assertThat(thrown).isSameInstanceAs(failure)

        initialization.ensureInitialized()
        initialization.ensureInitialized()
        assertThat(calls.get()).isEqualTo(2)
    }

    @Test(timeout = 10_000)
    fun concurrentCalls_shouldWaitForOneSuccessfulLoad() {
        val calls = AtomicInteger()
        val loadStarted = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val loaded = AtomicBoolean()
        val initialization = TreeSitterNativeInitialization {
            calls.incrementAndGet()
            loadStarted.countDown()
            check(finishLoad.await(5, TimeUnit.SECONDS))
            loaded.set(true)
        }
        val workers = Executors.newFixedThreadPool(4)
        val callersReady = CountDownLatch(4)
        val startCalls = CountDownLatch(1)
        try {
            val results = List(4) {
                workers.submit<Boolean> {
                    callersReady.countDown()
                    check(startCalls.await(5, TimeUnit.SECONDS))
                    initialization.ensureInitialized()
                    loaded.get()
                }
            }
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue()
            startCalls.countDown()
            assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(results.any { it.isDone }).isFalse()

            finishLoad.countDown()

            results.forEach { assertThat(it.get(5, TimeUnit.SECONDS)).isTrue() }
            assertThat(calls.get()).isEqualTo(1)
        } finally {
            startCalls.countDown()
            finishLoad.countDown()
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
