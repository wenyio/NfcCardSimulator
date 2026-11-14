package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.widget.*
import com.iscolt.cardsimulator.core.CardScript
import com.iscolt.cardsimulator.importer.CapturedTransaction
import com.iscolt.cardsimulator.importer.ImportEvidence
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.R

/** 每次选择一笔完整交易，明确确认 AID 后才转换为顺序固定回复脚本。 */
class TransactionImportDialog(private val ui: UiKit) {
    /** 展示交易选择与预览，用户确认 AID 后才转换为顺序脚本并交给 onSave；不在此直接写仓库。 */
    fun show(
        transactions: List<CapturedTransaction>,
        evidence: ImportEvidence? = null,
        onSave: (CardScript) -> Unit
    ) = with(ui) {
        val body = column().apply { setPadding(dp(16), 0, dp(16), 0) }
        val picker = Spinner(context).apply {
            adapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_dropdown_item,
                transactions.mapIndexed { i, t ->
                    context.getString(
                        R.string.transaction_summary,
                        i + 1,
                        t.pairs.size,
                        t.issues.size
                    )
                })
        }; body.addView(picker)
        val name = EditText(context).apply {
            hint =
                context.getString(R.string.transaction_card_name); setText(R.string.transaction_default_name)
        }; body.addView(name)
        val scheme = EditText(context).apply {
            hint =
                context.getString(R.string.scheme_title); setText(R.string.transaction_custom_scheme)
        }; body.addView(scheme)
        body.addView(label(context.getString(R.string.transaction_aids), 13f, muted))
        val aids = EditText(context); body.addView(aids)
        val preview = label("", 12f).apply { setTextIsSelectable(true) }; body.addView(preview)
        val error = label("", 13f); body.addView(error)
        val dialog = AlertDialog.Builder(context).setTitle(R.string.transaction_import_preview)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.transaction_import, null).create()

        /** 切换交易后更新提取的 AID 和限长预览；存在配对问题时禁用导入，完整规则不裁剪。 */
        fun update() {
            val transaction = transactions[picker.selectedItemPosition]
            aids.setText(transaction.aids().joinToString("\n"))
            // 预览限制显示长度；完整规则仍保留。错误必须修正原日志后重新导入。
            preview.text =
                (transaction.issues.joinToString("\n") + "\n" + transaction.pairs.joinToString("\n\n") {
                    context.getString(
                        R.string.transaction_line,
                        it.line
                    ) + "\nT: ${it.command}\nC: ${it.response}"
                }).take(16000)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                transaction.issues.isEmpty() && transaction.pairs.isNotEmpty()
        }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** 无选中事件时保持当前交易预览。 */
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            /** 用户选择交易后同步 AID、报文预览和是否允许导入。 */
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                update()
            }
        }
        dialog.show()
        run {
            update()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val card = transactions[picker.selectedItemPosition].toCard(
                        name.text.toString(), scheme.text.toString(),
                        aids.text.toString().lines().filter { it.isNotBlank() })
                    onSave(evidence?.attach(card, "T:/C: 交易日志") ?: card)
                    dialog.dismiss()
                } catch (e: Exception) {
                    error.text = e.message
                }
            }
        }; dialog
    }
}
