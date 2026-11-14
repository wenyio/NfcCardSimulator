package com.iscolt.cardsimulator.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

/**
 * 组装标签和横向分页，连接双向选择；renderPage 只在页面视图创建时填充内容。
 * 切页不重启卡片会话。
 */
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
        /** 向分页器报告标签数量，标签与页面一一对应。 */
        override fun getCount() = titles.size
        /** 使用视图身份判断页面对象，避免按内容或标签文本误判。 */
        override fun isViewFromObject(view: View, item: Any) = view === item
        /** 为指定位置创建内容容器，挂到分页器后调用页面渲染回调。 */
        override fun instantiateItem(container: ViewGroup, position: Int): Any = column().also { page ->
            page.tag = "tab-page:$position"
            container.addView(page); renderPage(position, page)
        }
        /** 移除被分页器回收的页面视图；脚本引擎的寿命不由此回调控制。 */
        override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
            container.removeView(item as View)
        }
    }
    // 详情仅三页，保留各页输入、规则展开和日志控件；首页使用默认缓存避免一次构造所有卡片。
    if (!scrollableTabs) pager.offscreenPageLimit = titles.size - 1
    pager.setCurrentItem(selected, false)
    pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
        /** 分页选中后同步标签并通知调用方，更新当前标签位置。 */
        override fun onPageSelected(position: Int) {
            tabs.getTabAt(position)?.select(); onSelected(position)
        }
        /** 把分页滚动比例传给标签栏，保持指示器随手势移动。 */
        override fun onPageScrolled(position: Int, offset: Float, offsetPixels: Int) {
            tabs.setScrollPosition(position, offset, true)
        }
    })
}
