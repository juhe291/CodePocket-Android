package com.codepocket.ide

import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkbenchFieldStyleTest {
    @Test fun sharedFieldStyleKeepsNativeImeSelectionAndComposition(){
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val activity=c.get();val field=EditText(activity)
        activity.setContentView(field);field.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        field.imeOptions=EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI;field.setText("abcd");field.setSelection(2);field.requestFocus()
        val content=field.text;val input=field.inputType;val options=field.imeOptions;WorkbenchStyle.content(field)
        assertSame(content,field.text);assertEquals(2,field.selectionStart);assertEquals(input,field.inputType);assertEquals(options,field.imeOptions);assertNull(field.backgroundTintList)
        val ic=field.onCreateInputConnection(EditorInfo());assertTrue(ic.setComposingText("拼",1));val start=BaseInputConnection.getComposingSpanStart(field.text);assertTrue(start>=0)
        WorkbenchStyle.content(field);assertSame(content,field.text);assertEquals(start,BaseInputConnection.getComposingSpanStart(field.text));assertTrue(ic.finishComposingText())
        assertEquals("ab拼cd",field.text.toString());c.pause().stop().destroy()
    }
    @Test fun focusedOutlineAndLargeFontTargetsWorkInBothThemes(){
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val activity=c.get();RuntimeEnvironment.setFontScale(2f)
        try{for(dark in listOf(true,false)){
            val root=LinearLayout(activity);val field=EditText(activity);val other=EditText(activity);root.addView(field);root.addView(other);activity.setContentView(root)
            field.setText("text");WorkbenchStyle.content(field,DesignTokens.palette(dark));other.requestFocus();field.refreshDrawableState();val idle=field.background.current
            field.requestFocus();field.refreshDrawableState();assertNotSame(idle,field.background.current);assertNull(field.backgroundTintList)
            field.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            assertTrue(field.measuredHeight>=48);assertTrue(field.measuredHeight>=field.compoundPaddingTop+field.compoundPaddingBottom+field.paint.fontSpacing.toInt())
            assertEquals("text",field.text.toString())
        }}finally{RuntimeEnvironment.setFontScale(1f);c.pause().stop().destroy()}
    }
}
