package com.iscolt.cardsimulator.ui.cards

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import android.view.View
import com.iscolt.cardsimulator.ui.UiKit

internal fun TextView.showEditorError(message: String) {
    text = message; visibility = View.VISIBLE
    (parent?.parent as? ScrollView)?.smoothScrollTo(0, 0)
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** 编辑字段有固定标签，输入后仍可识别含义；每个新页面使用独立视图实例。 */
internal fun UiKit.editorField(parent: LinearLayout, title: String, value: String, multiline: Boolean = false): EditText {
    parent.addView(label(title, 13f, muted))
    return EditText(context).apply {
        hint = title; setText(value); setSelectAllOnFocus(false)
        inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multiline) { minLines = 2; maxLines = 8 } else setSingleLine(true)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
}
