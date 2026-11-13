package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.importer.CapturedTransaction
import com.iscolt.cardsimulator.importer.ImportEvidence
import com.iscolt.cardsimulator.ui.UiKit

/** 每次选择一笔完整交易，明确确认 AID 后才转换为顺序固定回复脚本。 */
class TransactionImportDialog(private val ui: UiKit) {
    fun show(transactions: List<CapturedTransaction>, evidence: ImportEvidence? = null, onSave: (CardScript) -> Unit) = with(ui) {
        val body = column().apply { setPadding(dp(16), 0, dp(16), 0) }
        val picker = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                transactions.mapIndexed { i, t -> "交易 ${i + 1} · ${t.pairs.size} 组 · ${t.issues.size} 个错误" })
        }; body.addView(picker)
        val name = EditText(context).apply { hint = "导入卡片名称"; setText("日志测试卡") }; body.addView(name)
        val scheme = EditText(context).apply { hint = "卡组织"; setText("自定义") }; body.addView(scheme)
        body.addView(label("AID（每行一个；请核对 SELECT 提取结果）", 13f, muted))
        val aids = EditText(context); body.addView(aids)
        val preview = label("", 12f).apply { setTextIsSelectable(true) }; body.addView(preview)
        val error = label("", 13f); body.addView(error)
        val dialog = AlertDialog.Builder(context).setTitle("交易日志导入预览")
            .setView(ScrollView(context).apply { addView(body) }).setNegativeButton("取消", null)
            .setPositiveButton("导入交易", null).create()
        fun update() {
            val transaction = transactions[picker.selectedItemPosition]
            aids.setText(transaction.aids().joinToString("\n"))
            // 预览限制显示长度；完整规则仍保留。错误必须修正原日志后重新导入。
            preview.text = (transaction.issues.joinToString("\n") + "\n" + transaction.pairs.joinToString("\n\n") {
                "行 ${it.line}\nT: ${it.command}\nC: ${it.response}"
            }).take(16000)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = transaction.issues.isEmpty() && transaction.pairs.isNotEmpty()
        }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { update() }
        }
        dialog.show()
        run {
            update()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val card = transactions[picker.selectedItemPosition].toCard(name.text.toString(), scheme.text.toString(),
                        aids.text.toString().lines().filter { it.isNotBlank() })
                    onSave(evidence?.attach(card, "T:/C: 交易日志") ?: card)
                    dialog.dismiss()
                } catch (e: Exception) { error.text = e.message }
            }
        }; dialog
    }
}
