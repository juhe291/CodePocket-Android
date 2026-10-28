package com.codepocket.ide.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.R

/** Display metadata only. List positions continue to dispatch the caller's original snapshot. */
data class WorkbenchChoice(val key:String,val title:String,val detail:String,val icon:IdeIcon,
    val checked:Boolean=false,val startsGroup:Boolean=false,val description:String?=null,val monospaceTitle:Boolean=false)

class WorkbenchChoiceAdapter(private val context:Context,choices:List<WorkbenchChoice>):BaseAdapter(){
    private val choices=choices.toList()
    override fun getCount()=choices.size
    override fun getItem(position:Int)=choices[position].title
    override fun getItemId(position:Int)=position.toLong()
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View =
        (convertView as? ChoiceRow ?: ChoiceRow(context)).apply{bind(choices[position])}

    private class ChoiceRow(context:Context):LinearLayout(context){
        private val p=DesignTokens.forContext(context)
        private fun dp(value:Int)=IdeControls.dp(context,value)
        private val divider=View(context).apply{setBackgroundColor(p.border);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
        private val line=LinearLayout(context).apply{
            gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(64);setPadding(dp(16),dp(10),dp(16),dp(10))
        }
        private val glyph=ImageView(context).apply{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
        private val title=TextView(context).apply{
            textSize=14f;setTextColor(p.foreground);includeFontPadding=false;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        private val detail=TextView(context).apply{
            textSize=12f;setTextColor(p.muted);tag=SurfaceTextTone.MUTED;includeFontPadding=false
            maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setPadding(0,dp(5),0,0)
        }
        private val check=ImageView(context).apply{
            setImageDrawable(IdeIconDrawable(context,IdeIcon.CHECK,p.foreground,dp(18)))
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        init {
            orientation=VERTICAL
            addView(divider,LayoutParams(-1,dp(1)).apply{topMargin=dp(6);bottomMargin=dp(6)})
            line.addView(glyph,LayoutParams(dp(20),dp(24)))
            val words=LinearLayout(context).apply{orientation=VERTICAL;setPadding(dp(12),0,dp(12),0);addView(title);addView(detail)}
            line.addView(words,LayoutParams(0,-2,1f));line.addView(check,LayoutParams(dp(20),dp(20)))
            addView(line,LayoutParams(-1,-2))
        }
        fun bind(choice:WorkbenchChoice){
            title.typeface=if(choice.monospaceTitle)android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
            title.text=choice.title;detail.text=choice.detail;detail.visibility=if(choice.detail.isBlank())GONE else VISIBLE
            glyph.setImageDrawable(IdeIconDrawable(context,choice.icon,IdeIconDrawable.color(choice.icon,p),dp(20)))
            divider.visibility=if(choice.startsGroup)VISIBLE else GONE;check.visibility=if(choice.checked)VISIBLE else INVISIBLE
            isSelected=choice.checked
            line.background=if(choice.checked)WorkbenchStyle.shape(context,p.selection,0,4)else IdeControls.feedback(context,p)
            setTag(R.id.workbench_key,"choice:${choice.key}")
            val description=choice.description ?: (choice.title+(if(choice.detail.isBlank())"" else "，${choice.detail}"))
            IdeControls.describe(this,description+(if(choice.checked)"，已选择" else ""),tooltip=false)
        }
    }
}
