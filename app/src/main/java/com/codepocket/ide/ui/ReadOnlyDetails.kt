package com.codepocket.ide.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.animation.DialogMotion

/** Shared reader chrome only. Text, clipboard actions and the underlying draft stay caller-owned. */
object ReadOnlyDetails {
    @JvmStatic
    fun show(context:Context,title:String,scope:String,text:String,icon:IdeIcon,monospace:Boolean,
        animations:Boolean,description:String?,copyLabel:String?,copy:Runnable?):AlertDialog {
        val activity=generateSequence(context){(it as? ContextWrapper)?.baseContext?.takeIf{base->base!==it}}
            .filterIsInstance<Activity>().firstOrNull()
        val host=activity?.findViewById<View>(android.R.id.content)
        val dp={value:Int->IdeControls.dp(context,value)}
        val palette=DesignTokens.forContext(context)
        val titleView=TextView(context).apply{this.text=title;textSize=16f;setTypeface(null,Typeface.BOLD);setTextColor(palette.foreground)}
        val scopeView=WorkbenchForms.caption(context,scope).apply{IdeControls.describe(this,scope)}
        val words=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)
            addView(titleView,LinearLayout.LayoutParams(-1,-2));addView(scopeView,LinearLayout.LayoutParams(-1,-2))}
        val mark=ImageView(context).apply{setImageDrawable(IdeIconDrawable(context,icon,palette.accentText,dp(20)))
            setPadding(dp(8),dp(8),dp(8),dp(8));background=WorkbenchStyle.shape(context,palette.panel,palette.border,6)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        val line=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;addView(mark,LinearLayout.LayoutParams(dp(36),dp(36)))
            addView(words,LinearLayout.LayoutParams(0,-2,1f))}
        val divider=View(context).apply{setBackgroundColor(palette.border);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        val header=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL
            addView(line,LinearLayout.LayoutParams(-1,-2));addView(divider,LinearLayout.LayoutParams(-1,dp(1)))}
        var compactHeader:Boolean?=null
        fun heading(compact:Boolean){
            if(compactHeader==compact)return
            compactHeader=compact
            header.setPadding(dp(if(compact)12 else 20),dp(if(compact)4 else 18),dp(if(compact)12 else 20),dp(4))
            words.orientation=if(compact)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            titleView.textSize=if(compact)14f else 16f
            titleView.maxLines=if(compact)1 else Int.MAX_VALUE
            titleView.ellipsize=if(compact)TextUtils.TruncateAt.END else null
            IdeControls.describe(titleView,title)
            titleView.layoutParams=LinearLayout.LayoutParams(if(compact)0 else -1,-2,if(compact)1f else 0f)
            scopeView.layoutParams=LinearLayout.LayoutParams(if(compact)0 else -1,-2,if(compact)1f else 0f)
            scopeView.setPadding(dp(if(compact)8 else 0),dp(if(compact)0 else 4),0,0)
            scopeView.maxLines=if(compact)1 else 3;scopeView.ellipsize=TextUtils.TruncateAt.MIDDLE
            mark.visibility=if(compact)View.GONE else View.VISIBLE
            words.setPadding(dp(if(compact)0 else 12),0,0,0)
            divider.layoutParams=LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(if(compact)4 else 12);bottomMargin=dp(if(compact)4 else 8)}
        }
        var bodyLimit=dp(480)
        var pendingScroll:Int?=null
        val body=WorkbenchForms.details(context,text,monospace).getChildAt(0) as TextView
        (body.parent as ScrollView).removeView(body)
        description?.let{body.contentDescription=it}
        val scroll=object:ScrollView(context){
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
                val limit=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED)bodyLimit
                    else minOf(bodyLimit,MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST))
            }
            override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int){
                super.onLayout(changed,left,top,right,bottom)
                // Selectable TextView focus would otherwise pull a resized reader back to its selection.
                pendingScroll?.let{position->scrollTo(scrollX,position)}
            }
            override fun requestChildRectangleOnScreen(child:View,rectangle:Rect,immediate:Boolean):Boolean =
                if(pendingScroll!=null)false else super.requestChildRectangleOnScreen(child,rectangle,immediate)
        }.apply{addView(body);isFillViewport=false}
        val restoreScroll=object:ViewTreeObserver.OnPreDrawListener{
            override fun onPreDraw():Boolean{
                scroll.viewTreeObserver.removeOnPreDrawListener(this)
                // TextView may request its focused selection again during pre-draw, after onLayout.
                scroll.post{pendingScroll?.let{position->scroll.scrollTo(scroll.scrollX,position)};pendingScroll=null}
                return true
            }
        }
        val builder=AlertDialog.Builder(context).setCustomTitle(header).setView(scroll)
        if(copy!=null && copyLabel!=null)builder.setNegativeButton("关闭",null).setPositiveButton(copyLabel){_,_->copy.run()}
        else builder.setPositiveButton("关闭",null)
        val dialog=builder.create()
        heading(false)
        dialog.setOnKeyListener{_,key,event->
            if(key==KeyEvent.KEYCODE_BACK || key==KeyEvent.KEYCODE_ESCAPE){
                if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)dialog.cancel()
                true
            }else false
        }
        DialogMotion.show(dialog,animations)
        var lastWidth=0
        var lastHeight=0
        fun resize(){
            if(!dialog.isShowing)return
            val width=minOf(dp(640),((host?.width?.takeIf{it>0}?:context.resources.displayMetrics.widthPixels)-dp(32)).coerceAtLeast(dp(48)))
            val availableHeight=host?.height?.takeIf{it>0}?:context.resources.displayMetrics.heightPixels
            if(lastWidth!=0 && (lastWidth!=width || lastHeight!=availableHeight)){
                if(pendingScroll==null)pendingScroll=scroll.scrollY
                scroll.viewTreeObserver.removeOnPreDrawListener(restoreScroll)
                scroll.viewTreeObserver.addOnPreDrawListener(restoreScroll)
            }
            lastWidth=width;lastHeight=availableHeight
            val compact=availableHeight<dp(360)
            heading(compact)
            val height=availableHeight-if(compact)0 else dp(32)
            // Measure wrapped title and scaled native buttons before allocating the scrolling viewport.
            header.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val buttons=dialog.getButton(AlertDialog.BUTTON_POSITIVE).parent as View
            buttons.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val decor=dialog.window!!.decorView
            val limit=(height-header.measuredHeight-buttons.measuredHeight-decor.paddingTop-decor.paddingBottom-dp(8)).coerceAtLeast(0)
            if(bodyLimit!=limit){bodyLimit=limit;scroll.requestLayout()}
            if(dialog.window?.attributes?.width!=width)dialog.window?.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT)
        }
        resize()
        val listener=View.OnLayoutChangeListener{_,_,_,_,_,_,_,_,_->resize()}
        host?.addOnLayoutChangeListener(listener)
        dialog.setOnDismissListener{
            host?.removeOnLayoutChangeListener(listener)
            if(scroll.viewTreeObserver.isAlive)scroll.viewTreeObserver.removeOnPreDrawListener(restoreScroll)
            pendingScroll=null
        }
        return dialog
    }
}
