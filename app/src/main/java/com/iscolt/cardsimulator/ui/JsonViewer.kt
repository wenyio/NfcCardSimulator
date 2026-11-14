package com.iscolt.cardsimulator.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.widget.ScrollView
import com.iscolt.cardsimulator.R

/** 卡片与卡包共用只读 JSON 视图；内容与对应导出文件一致，可长按选择复制。 */
class JsonViewer(private val ui: UiKit) {
    fun show(title: String, json: String) = with(ui) {
        val text = label(json, 12f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            contentDescription = context.getString(R.string.json_content)
        }
        AlertDialog.Builder(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(text) })
            .setPositiveButton(R.string.action_close, null).show()
    }
}
