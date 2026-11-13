package com.iscolt.cardsimulator

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iscolt.cardsimulator.data.StoreLoader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class StoreLoaderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun loadsOffMainThreadAndCancelledPageDoesNotReceiveResult() {
        val existing = (instrumentation.targetContext.applicationContext as SimulatorApp).store
        val gate = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val cancelledCalled = AtomicBoolean(false)
        val offMain = AtomicBoolean(false)
        val loader = StoreLoader {
            offMain.set(Looper.myLooper() != Looper.getMainLooper())
            check(gate.await(10, TimeUnit.SECONDS))
            existing
        }
        instrumentation.runOnMainSync {
            val cancel = loader.whenReady { cancelledCalled.set(true) }
            cancel()
            loader.whenReady {
                assertSame(existing, it.getOrThrow())
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                delivered.countDown()
            }
        }
        gate.countDown()
        assertTrue(delivered.await(10, TimeUnit.SECONDS))
        assertTrue(offMain.get())
        assertFalse(cancelledCalled.get())
        // 重建后的新页面订阅已经就绪的仓库，仍只回调一次。
        val recreated = CountDownLatch(1)
        instrumentation.runOnMainSync { loader.whenReady { assertSame(existing, it.getOrThrow()); recreated.countDown() } }
        assertTrue(recreated.await(10, TimeUnit.SECONDS))
    }
    @Test fun loadingFailureIsDeliveredWithoutPublishingPartialStore() {
        val delivered = CountDownLatch(1)
        val loader = StoreLoader { error("test-load-failure") }
        instrumentation.runOnMainSync {
            loader.whenReady { assertEquals("test-load-failure", it.exceptionOrNull()?.message); delivered.countDown() }
        }
        assertTrue(delivered.await(10, TimeUnit.SECONDS))
        assertEquals("test-load-failure", runCatching { loader.await() }.exceptionOrNull()?.message)
    }
}
