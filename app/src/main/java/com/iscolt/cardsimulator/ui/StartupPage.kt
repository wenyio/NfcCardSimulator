package com.iscolt.cardsimulator.ui

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar

/** 首帧即可绘制的轻量页面，不依赖卡包、日志或 NFC 系统服务。 */
class StartupPage(private val ui: UiKit) {
    fun create(error: Throwable? = null): LinearLayout = with(ui) {
        column().apply {
            setBackgroundColor(Color.rgb(241, 245, 249))
            addView(row().apply {
                setBackgroundColor(ink); setPadding(dp(16), dp(4), dp(16), dp(4)); minimumHeight = dp(56)
                addView(label("NFC 卡模拟器", 20f, Color.WHITE, true))
            })
            addView(column().apply {
                gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24))
                if (error == null) addView(ProgressBar(context), LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(label(if (error == null) "正在加载卡片…" else "卡片加载失败，请重新打开应用", 14f, muted).apply { gravity = Gravity.CENTER })
                if (error != null) addView(label(error.message.orEmpty(), 12f, muted).apply { gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
}
