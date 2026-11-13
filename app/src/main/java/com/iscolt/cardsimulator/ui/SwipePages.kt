package com.iscolt.cardsimulator.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

/** Tab 与页面双向联动，页面切换只更新视图，不触发卡片会话的重新启动。 */
fun UiKit.swipePages(parent: LinearLayout, titles: List<String>, selected: Int,
    scrollableTabs: Boolean = false, onSelected: (Int) -> Unit,
    renderPage: (Int, LinearLayout) -> Unit) {
    val pager = SwipePager(context).apply {
        // 相邻页之间保留背景留白，横滑时卡片不会贴成一整块；静止页宽和外边距保持不变。
        pageMargin = dp(12)
    }
    val tabs = pageTabs(titles, selected, scrollableTabs) { index ->
        if (pager.currentItem != index) pager.setCurrentItem(index, true)
    }
    parent.addView(tabs)
    parent.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
    pager.adapter = object : PagerAdapter() {
        override fun getCount() = titles.size
        override fun isViewFromObject(view: View, item: Any) = view === item
        override fun instantiateItem(container: ViewGroup, position: Int): Any = column().also { page ->
            page.tag = "tab-page:$position"
            container.addView(page); renderPage(position, page)
        }
        override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
            container.removeView(item as View)
        }
    }
    // 详情仅三页，保留各页输入、规则展开和日志控件；首页使用默认缓存避免一次构造所有卡片。
    if (!scrollableTabs) pager.offscreenPageLimit = titles.size - 1
    pager.setCurrentItem(selected, false)
    pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            tabs.getTabAt(position)?.select(); onSelected(position)
        }
        override fun onPageScrolled(position: Int, offset: Float, offsetPixels: Int) {
            tabs.setScrollPosition(position, offset, true)
        }
    })
}
