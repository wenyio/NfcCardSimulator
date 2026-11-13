package com.iscolt.cardsimulator.nfc

/** 详情范围的模拟控制；页面重绘和工具切换不能重选卡或重置正在执行的步骤。 */
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
    fun open(id: String) {
        if (cardId != id) { stop(); select(id) }
        cardId = id; requested = true; error = null; sync()
    }
    fun leave() { cardId = null; error = null; requested = true; stop() }
    fun foreground(value: Boolean) { foreground = value; sync() }
    fun editing(value: Boolean) { editing = value; sync() }
    fun setRequested(value: Boolean) { requested = value; if (!value) error = null; sync() }
    private fun sync() {
        if (cardId == null || !foreground || editing || !requested) { stop(); return }
        // start 必须幂等，返回设置或重绘页面不会重置已经启动的射频会话。
        error = runCatching { start() }.exceptionOrNull()?.message
    }
}
