package com.iscolt.cardsimulator

import android.app.Application
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.data.StoreLoader

/** 进程级入口，持有唯一仓库加载器；页面实例重建时仍共享同一进程内数据。 */
class SimulatorApp : Application() {
    lateinit var storeLoader: StoreLoader
        private set
    val store: SimulatorStore get() = storeLoader.await()

    /**
     * Android 创建应用进程时调用；启动一次后台仓库加载，让首屏先绘制。
     * 界面通过 whenReady 接收结果；同步访问 store 的调用方可能等待加载完成。
     */
    override fun onCreate() {
        super.onCreate()
        // 冷启动只发起后台任务，不在 Application 主线程读取卡包或历史文件。
        storeLoader = StoreLoader { SimulatorStore(this) }
    }
}
