package com.iscolt.cardsimulator.ui.cards

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.SimulatorApp
import com.iscolt.cardsimulator.core.ApduRule
import com.iscolt.cardsimulator.core.RuleCodec
import com.iscolt.cardsimulator.data.CardSchemeStore
import com.iscolt.cardsimulator.ui.UiKit
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 新建、编辑、复制共用页面；已保存卡片与编辑草稿完全隔离。 */
class CardEditorActivity : AppCompatActivity() {
    private val store get() = (application as SimulatorApp).store
    private lateinit var ui: UiKit
    private lateinit var draft: CardEditorDraft
    private lateinit var form: CardEditorPage
    private lateinit var ruleForm: RuleEditorPage
    private lateinit var body: LinearLayout
    private lateinit var toolbar: LinearLayout
    private var ruleIndex: Int? = null
    private var rule: ApduRule? = null
    private var originalRule: ApduRule? = null
    private var token = UUID.randomUUID().toString()
    private val stateFile get() = File(filesDir, "editor-"+token+".json")
    /**
     * 创建独立编辑页：新建时生成草稿，重建时按 token 读取当前格式私有草稿。
     * 已保存卡片与可变草稿分离；恢复文件丢失时提示并退出，不猜测历史格式。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this); form = CardEditorPage(ui, CardSchemeStore(this, store::cards)); ruleForm = RuleEditorPage(ui)
        token = savedInstanceState?.getString("draftToken") ?: token
        val restored = if (savedInstanceState != null) runCatching { JSONObject(stateFile.readText()) }.getOrNull() else null
        if (savedInstanceState != null && restored == null) {
            Toast.makeText(this, R.string.editor_restore_failed, Toast.LENGTH_LONG).show(); finish(); return
        }
        if (restored != null) {
            draft = CardEditorDraft.restore(restored.getJSONObject("draft"))
            if (restored.has("ruleIndex")) {
                ruleIndex = restored.getInt("ruleIndex"); rule = RuleCodec.decode(restored.getJSONObject("rule"))
                originalRule = RuleCodec.decode(restored.getJSONObject("originalRule"))
            }
        } else {
            val sourceId = intent.getStringExtra("cardId")
            val source = sourceId?.let(store::findCard)
            if (sourceId != null && source == null) { finish(); return }
            val target = store.currentPack().takeUnless { it.builtIn }?.id ?: com.iscolt.cardsimulator.core.CardPacks.PERSONAL
            draft = CardEditorDraft.create(source, intent.getBooleanExtra("copy", false) || source?.builtIn == true, target)
        }
        buildShell(); render()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** 将系统返回交给 back，统一检查规则或卡片是否存在未保存修改。 */
            override fun handleOnBackPressed() = back()
        })
    }
    /** 安装编辑页工具栏与内容容器，具体卡片或规则表单由 render 构造。 */
    private fun buildShell() = with(ui) {
        body = column().apply { setPadding(dp(16), 0, dp(16), 0) }
        toolbar = shell(this@CardEditorActivity, body)
    }
    /** 根据是否正在编辑规则构建表单；标题由 EditorMode 映射 strings.xml，状态不依赖显示文字。 */
    private fun render(): Unit = with(ui) {
        toolbar.removeAllViews(); body.removeAllViews()
        toolbar.addView(icon(R.drawable.ic_back, getString(R.string.action_back)) { back() }.apply { setColorFilter(Color.WHITE) })
        val title = getString(when (draft.mode) { EditorMode.NEW -> R.string.editor_title_new; EditorMode.COPY -> R.string.editor_title_copy; EditorMode.EDIT -> R.string.editor_title_edit })
        toolbar.addView(label(if (ruleIndex == null) title else getString(R.string.editor_rule_title), 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(Button(this@CardEditorActivity).apply {
            text = getString(if (ruleIndex == null) R.string.editor_save_card else R.string.editor_save_rule); isAllCaps = false
            setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { save() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        if (ruleIndex != null) ruleForm.show(body, rule!!) else form.show(body, draft.card, store.packs().filterNot { it.builtIn },
            editRule = { index ->
                draft.card = form.snapshot(); hideKeyboard(); ruleIndex = index
                rule = draft.card.rules.getOrNull(index) ?: ApduRule("", "9000")
                originalRule = rule; render()
            }, changedRules = { draft.card = it; hideKeyboard(); render() })
    }
    /**
     * 规则页先校验并写回卡片草稿；卡片页校验完整草稿后在后台提交仓库。
     * 只有持久化成功才删除草稿并返回 savedCardId，失败保留表单及错误提示。
     */
    private fun save() {
        if (ruleIndex != null) {
            runCatching { ruleForm.validated() }.onSuccess { validated ->
                val updated = draft.card.rules.toMutableList(); val index = ruleIndex!!
                if (index == -1) updated.add(validated) else updated[index] = validated
                draft.card = draft.card.copy(rules = updated); closeRule()
            }.onFailure { ruleForm.error(it.message ?: getString(R.string.editor_rule_validation_failed)) }
        } else {
            draft.card = form.snapshot()
            val card = runCatching { draft.validated() }.getOrElse { form.error(it.message ?: getString(R.string.editor_save_failed)); return }
            PackWork.run(this, { store.saveEditedCard(card, draft.originalKey); card }) { result ->
                result.onSuccess {
                    stateFile.delete(); setResult(RESULT_OK, Intent().putExtra("savedCardId", it.key)); finish()
                }.onFailure { form.error(it.message ?: getString(R.string.editor_save_failed)) }
            }
        }
    }
    /** 读取当前表单快照，存在修改时询问是否放弃；规则返回与整卡退出分别处理。 */
    private fun back() {
        if (ruleIndex != null) {
            rule = ruleForm.snapshot()
            if (rule != originalRule) confirmDiscard { closeRule() } else closeRule()
        } else {
            draft.card = form.snapshot()
            if (draft.dirty) confirmDiscard { discard() } else discard()
        }
    }
    /** 显示放弃修改确认，确认后才执行传入动作；继续编辑保留原输入。 */
    private fun confirmDiscard(discard: () -> Unit) {
        AlertDialog.Builder(this).setTitle(R.string.editor_discard_confirm)
            .setNegativeButton(R.string.editor_continue, null).setPositiveButton(R.string.editor_discard) { _, _ -> discard() }.show()
    }
    /** 删除本次私有草稿文件并关闭编辑页，不提交卡片内容。 */
    private fun discard() { stateFile.delete(); finish() }
    /** 隐藏键盘并清理规则子页状态，再返回卡片表单；整卡草稿继续保留。 */
    private fun closeRule() { hideKeyboard(); ruleIndex = null; rule = null; originalRule = null; render() }
    /** 请求收起输入法并清理内容焦点，避免切换表单后键盘和焦点停留。 */
    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(body.windowToken, 0)
        body.clearFocus()
    }
    /**
     * 保存当前表单到原子草稿文件，只把 token 放入 Bundle，避免长 APDU 挤占进程间传输空间。
     * Bundle 是 Android 的键值状态容器；Binder 是进程间通信机制，传输大小受系统限制。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        if (::draft.isInitialized && ::body.isInitialized) {
            if (ruleIndex == null) draft.card = form.snapshot() else rule = ruleForm.snapshot()
            // APDU 可能很长，草稿存应用私有文件；Bundle 只传标识，避免 Binder 大小限制。
            // 保存未完成内容，不能在页面重建时要求所有字段都已经符合正式卡片校验。
            val json = JSONObject().put("draft", draft.state())
            if (ruleIndex != null) json.put("ruleIndex", ruleIndex).put("rule", RuleCodec.encode(rule!!, draft = true)).put("originalRule", RuleCodec.encode(originalRule!!, draft = true))
            val atomic = android.util.AtomicFile(stateFile)
            val stream = atomic.startWrite()
            try {
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
            outState.putString("draftToken", token)
        }
        super.onSaveInstanceState(outState)
    }
}
