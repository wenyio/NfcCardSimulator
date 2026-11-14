package com.iscolt.cardsimulator.ui.logs

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 会话分组仅用于展示，导出保留完整时间及方向记录。 */
class LogsPage(private val ui: UiKit, private val store: SimulatorStore, private val export: () -> Unit) {
    private var text: TextView? = null
    private var scroll: ScrollView? = null
    private var cardId: String? = null
    fun show(content: LinearLayout, cardId: String? = null) = with(ui) {
        this@LogsPage.cardId = cardId
        val actions = row()
        actions.action(context.getString(R.string.logs_export)) { export() }
        actions.action(context.getString(if (cardId == null) R.string.logs_clear_all else R.string.logs_clear_card)) {
            store.clearLogs(cardId); update()
        }
        content.addView(actions)
        content.addView(label(context.getString(R.string.logs_help), 12f, muted))
        text = label("", 12f).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            setPadding(dp(12), dp(8), dp(12), dp(12)); background = background(Color.WHITE)
        }
        scroll = ScrollView(context).apply { addView(text) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); update()
    }
    fun update() {
        val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)
        var previous = ""
        text?.text = store.logs().filter { cardId == null || it.cardId == cardId }.joinToString("\n\n") {
            val group = if (it.session != previous) { previous = it.session; "━━ ${it.session} ━━\n" } else ""
            group + "${formatter.format(Date(it.time))}  ${it.kind}\n${it.message}"
        }.ifBlank { ui.context.getString(R.string.logs_empty) }
        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
    }
    fun detach() { text = null; scroll = null }
}
