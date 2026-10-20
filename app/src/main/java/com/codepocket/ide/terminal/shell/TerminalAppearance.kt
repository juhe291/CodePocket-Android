package com.codepocket.ide.terminal.shell

import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import java.util.Properties
import java.util.WeakHashMap

/** App-wide color preference, applied without replacing the emulator, renderer or PTY. */
object TerminalAppearance {
    data class Palette(val background:Int,val foreground:Int,val cursor:Int,val ansi:IntArray)
    private var activeKey=""
    private val cached=mutableMapOf<String,Palette>()
    private val applied=WeakHashMap<TerminalEmulator,String>()
    private fun rgb(value:String)=(0xff000000L or value.toLong(16)).toInt()
    fun palette(name:String,dark:Boolean):Palette {
        val resolved=if(name=="FOLLOW_UI")if(dark)"CODEPOCKET_DARK" else "LIGHT" else name
        cached[resolved]?.let{return it}
        val (bg,fg,colors)=when(resolved){
            "LIGHT"->Triple("FAFBFD","263445","263445 AF4C50 477348 906C28 3F6F9F 805B9C 347B82 D0D7E1 65768A C05F62 527F54 A17B32 507EA9 906AAB 448A91 FFFFFF")
            "NORD"->Triple("2E3440","D8DEE9","3B4252 BF616A A3BE8C EBCB8B 81A1C1 B48EAD 88C0D0 E5E9F0 4C566A BF616A A3BE8C EBCB8B 81A1C1 B48EAD 8FBCBB ECEFF4")
            "DRACULA"->Triple("282A36","E5E3ED","282A36 D78A91 A5CAA2 DBCB9B A99BC9 C49DBE 94C7D4 DBD9E2 62647A E79CA3 B6DAB2 E9DBAF BAACDB D5B0D0 A5D6E1 F0EDF5")
            else->Triple("151B23","D4DDE8","29313D D49191 A8BEA5 D3BF8E 8CAED5 B5A4CC 91BBC0 CDD5DF 65768A E4A3A3 B8CFB3 E1CFA3 9EBEE0 C7B6DB A4CCD0 E7EDF5")
        }
        return Palette(rgb(bg),rgb(fg),rgb(fg),colors.split(' ').map(::rgb).toIntArray()).also{cached[resolved]=it}
    }
    @Synchronized fun apply(session:TerminalSession?,name:String,dark:Boolean):Palette {
        val key="$name:$dark";val p=palette(name,dark)
        if(activeKey!=key){
            val props=Properties();props.setProperty("background","#%06x".format(p.background and 0xffffff));props.setProperty("foreground","#%06x".format(p.foreground and 0xffffff));props.setProperty("cursor","#%06x".format(p.cursor and 0xffffff))
            p.ansi.forEachIndexed{i,c->props.setProperty("color$i","#%06x".format(c and 0xffffff))}
            TerminalColors.COLOR_SCHEME.updateWith(props);activeKey=key
        }
        val emulator=session?.emulator
        if(emulator!=null&&applied[emulator]!=key){
            for(i in 0..15)emulator.mColors.reset(i)
            emulator.mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND);emulator.mColors.reset(TextStyle.COLOR_INDEX_BACKGROUND);emulator.mColors.reset(TextStyle.COLOR_INDEX_CURSOR)
            applied[emulator]=key
        }
        return p
    }
}
