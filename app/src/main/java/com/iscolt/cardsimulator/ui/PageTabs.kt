package com.iscolt.cardsimulator.ui

import android.graphics.Color
import android.widget.LinearLayout
import com.google.android.material.tabs.TabLayout

/**
 * 创建标签栏并恢复选中项，随后绑定选择回调；scrollable 决定滚动或等宽布局。
 */
fun UiKit.pageTabs(
    titles: List<String>, selected: Int, scrollable: Boolean = false,
    onSelected: (Int) -> Unit
): TabLayout = TabLayout(context).apply {
    tabMode = if (scrollable) TabLayout.MODE_SCROLLABLE else TabLayout.MODE_FIXED
    tabGravity = if (scrollable) TabLayout.GRAVITY_START else TabLayout.GRAVITY_FILL
    setBackgroundColor(Color.TRANSPARENT)
    setTabTextColors(muted, blue)
    setSelectedTabIndicatorColor(blue)
    layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) }
    titles.forEachIndexed { index, title -> addTab(newTab().setText(title), index == selected) }
    post { setScrollPosition(selected, 0f, true) }
    addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        /** 用户或分页选中标签后，把标签位置交给调用方同步页面。 */
        override fun onTabSelected(tab: TabLayout.Tab) {
            onSelected(tab.position)
        }

        /** 取消选中无需业务处理，保持页面会话状态不变。 */
        override fun onTabUnselected(tab: TabLayout.Tab) {}

        /** 重复点击当前标签无需业务处理，不重置输入或模拟步骤。 */
        override fun onTabReselected(tab: TabLayout.Tab) {}
    })
}
