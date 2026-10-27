package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*

internal object CppLicenses {
    val entries = listOf(
        "来源、对应源码与重链接" to "README.txt",
        "LLVM / clangd / libc++" to "LLVM-LICENSE.txt",
        "LLVM 历史 NCSA" to "NCSA.txt",
        "libffi" to "LIBFFI-COPYRIGHT.txt",
        "libxml2" to "LIBXML2-COPYRIGHT.txt",
        "zlib" to "ZLIB-COPYRIGHT.txt",
        "zstd（BSD 许可）" to "ZSTD-LICENSE.txt",
        "libiconv LGPL 2.1" to "LIBICONV-COPYING.LIB.txt",
        "libiconv 上游 README" to "LIBICONV-README.txt",
        "GNU GPL 3（上游附带文本）" to "LIBICONV-COPYING.txt",
        "Apache 2.0" to "Apache-2.0.txt",
        "Android NDK r30 通知" to "NDK-r30-NOTICE-0.txt",
        "NDK r30 LLVM 通知" to "NDK-r30-NOTICE-1.txt",
        "NDK r30 sysroot 通知" to "NDK-r30-NOTICE-2.txt",
        "NDK r30 toolchain 通知" to "NDK-r30-NOTICE-3.txt")
    fun show(activity: Activity, animations: Boolean) {
        val choices=entries.map { (title,file) -> WorkbenchChoice(file,title,"",IdeIcon.HELP) }
        val picker=AlertDialog.Builder(activity).setCustomTitle(WorkbenchForms.header(activity,"C++ 语义分析许可与源码","帮助与许可",IdeIcon.HELP))
            .setAdapter(WorkbenchChoiceAdapter(activity,choices),null).setNegativeButton("关闭",null).create()
        DialogMotion.show(picker,animations)
        picker.window?.setLayout(minOf(IdeControls.dp(activity,560),(activity.resources.displayMetrics.widthPixels-IdeControls.dp(activity,32)).coerceAtLeast(IdeControls.dp(activity,200))),-2)
        picker.listView.setOnItemClickListener { _,_,index,_ ->
            val (title,file)=entries[index]
            val text=activity.assets.open("cpp-licenses/$file").bufferedReader().use { it.readText() }
            ReadOnlyDetails.show(activity,title,"C++ 语义分析许可与源码",text,IdeIcon.HELP,false,animations,null,null,null)
        }
    }
}
