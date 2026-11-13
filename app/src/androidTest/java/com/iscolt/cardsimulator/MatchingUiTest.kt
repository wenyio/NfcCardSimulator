package com.iscolt.cardsimulator

import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iscolt.cardsimulator.core.*
import com.iscolt.cardsimulator.ui.UiKit
import com.iscolt.cardsimulator.ui.cards.RuleEditorPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchingUiTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun visible(v: View): Boolean = v.visibility == View.VISIBLE &&
        ((v.parent as? View)?.let(::visible) ?: true)

    @Test fun modesShowRelevantFieldsAndPreserveMaskWhileSwitching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var page: RuleEditorPage
            lateinit var parent: LinearLayout
            lateinit var mode: Spinner
            lateinit var mask: EditText
            scenario.onActivity { activity ->
                parent = LinearLayout(activity)
                page = RuleEditorPage(UiKit(activity))
                page.show(parent, ApduRule("00CA000000", "9000"))
                activity.setContentView(parent)
                mode = views(parent).filterIsInstance<Spinner>().single()
                mask = views(parent).filterIsInstance<EditText>().single { it.hint == "掩码（与命令等长）" }
                assertEquals("完整匹配", mode.selectedItem)
                assertFalse(visible(mask))
                assertEquals(MatchMode.EXACT, page.validated().mode)
                mode.setSelection(MatchMode.MASK.ordinal)
            }
            scenario.onActivity {
                assertTrue(visible(mask))
                mask.setText("FFFFFFFF00")
                assertEquals("FFFFFFFF00", page.validated().mask)
                assertEquals(MatchMode.MASK, page.validated().mode)
                mode.setSelection(MatchMode.PREFIX.ordinal)
            }
            scenario.onActivity {
                assertFalse(visible(mask))
                assertEquals(MatchMode.PREFIX, page.validated().mode)
                assertTrue(views(parent).filterIsInstance<TextView>().any {
                    visible(it) && it.text.toString().contains("不会重算密文")
                })
                mode.setSelection(MatchMode.MASK.ordinal)
            }
            scenario.onActivity {
                assertTrue(visible(mask)); assertEquals("FFFFFFFF00", mask.text.toString())
                mode.setSelection(MatchMode.EXACT.ordinal)
            }
            scenario.onActivity {
                assertFalse(visible(mask))
                assertFalse(views(parent).filterIsInstance<TextView>().any {
                    visible(it) && it.text.toString().contains("不会重算密文")
                })
                assertEquals(MatchMode.EXACT, page.validated().mode)
            }
        }
    }
}
