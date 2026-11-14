package com.iscolt.cardsimulator.ui.cards

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import com.iscolt.cardsimulator.R
import java.util.concurrent.Executors

/** 串行执行卡包事务；原子存储负责提交，进度期间不允许重复操作。 */
internal object PackWork {
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 在共用单线程后台执行器中执行事务，主线程显示不可取消的进度并暂时锁定方向。
     * 完成后只向仍有效的页面回调 Result，恢复方向；文件原子性由仓库负责。
     */
    fun <T> run(activity: Activity, operation: () -> T, complete: (Result<T>) -> Unit) {
        val progress = AlertDialog.Builder(activity).setMessage(R.string.pack_saving)
            .setCancelable(false).create()
        val orientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        progress.show()
        executor.execute {
            val result = runCatching(operation)
            activity.runOnUiThread {
                if (!activity.isDestroyed && !activity.isFinishing) {
                    progress.dismiss()
                    activity.requestedOrientation = orientation
                    complete(result)
                }
            }
        }
    }
}
