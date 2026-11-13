package com.iscolt.cardsimulator.ui.cards

import android.graphics.Color
import android.widget.*
import com.iscolt.cardsimulator.data.SimulatorStore
import com.iscolt.cardsimulator.ui.UiKit

/** 首页卡包名称直接打开管理页，选择与管理使用统一入口。 */
class CardPackTitle(private val ui: UiKit, private val store: SimulatorStore,
    private val manage: () -> Unit) {
    fun title(version: String): LinearLayout = with(ui) {
        row().apply {
            minimumHeight = dp(48); isFocusable = true
            contentDescription = "管理卡包：" + store.currentPack().name
            val text = column()
            text.addView(label(store.currentPack().name, 18f, Color.WHITE, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            text.addView(label("NFC 卡模拟器 · v" + version, 11f, Color.rgb(173, 184, 198)))
            addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageView(context).apply { setImageResource(com.iscolt.cardsimulator.R.drawable.ic_expand_more)
                setColorFilter(Color.WHITE); rotation = -90f }, LinearLayout.LayoutParams(dp(24), dp(24)))
            setOnClickListener { manage() }
        }
    }
}
