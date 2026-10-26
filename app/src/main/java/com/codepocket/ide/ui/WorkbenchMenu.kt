package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import com.codepocket.ide.animation.Animations
import com.codepocket.ide.animation.MotionEnvironment

/** Native command identity/callbacks with optional fixed category context above the scrollable rows. */
class WorkbenchMenu(context:Context,private val anchor:View,private val animate:Boolean):PopupMenu(context,anchor){
    private var window:PopupWindow?=null
    private data class Appearance(val icon:IdeIcon,val destructive:Boolean)
    private val appearances=mutableMapOf<Int,Appearance>()
    private var heading:String?=null
    private var returnAction:(()->Unit)?=null
    private var returnDescription="返回上一级菜单"
    fun setItemAppearance(itemId:Int,icon:IdeIcon,destructive:Boolean=false){appearances[itemId]=Appearance(icon,destructive)}
    fun setHeading(title:String,backDescription:String="返回上一级菜单",onBack:(()->Unit)?=null){
        heading=title;returnDescription=backDescription;returnAction=onBack
    }
    private fun returnToParent(){
        if(window?.isShowing!=true)return
        val action=returnAction?:return
        dismiss()
        if(anchor.isAttachedToWindow)action()
    }
    private fun handleMenuKey(event:KeyEvent):Boolean {
        if(event.keyCode==KeyEvent.KEYCODE_ESCAPE){
            if(event.action==KeyEvent.ACTION_UP&&!event.isCanceled)dismiss()
            return true
        }
        if(event.keyCode==KeyEvent.KEYCODE_DPAD_LEFT&&returnAction!=null){
            if(event.action==KeyEvent.ACTION_UP&&!event.isCanceled)returnToParent()
            return true
        }
        return false
    }
    override fun show(){
        dismiss()
        val p=DesignTokens.forContext(anchor.context);val dp={v:Int->IdeControls.dp(anchor.context,v)}
        val rows=LinearLayout(anchor.context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(6),dp(4),dp(6))}
        var previousDanger:Boolean?=null
        var previousGroup:Int?=null
        for(i in 0 until menu.size()){
            val item=menu.getItem(i);if(!item.isVisible)continue
            val appearance=appearances[item.itemId]
            val destructive=appearance?.destructive ?: (item.title.toString() in listOf("删除","删除当前文件","结束会话","结束当前 Shell"))
            if(previousDanger!=null&&(previousDanger!=destructive||previousGroup!=item.groupId))rows.addView(View(anchor.context).apply{setBackgroundColor(p.border)},LinearLayout.LayoutParams(-1,dp(1)))
            previousDanger=destructive;previousGroup=item.groupId
            rows.addView(WorkbenchStyle.menuRow(anchor.context,item.title.toString(),item.isEnabled,item.isChecked,destructive,appearance?.let{IdeIconDrawable(anchor.context,it.icon,if(it.destructive)p.error else p.muted,dp(20))}).apply {
                isFocusable=item.isEnabled
                setOnKeyListener{_,_,event->handleMenuKey(event)}
                setOnClickListener{if(item.isEnabled){dismiss();menu.performIdentifierAction(item.itemId,0)}}
            },LinearLayout.LayoutParams(-1,-2))
        }
        val display=anchor.resources.displayMetrics
        val frame=android.graphics.Rect();anchor.getWindowVisibleDisplayFrame(frame)
        val availableWidth=minOf(anchor.rootView.width.takeIf{it>0}?:display.widthPixels,frame.width().takeIf{it>0}?:display.widthPixels)
        val availableHeight=minOf(anchor.rootView.height.takeIf{it>0}?:display.heightPixels,frame.height().takeIf{it>0}?:display.heightPixels)
        val width=minOf(dp(340),(availableWidth-dp(24)).coerceAtLeast(dp(48)))
        val maximumHeight=(availableHeight*.75f).toInt().coerceAtLeast(dp(48))
        val scroll=object:ScrollView(anchor.context){
            override fun dispatchKeyEvent(event:KeyEvent):Boolean = handleMenuKey(event)||super.dispatchKeyEvent(event)
        }.apply {isFillViewport=false;isFocusableInTouchMode=true;addView(rows)}
        val header=heading?.takeIf{width>=dp(96)&&maximumHeight>=dp(100)}?.let { title ->
            LinearLayout(anchor.context).apply {
                orientation=LinearLayout.VERTICAL;setBackgroundColor(p.panel)
                val line=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48)}
                if(returnAction!=null)line.addView(IdeControls.icon(context,p,IdeIcon.BACK,returnDescription){returnToParent()},LinearLayout.LayoutParams(dp(48),dp(48)))
                line.addView(TextView(context).apply {
                    text=title;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(p.foreground)
                    includeFontPadding=false;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
                    setPadding(dp(if(returnAction==null)16 else 8),dp(8),dp(12),dp(8))
                },LinearLayout.LayoutParams(0,-2,1f))
                addView(line,LinearLayout.LayoutParams(-1,-2))
                addView(View(context).apply{setBackgroundColor(p.border);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(-1,dp(1)))
            }
        }
        rows.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        header?.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((maximumHeight-dp(48)).coerceAtLeast(0),View.MeasureSpec.AT_MOST))
        val height=minOf(rows.measuredHeight+(header?.measuredHeight?:0),maximumHeight)
        val content:View=if(header==null)scroll.apply{background=WorkbenchStyle.shape(context,p.overlay,p.border,6)} else
            object:LinearLayout(anchor.context){
                override fun dispatchKeyEvent(event:KeyEvent):Boolean = handleMenuKey(event)||super.dispatchKeyEvent(event)
            }.apply {
                orientation=LinearLayout.VERTICAL;clipToOutline=true;background=WorkbenchStyle.shape(context,p.overlay,p.border,6)
                addView(header,LinearLayout.LayoutParams(-1,header.measuredHeight))
                addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
            }
        val popup=PopupWindow(content,width,height,true).apply {setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true;elevation=dp(4).toFloat();inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED}
        window=popup;popup.setOnDismissListener{Animations.cancelTree(content);if(window===popup)window=null}
        popup.showAsDropDown(anchor,0,dp(4),Gravity.END)
        scroll.requestFocus()
        Animations.fadeIn(content,90,enabled=MotionEnvironment.enabled(anchor.context,animate))
    }
    override fun dismiss(){window?.dismiss();window=null;super.dismiss()}
}
