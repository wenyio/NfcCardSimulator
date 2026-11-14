package com.iscolt.cardsimulator.nfc

/**
 * 详情范围的模拟控制；页面重绘和工具切换不能重选卡或重置正在执行的步骤。
 * requested 是用户意图，foreground/editing 是页面条件；实际 enabled 状态由外部 NfcController 管理。
 * 主线程调用；这里只协调传入回调，没有射频硬件或脚本游标，不把三种状态混在一个开关里。
 */
class DetailSimulationSession(
    private val select: (String) -> Unit,
    private val start: () -> Unit,
    private val stop: () -> Unit
) {
    var cardId: String? = null
        private set
    var error: String? = null
        private set
    val isRequested get() = requested
    private var requested = true
    private var foreground = false
    private var editing = false

    /** 打开详情时仅在目标卡改变时停止并重选；同卡不重选，随后恢复默认启动意图并同步条件。 */
    fun open(id: String) {
        if (cardId != id) {
            stop(); select(id)
        }
        cardId = id
        requested = true
        error = null
        sync()
    }

    /** 清除详情目标和错误，恢复下次进入的默认意图，并停止实际模拟。 */
    fun leave() {
        cardId = null
        error = null
        requested = true
        stop()
    }

    /** 更新页面是否允许前台模拟，然后同步；切后台只暂停实际状态，不清除启动意图。 */
    fun foreground(value: Boolean) {
        foreground = value
        sync()
    }

    /** 标记独立编辑页是否占用详情会话；编辑期间禁止启动，返回后重新检查条件。 */
    fun editing(value: Boolean) {
        editing = value
        sync()
    }

    /** 记录用户开关意图；关闭时清理错误，再按前台和编辑状态决定是否实际启用。 */
    fun setRequested(value: Boolean) {
        requested = value
        if (!value) error = null
        sync()
    }

    /**
     * 集中计算是否允许启动：有卡、详情前台、未编辑且用户请求启动，四者必须同时满足。
     * 条件不满足就停止；启动失败保存错误供界面显示。start 回调必须幂等，避免重绘推进或重置会话。
     */
    private fun sync() {
        if (cardId == null || !foreground || editing || !requested) {
            stop(); return
        }
        // start 必须幂等，返回设置或重绘页面不会重置已经启动的射频会话。
        error = runCatching { start() }.exceptionOrNull()?.message
    }
}
