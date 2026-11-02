package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.codepocket.ide.R

/** Local Lucide 0.468.0 vectors; original type badges. See assets/icon-licenses. */
enum class IdeIcon(val resource: Int) {
    CHEVRON_RIGHT(R.drawable.lucide_chevron_right),
    SEARCH(R.drawable.lucide_search),
    SAVE(R.drawable.lucide_save),
    UNDO(R.drawable.lucide_undo_2),
    REDO(R.drawable.lucide_redo_2),
    ADD_FILE(R.drawable.lucide_file_plus_2),
    ADD_FOLDER(R.drawable.lucide_folder_plus),
    CLEAR(R.drawable.lucide_eraser),
    EXPLORER(R.drawable.lucide_files),
    MORE(R.drawable.lucide_ellipsis_vertical),
    RUN(R.drawable.lucide_play),
    STOP(R.drawable.lucide_square),
    CLOSE(R.drawable.lucide_x),
    MAXIMIZE(R.drawable.lucide_maximize_2),
    RESTORE(R.drawable.lucide_minimize_2),
    INPUT(R.drawable.lucide_keyboard),
    FOLDER(R.drawable.lucide_folder),
    FOLDER_OPEN(R.drawable.lucide_folder_open),
    FILE(R.drawable.lucide_file),
    PYTHON(R.drawable.lucide_file),
    CPP(R.drawable.lucide_file),
    HEADER(R.drawable.lucide_file),
    CONFIG(R.drawable.lucide_file_json),
    TEXT(R.drawable.lucide_file_text),
    TERMINAL(R.drawable.lucide_terminal),
    ADD(R.drawable.lucide_plus),
    SETTINGS(R.drawable.lucide_settings_2),
    LOCATE(R.drawable.lucide_crosshair),
    COLLAPSE(R.drawable.lucide_list_collapse),
    MOVE(R.drawable.lucide_move),
    RENAME(R.drawable.lucide_pencil),
    DELETE(R.drawable.lucide_trash_2),
    COPY(R.drawable.lucide_copy),
    PASTE(R.drawable.lucide_clipboard),
    INFO(R.drawable.lucide_info),
    HELP(R.drawable.lucide_circle_help),
    BACK(R.drawable.lucide_arrow_left),
    NEXT(R.drawable.lucide_arrow_right),
    CHECK(R.drawable.lucide_check),
    PALETTE(R.drawable.lucide_palette),
    DOWN(R.drawable.lucide_chevron_down),
    EXPORT(R.drawable.lucide_download),
    IMPORT(R.drawable.lucide_upload),
    REFRESH(R.drawable.lucide_refresh_cw),
    FILTER(R.drawable.lucide_list_filter),
    CODE(R.drawable.lucide_file_code_2),
    BRACKETS(R.drawable.lucide_brackets),
    CASE(R.drawable.lucide_case_sensitive),
    SOURCE_CONTROL(R.drawable.ic_source_control)
}
class IdeIconDrawable(context: Context, private val icon: IdeIcon, color: Int, private val size: Int) : Drawable() {
    private val vector = context.getDrawable(icon.resource)!!.mutate().apply { setTint(color) }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD); textAlign=Paint.Align.CENTER }
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun onBoundsChange(bounds: android.graphics.Rect) { vector.bounds=bounds }
    override fun setAlpha(alpha:Int){vector.alpha=alpha;badge.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){vector.colorFilter=filter;badge.colorFilter=filter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun draw(canvas:Canvas){
        vector.draw(canvas)
        val label=when(icon){IdeIcon.CPP->"C++";IdeIcon.PYTHON->"Py";IdeIcon.HEADER->"H";else->null}
        if(label!=null){badge.textSize=bounds.width()*.28f;canvas.drawText(label,bounds.exactCenterX(),bounds.top+bounds.height()*.68f,badge)}
    }
    companion object {
        fun file(path:String)=when(path.substringAfterLast('.',"").lowercase()){
            "py"->IdeIcon.PYTHON
            "h","hpp","hxx"->IdeIcon.HEADER
            "cpp","cc","cxx","c"->IdeIcon.CPP
            "json","toml","yaml","yml","ini","xml","gradle","properties"->IdeIcon.CONFIG
            "txt","md","log"->IdeIcon.TEXT
            else->IdeIcon.FILE
        }
        fun color(icon:IdeIcon,palette:IdePalette):Int {
            val dark=palette.editor!=0xFFFFFFFF.toInt()
            return when(icon){
                IdeIcon.CPP->if(dark)0xFF96B5DA.toInt() else 0xFF396A9F.toInt()
                IdeIcon.PYTHON->if(dark)0xFFD5C48F.toInt() else 0xFF80662D.toInt()
                IdeIcon.HEADER->if(dark)0xFFB8A7D2.toInt() else 0xFF72568F.toInt()
                IdeIcon.CONFIG->if(dark)0xFFA8BEA5.toInt() else 0xFF486F44.toInt()
                IdeIcon.FOLDER,IdeIcon.FOLDER_OPEN->if(dark)0xFFC6B18D.toInt() else 0xFF866637.toInt()
                else->palette.muted
            }
        }
        fun forTitle(title:String):IdeIcon=when {
            title.startsWith("最近：")->IdeIcon.FOLDER_OPEN
            title.endsWith(".py",true)->IdeIcon.PYTHON
            title.endsWith(".cpp",true)->IdeIcon.CPP
            title.endsWith(".h",true)||title.endsWith(".hpp",true)->IdeIcon.HEADER
            title.startsWith("代码字号")->IdeIcon.CASE
            title.contains("删除")||title.contains("结束")->IdeIcon.DELETE
            title.contains("新建文件夹")->IdeIcon.ADD_FOLDER
            title.contains("新建")->IdeIcon.ADD_FILE
            title.contains("重命名")->IdeIcon.RENAME
            title.contains("移动")->IdeIcon.MOVE
            title.contains("复制")->IdeIcon.COPY
            title.contains("粘贴")->IdeIcon.PASTE
            title.contains("设置")->IdeIcon.SETTINGS
            title=="外观"->IdeIcon.PALETTE
            title=="编辑器"->IdeIcon.FILE
            title=="补全与快捷键"->IdeIcon.INPUT
            title=="文件与保存"->IdeIcon.SAVE
            title=="动画"->IdeIcon.NEXT
            title=="帮助与许可"->IdeIcon.HELP
            title.contains("主题")||title.contains("配色")->IdeIcon.PALETTE
            title.contains("保存")->IdeIcon.SAVE
            title.contains("撤销")->IdeIcon.UNDO
            title.contains("重做")->IdeIcon.REDO
            title.contains("查找")||title.contains("搜索")->IdeIcon.SEARCH
            title.contains("补全")||title.contains("片段")->IdeIcon.CODE
            title.contains("参数")->IdeIcon.BRACKETS
            title.contains("大小写")->IdeIcon.CASE
            title.contains("键盘")||title.contains("输入")->IdeIcon.INPUT
            title.contains("清空")->IdeIcon.CLEAR
            title.contains("重新")||title.contains("恢复")->IdeIcon.REFRESH
            title.contains("导入")->IdeIcon.IMPORT
            title.contains("导出")->IdeIcon.EXPORT
            title.contains("终端")||title.contains("Shell")||title.contains("会话")->IdeIcon.TERMINAL
            title.endsWith("/")||title.contains("目录")||title.contains("项目")||title.contains("工作区")->IdeIcon.FOLDER_OPEN
            title.contains("运行")||title.contains("启动")->IdeIcon.RUN
            else->IdeIcon.INFO
        }
    }
}
