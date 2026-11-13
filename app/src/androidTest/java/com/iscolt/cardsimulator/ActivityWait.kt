package com.iscolt.cardsimulator

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** 等待真实 Activity 转场完成，主线程空闲不代表目标页已进入 RESUMED。 */
internal inline fun <reified T : Activity> awaitResumedActivity(): T {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    var target: T? = null
    val deadline = SystemClock.uptimeMillis() + 15000
    while (target == null && SystemClock.uptimeMillis() < deadline) {
        instrumentation.runOnMainSync {
            target = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<T>().singleOrNull()
        }
        if (target == null) SystemClock.sleep(50)
    }
    return requireNotNull(target) { "目标页面未进入前台：" + T::class.java.simpleName }
}
