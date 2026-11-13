package com.iscolt.cardsimulator.ui.debug

import android.app.AlertDialog
import android.content.Context

/** 将调试操作与固定回复的边界集中到说明弹窗。 */
object DebugHelp {
    fun show(context: Context) {
        AlertDialog.Builder(context).setTitle("调试使用说明")
            .setMessage(listOf(
                "1. 选择命令只填充输入框，点击“发送到脚本”才执行；输入可以继续修改。",
                "2. 手动与 NFC 会话独立；顺序模式按步骤发送，未匹配不推进，重置后从头开始。",
                "3. 固定回复不会随输入变化重新计算密文或签名。"
            ).joinToString("\n\n"))
            .setPositiveButton("知道了", null).show()
    }
}
