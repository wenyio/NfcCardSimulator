package com.iscolt.cardsimulator.ui

import android.graphics.Color
import android.widget.LinearLayout
import com.google.android.material.tabs.TabLayout

/** 首页和详情共用轻量 Tab 样式；先恢复选中项，再监听用户切换。 */
fun UiKit.pageTabs(titles: List<String>, selected: Int, scrollable: Boolean = false,
    onSelected: (Int) -> Unit): TabLayout = TabLayout(context).apply {
    tabMode = if (scrollable) TabLayout.MODE_SCROLLABLE else TabLayout.MODE_FIXED
    tabGravity = if (scrollable) TabLayout.GRAVITY_START else TabLayout.GRAVITY_FILL
    setBackgroundColor(Color.TRANSPARENT)
    setTabTextColors(muted, blue)
    setSelectedTabIndicatorColor(blue)
    layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) }
    titles.forEachIndexed { index, title -> addTab(newTab().setText(title), index == selected) }
    post { setScrollPosition(selected, 0f, true) }
    addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) { onSelected(tab.position) }
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        override fun onTabReselected(tab: TabLayout.Tab) {}
    })
}
