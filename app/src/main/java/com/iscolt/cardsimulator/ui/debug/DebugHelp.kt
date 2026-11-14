package com.iscolt.cardsimulator.ui.debug

import android.app.AlertDialog
import android.content.Context
import com.iscolt.cardsimulator.R

/** 将调试操作与固定回复的边界集中到说明弹窗。 */
object DebugHelp {
    fun show(context: Context) {
        AlertDialog.Builder(context).setTitle(R.string.debug_help_title)
            .setMessage(R.string.debug_instructions)
            .setPositiveButton(R.string.sim_ok, null).show()
    }
}
