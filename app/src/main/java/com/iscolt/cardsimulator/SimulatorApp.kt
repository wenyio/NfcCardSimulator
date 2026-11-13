package com.iscolt.cardsimulator

import android.app.Application
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.data.StoreLoader

class SimulatorApp : Application() {
    lateinit var storeLoader: StoreLoader
        private set
    val store: SimulatorStore get() = storeLoader.await()
    override fun onCreate() {
        super.onCreate()
        // 冷启动只发起后台任务，不在 Application 主线程读取卡包或历史文件。
        storeLoader = StoreLoader { SimulatorStore(this) }
    }
}
