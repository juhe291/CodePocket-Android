package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.codepocket.ide.animation.Animations
import com.codepocket.ide.animation.MotionEnvironment

/** Native menu identity/callbacks, with a shared touch-sized presentation. Keeps platform menu identities and keyboard navigation. */
class WorkbenchMenu(context:Context,private val anchor:View,private val animate:Boolean):PopupMenu(context,anchor){
    private var window:PopupWindow?=null
    override fun show(){
        dismiss()
        val p=DesignTokens.forContext(anchor.context);val dp={v:Int->IdeControls.dp(anchor.context,v)}
        val rows=LinearLayout(anchor.context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4))}
        var danger=false
        for(i in 0 until menu.size()){
            val item=menu.getItem(i);if(!item.isVisible)continue
            val destructive=item.title.toString() in listOf("删除","删除当前文件","结束会话","结束当前 Shell")
            if(destructive&&!danger){rows.addView(View(anchor.context).apply{setBackgroundColor(p.border)},LinearLayout.LayoutParams(-1,dp(1)));danger=true}
            rows.addView(WorkbenchStyle.menuRow(anchor.context,item.title.toString(),item.isEnabled,item.isChecked,destructive).apply {
                isFocusable=item.isEnabled
                setOnKeyListener{_,key,event->if(key==android.view.KeyEvent.KEYCODE_ESCAPE&&event.action==android.view.KeyEvent.ACTION_UP){dismiss();true}else false}
                setOnClickListener{if(item.isEnabled){dismiss();menu.performIdentifierAction(item.itemId,0)}}
            },LinearLayout.LayoutParams(-1,-2))
        }
        val display=anchor.resources.displayMetrics
        val frame=android.graphics.Rect();anchor.getWindowVisibleDisplayFrame(frame)
        val availableWidth=minOf(anchor.rootView.width.takeIf{it>0}?:display.widthPixels,frame.width().takeIf{it>0}?:display.widthPixels)
        val availableHeight=minOf(anchor.rootView.height.takeIf{it>0}?:display.heightPixels,frame.height().takeIf{it>0}?:display.heightPixels)
        val width=minOf(dp(340),(availableWidth-dp(24)).coerceAtLeast(dp(48)))
        val scroll=ScrollView(anchor.context).apply {isFillViewport=false;addView(rows);background=WorkbenchStyle.shape(context,p.overlay,p.border,8)}
        rows.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val height=minOf(rows.measuredHeight,(availableHeight*.75f).toInt().coerceAtLeast(dp(48)))
        val popup=PopupWindow(scroll,width,height,true).apply {setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true;elevation=dp(8).toFloat();inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED}
        window=popup;popup.setOnDismissListener{Animations.cancelTree(scroll);if(window===popup)window=null}
        popup.showAsDropDown(anchor,0,dp(4),Gravity.END)
        Animations.fadeIn(scroll,90,enabled=MotionEnvironment.enabled(anchor.context,animate))
    }
    override fun dismiss(){window?.dismiss();window=null;super.dismiss()}
}
