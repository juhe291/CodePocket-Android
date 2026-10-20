package com.codepocket.ide.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shared native surfaces. Platform text selection and SAF pickers remain owned by Android. */
object WorkbenchStyle {
    fun shape(context:Context,color:Int,border:Int=0,radius:Int=8)=GradientDrawable().apply {
        setColor(color);cornerRadius=IdeControls.dp(context,radius).toFloat()
        if(border!=0)setStroke(IdeControls.dp(context,1),border)
    }
    fun menuRow(context:Context,title:String,enabled:Boolean=true,checked:Boolean=false,destructive:Boolean=false):LinearLayout {
        val p=DesignTokens.forContext(context);val dp={v:Int->IdeControls.dp(context,v)}
        return LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48)
            setPadding(dp(12),dp(4),dp(12),dp(4));background=IdeControls.feedback(context,p)
            isEnabled=enabled;alpha=if(enabled)1f else .4f;isSelected=checked
            if(checked)background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((p.foreground and 0xffffff) or 0x20000000),shape(context,p.selection),null)
            val tint=if(destructive)p.error else p.muted
            addView(ImageView(context).apply {setImageDrawable(IdeIconDrawable(context,if(checked)IdeIcon.CHECK else IdeIconDrawable.forTitle(title),tint,dp(20)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(20),dp(20)))
            addView(TextView(context).apply {text=title.substringBefore("  ");textSize=14f;setTextColor(if(destructive)p.error else p.foreground);setPadding(dp(12),0,dp(8),0);maxLines=3},LinearLayout.LayoutParams(0,-2,1f))
            if(title.contains("  "))addView(TextView(context).apply {text=title.substringAfter("  ");textSize=11f;setTextColor(p.muted);typeface=Typeface.MONOSPACE},LinearLayout.LayoutParams(-2,-2))
            contentDescription=title+(if(checked)"，已选择" else "")
        }
    }
    fun content(view:View,p:IdePalette=DesignTokens.forContext(view.context)) {
        val dp={v:Int->IdeControls.dp(view.context,v)}
        when(view){
            is EditText->{view.setTextColor(p.foreground);view.setHintTextColor(p.muted);view.backgroundTintList=ColorStateList.valueOf(p.accent);view.minHeight=dp(48);view.textSize=14f}
            is Button->{view.setTextColor(p.accentText);view.isAllCaps=false;view.minHeight=dp(48);view.textSize=14f;view.background=IdeControls.feedback(view.context,p)}
            is Switch->{view.setTextColor(p.foreground);view.thumbTintList=ColorStateList.valueOf(p.accentText);view.minHeight=dp(48)}
            is CheckBox->{view.setTextColor(p.foreground);view.buttonTintList=ColorStateList.valueOf(p.accentText);view.minHeight=dp(48)}
            is TextView->{view.setTextColor(p.foreground);view.setLineSpacing(dp(2).toFloat(),1f);if(view.textSize/view.resources.displayMetrics.scaledDensity>20f)view.textSize=18f}
        }
        if(view is ViewGroup)for(i in 0 until view.childCount)content(view.getChildAt(i),p)
    }
    fun dialog(dialog:AlertDialog){
        val context=dialog.context;val p=DesignTokens.forContext(context)
        dialog.window?.setBackgroundDrawable(shape(context,p.overlay,p.border,12))
        dialog.window?.decorView?.let{content(it,p)}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button ->
            if(button.text.toString() in listOf("删除","结束","覆盖","放弃修改"))button.setTextColor(p.error)
        }
        dialog.listView?.let { list ->
            val original=list.adapter?:return@let
            val checkedPosition=list.checkedItemPosition
            val values=(0 until original.count).map{original.getItem(it).toString()}
            list.divider=null;list.setPadding(0,IdeControls.dp(context,4),0,IdeControls.dp(context,4))
            list.adapter=object:BaseAdapter(){
                override fun getCount()=values.size
                override fun getItem(position:Int)=values[position]
                override fun getItemId(position:Int)=position.toLong()
                override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=menuRow(context,values[position],checked=position==checkedPosition,destructive=values[position]=="删除"||values[position]=="结束会话")
            }
            if(checkedPosition>=0)list.setItemChecked(checkedPosition,true)
        }
    }
}
