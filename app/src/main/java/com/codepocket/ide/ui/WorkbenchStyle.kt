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
    fun menuRow(context:Context,title:String,enabled:Boolean=true,checked:Boolean=false,destructive:Boolean=false,
        iconDrawable:android.graphics.drawable.Drawable?=null):LinearLayout {
        val p=DesignTokens.forContext(context);val dp={v:Int->IdeControls.dp(context,v)}
        val name=TextView(context).apply {
            text=title.substringBefore("  ");textSize=13f;setTextColor(if(destructive)p.error else p.foreground);maxLines=3
        }
        val shortcut=if(title.contains("  "))TextView(context).apply {
            text=title.substringAfter("  ");textSize=11f;setTextColor(p.muted);tag=SurfaceTextTone.MUTED;typeface=Typeface.MONOSPACE
        }else null
        val words=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),0,0,0)}
        val nameParams=LinearLayout.LayoutParams(0,-2,1f)
        val shortcutParams=LinearLayout.LayoutParams(-2,-2)
        if(shortcut!=null){words.addView(name,nameParams);words.addView(shortcut,shortcutParams)}
        else name.setPadding(dp(12),0,dp(8),0)
        return object:LinearLayout(context){
            private var stacked:Boolean?=null
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
                val stack=resources.configuration.fontScale>1.3f || MeasureSpec.getSize(widthMeasureSpec)/resources.displayMetrics.density<300
                if(shortcut!=null&&stacked!=stack){
                    stacked=stack;words.orientation=if(stack)VERTICAL else HORIZONTAL
                    nameParams.width=if(stack)-1 else 0;nameParams.weight=if(stack)0f else 1f;name.layoutParams=nameParams
                    name.setPadding(0,0,if(stack)0 else dp(8),0)
                    shortcutParams.width=if(stack)-1 else -2;shortcut.layoutParams=shortcutParams;shortcut.setPadding(0,if(stack)dp(3)else 0,0,0)
                }
                super.onMeasure(widthMeasureSpec,heightMeasureSpec)
            }
        }.apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48)
            setPadding(dp(12),dp(6),dp(12),dp(6));background=IdeControls.feedback(context,p)
            isEnabled=enabled;alpha=if(enabled)1f else .4f;isSelected=checked
            if(checked)background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((p.foreground and 0xffffff) or 0x20000000),shape(context,p.selection),null)
            val glyph=if(checked)IdeIcon.CHECK else IdeIconDrawable.forTitle(title)
            val tint=if(destructive)p.error else if(glyph in setOf(IdeIcon.CPP,IdeIcon.PYTHON,IdeIcon.HEADER))IdeIconDrawable.color(glyph,p) else p.muted
            addView(ImageView(context).apply {
                setImageDrawable(if(!checked&&iconDrawable!=null)iconDrawable else IdeIconDrawable(context,glyph,tint,dp(20)))
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(20),dp(20)))
            addView(if(shortcut==null)name else words,LinearLayout.LayoutParams(0,-2,1f))
            contentDescription=title+(if(checked)"，已选择" else "")
        }
    }
    fun content(view:View,p:IdePalette=DesignTokens.forContext(view.context)) {
        val dp={v:Int->IdeControls.dp(view.context,v)}
        when(view){
            is EditText->{
                view.setTextColor(p.foreground);view.setHintTextColor(p.muted);view.minHeight=dp(48);view.textSize=14f
                view.backgroundTintList=null
                view.background=android.graphics.drawable.StateListDrawable().apply{
                    addState(intArrayOf(android.R.attr.state_focused),shape(view.context,p.editor,p.accent,6))
                    addState(intArrayOf(),shape(view.context,p.panel,p.border,6))
                }
                view.setPadding(dp(12),dp(6),dp(12),dp(6))
            }
            is Button->{view.setTextColor(p.accentText);view.isAllCaps=false;view.minHeight=dp(48);view.textSize=14f;view.background=IdeControls.feedback(view.context,p)}
            is Switch->{view.setTextColor(p.foreground);view.thumbTintList=ColorStateList.valueOf(p.accentText);view.minHeight=dp(48)}
            is CheckBox->{view.setTextColor(p.foreground);view.buttonTintList=ColorStateList.valueOf(p.accentText);view.minHeight=dp(48)}
            is TextView->{view.setTextColor(when(view.tag){SurfaceTextTone.MUTED->p.muted;SurfaceTextTone.DANGER->p.error;else->p.foreground});view.setLineSpacing(dp(2).toFloat(),1f);if(view.textSize/view.resources.displayMetrics.scaledDensity>20f)view.textSize=18f}
        }
        if(view is ViewGroup)for(i in 0 until view.childCount)content(view.getChildAt(i),p)
    }
    fun dialog(dialog:AlertDialog){
        val context=dialog.context;val p=DesignTokens.forContext(context)
        dialog.window?.setBackgroundDrawable(shape(context,p.overlay,p.border,6))
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
