package com.iscolt.cardsimulator.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/** 在内容区按主方向锁定手势：横向交给分页，纵向保留列表滚动。 */
class SwipePager(context: Context) : ViewPager(context) {
    private var downX = 0f
    private var downY = 0f
    private var direction = 0
    private var childControl = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    /** 记录按下位置，越过系统滑动阈值后锁定主方向；横滑交分页，纵滑保留内容滚动。 */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; direction = 0
                childControl = controlAt(this, event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (direction == 0) {
                val dx = abs(event.x - downX); val dy = abs(event.y - downY)
                if (dx > slop || dy > slop) direction = if (dx > dy) 1 else 2
            }
        }
        // ScrollView/可选择文本会阻止父容器拦截；明确横向后恢复分页接管，子控件收到 CANCEL。
        if (direction == 1 && !childControl) super.requestDisallowInterceptTouchEvent(false)
        return super.dispatchTouchEvent(event)
    }
    /** 触点位于输入/开关控件或已锁定纵向时不拦截，其余沿用 ViewPager 的分页判断。 */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (childControl || direction == 2) return false
        return super.onInterceptTouchEvent(event)
    }
    /** 把输入框和开关视为可自行处理手势的控件，防止分页抢走其操作。 */
    override fun canScroll(view: View, checkView: Boolean, dx: Int, x: Int, y: Int): Boolean {
        if (view is EditText || view is SwitchCompat) return true
        return super.canScroll(view, checkView, dx, x, y)
    }
    /** 从最上层子视图递归命中输入框或开关；坐标逐层转换，并计入父容器滚动偏移。 */
    private fun controlAt(view: View, x: Float, y: Float): Boolean {
        if (view is EditText || view is SwitchCompat) return true
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            val localX = x + view.scrollX - child.left
            val localY = y + view.scrollY - child.top
            if (child.visibility == View.VISIBLE && localX >= 0 && localY >= 0 &&
                localX < child.width && localY < child.height && controlAt(child, localX, localY)) return true
        }
        return false
    }
}
