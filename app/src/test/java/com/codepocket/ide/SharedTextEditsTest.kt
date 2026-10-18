package com.codepocket.ide

import com.codepocket.editor.surface.TextEdits
import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Test

/** The production Kotlin facade must keep using the same rules as the Java Sora adapter. */
class SharedTextEditsTest {
    private fun check(text: String, start: Int, end: Int, replacement: String, a: Int, b: Int = a): EditResult {
        val frame = EditorFrame(EditorSnapshot("file", 1, text, 0, 0), false, true)
        return EditorTransactions.check(frame, EditorTransaction(frame.buffer, TextChange(start,end,replacement,a,b)),200000)
    }
    @Test fun replacementValidityAndSelectionSeamsUseSharedRules() {
        assertEquals(EditResult.INVALID_TEXT,check("abc",0,0,"\uD800",1))
        assertEquals(EditResult.INVALID_RANGE,check("\rX\n",1,2,"",1))
        assertEquals(EditResult.APPLIED,check("\rX\n",1,2,"",2))
        assertEquals(EditResult.INVALID_RANGE,check("abc",0,0,"😀",1))
    }
    @Test fun existingTextIsNotNormalizedOrRejectedByAnUnrelatedEdit() {
        assertEquals(EditResult.APPLIED,check("\uD800abc\r\n",1,2,"z",2))
        assertEquals(EditResult.APPLIED,check("😀\r\n",2,4,"\n",3))
    }
    @Test fun sharedRestorationMatchesProductionForBothSelectionDirections() {
        val text="a😀\r\nb"
        for(a in -2..text.length+2) for(b in -2..text.length+2) {
            val restored=EditorNavigation.restoredSelection(text,a,b)
            assertArrayEquals(intArrayOf(restored.start,restored.end),TextEdits.restoreSelection(text,a,b))
        }
    }
    @Test fun validationDoesNotRequireToStringOrSubSequence() {
        val source=object:CharSequence {
            override val length=3
            override fun get(index:Int)="abc"[index]
            override fun subSequence(startIndex:Int,endIndex:Int):CharSequence=error("unexpected copy")
            override fun toString():String=error("unexpected copy")
        }
        assertEquals(TextEdits.Result.APPLIED,TextEdits.check(source,0,0,1,2,"z",2,2,200000))
        assertEquals(TextEdits.Result.SELECTION_ONLY,TextEdits.check(source,0,0,1,2,"b",1,1,200000))
    }
    @Test fun sameTextAndSelectionKeepTheirOriginalResultCategories() {
        assertEquals(EditResult.UNCHANGED,check("abc",1,2,"b",0))
        assertEquals(EditResult.SELECTION_ONLY,check("abc",1,2,"b",1,2))
        assertEquals(EditResult.APPLIED,check("abc",1,2,"z",1,2))
    }
    @Test fun lengthBudgetIsCheckedBeforeDispatch() {
        assertEquals(EditResult.LIMIT_EXCEEDED,check("x".repeat(200000),0,0,"z",1))
        assertEquals(EditResult.APPLIED,check("x".repeat(200000),0,1,"z",1))
        assertEquals(TextEdits.Result.LIMIT_EXCEEDED,TextEdits.check("",0,0,0,0,"",0,0,-1))
    }
}
