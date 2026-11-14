package com.iscolt.cardsimulator.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/** 进程内只加载一次。卡包和日志在后台准备，Activity 通过可取消订阅接收结果。 */
class StoreLoader(load: () -> SimulatorStore) {
    private val main = Handler(Looper.getMainLooper())
    private val task = FutureTask {
        val started = SystemClock.elapsedRealtime()
        runCatching(load).also {
            Log.i(
                "SimulatorStartup",
                "Store loaded in " + (SystemClock.elapsedRealtime() - started) + " ms"
            )
        }
    }
    private val callbacks = mutableSetOf<(Result<SimulatorStore>) -> Unit>()
    private var result: Result<SimulatorStore>? = null

    init {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "simulator-startup") }
        executor.execute {
            // 加载线程降低优先级，将绘制首帧的 CPU 时间优先交给 UI。
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
            val loaded = task.get()
            main.post {
                result = loaded
                val waiting = callbacks.toList()
                callbacks.clear()
                waiting.forEach { it(loaded) }
            }
        }
        executor.shutdown()
    }

    /**
     * 同步等待共享加载任务并返回仓库；失败重新抛出加载异常。
     * 这是阻塞入口，HCE 回调使用它也可能等待；首页使用 whenReady 避免首屏等待。
     */
    fun await(): SimulatorStore = task.get().getOrThrow()

    /**
     * 仅主线程订阅加载结果，返回取消订阅函数；结果已就绪时仍异步投递。
     * 取消只移除回调，不取消整个进程共用的加载任务。
     */
    fun whenReady(callback: (Result<SimulatorStore>) -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        callbacks.add(callback)
        result?.let { loaded -> main.post { if (callbacks.remove(callback)) callback(loaded) } }
        return { callbacks.remove(callback); Unit }
    }
}
