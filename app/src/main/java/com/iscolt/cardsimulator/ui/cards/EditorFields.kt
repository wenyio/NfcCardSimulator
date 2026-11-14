package com.iscolt.cardsimulator.ui.cards

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import android.view.View
import com.iscolt.cardsimulator.ui.UiKit

/** 显示错误文字、将表单滚动到顶部并弹出提示；不清空用户输入。 */
internal fun TextView.showEditorError(message: String) {
    text = message; visibility = View.VISIBLE
    (parent?.parent as? ScrollView)?.smoothScrollTo(0, 0)
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/**
 * 创建固定标签及独立输入控件，按 multiline 决定单行或多行，返回输入框供快照读取。
 */
internal fun UiKit.editorField(
    parent: LinearLayout,
    title: String,
    value: String,
    multiline: Boolean = false
): EditText {
    parent.addView(label(title, 13f, muted))
    return EditText(context).apply {
        hint = title; setText(value); setSelectAllOnFocus(false)
        inputType =
            InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multiline) {
            minLines = 2; maxLines = 8
        } else setSingleLine(true)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
}
