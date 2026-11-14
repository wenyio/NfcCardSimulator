package com.iscolt.cardsimulator.ui.cards

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.iscolt.cardsimulator.R
import com.iscolt.cardsimulator.SimulatorApp
import com.iscolt.cardsimulator.io.DocumentController
import com.iscolt.cardsimulator.ui.UiKit

/** 独立管理入口：页面只负责导航与文件生命周期，列表和操作各自维护。 */
class CardPackManagerActivity : AppCompatActivity() {
    private val store get() = (application as SimulatorApp).store
    private lateinit var documents: DocumentController
    private lateinit var page: CardPackManagerPage
    private lateinit var actions: CardPackActions
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private var restoredScroll = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = UiKit(this)
        // 导入成功已经切换目标卡包，关闭管理页后由首页统一刷新。
        documents = DocumentController(this, store, { finish() }, ::message)
        page = CardPackManagerPage(ui)
        actions = CardPackActions(ui, store, ::render, documents::exportPack, ::message)
        restoredScroll = savedInstanceState?.getInt("scrollY") ?: 0
        with(ui) {
            body = column().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
            scroll = ScrollView(this@CardPackManagerActivity).apply { addView(body); isFillViewport = true }
            val toolbar = shell(this@CardPackManagerActivity, scroll)
            toolbar.addView(icon(R.drawable.ic_back, getString(R.string.action_back)) { finish() }.apply { setColorFilter(Color.WHITE) })
            toolbar.addView(label(getString(R.string.pack_manage_title), 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            toolbar.addView(icon(R.drawable.ic_add, getString(R.string.pack_add)) { anchor ->
                PopupMenu(this@CardPackManagerActivity, anchor).apply {
                    menu.add(R.string.pack_new).setOnMenuItemClickListener {
                        CardPackCreateDialog(ui, store) { finish() }.show(); true
                    }
                    menu.add(R.string.action_import_file).setOnMenuItemClickListener { documents.import(); true }
                    show()
                }
            }.apply { setColorFilter(Color.WHITE) })
        }
    }

    override fun onResume() {
        super.onResume(); render()
    }

    private fun render() {
        val position = maxOf(scroll.scrollY, restoredScroll); restoredScroll = 0
        page.show(body, store.packs(), store.activePackId, use = { pack ->
            // 切包只改变首页列表，不选择卡片、不请求 HCE 模拟。
            runCatching { store.switchPack(pack.id); finish() }.onFailure { message(it.message ?: getString(R.string.pack_switch_failed)) }
        }, more = actions::show)
        scroll.post { scroll.scrollTo(0, position) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scrollY", scroll.scrollY); super.onSaveInstanceState(outState)
    }

    override fun onDestroy() { documents.close(); super.onDestroy() }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
}
