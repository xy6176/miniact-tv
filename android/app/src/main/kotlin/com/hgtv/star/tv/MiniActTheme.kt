// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.tv

import android.content.Context
import android.graphics.Color

internal data class MiniActTheme(
    val id:String,val label:String,val top:Int,val middle:Int,val bottom:Int,
    val upperLight:Int,val lowerLight:Int,val glassBase:Int,val glassShade:Int,
    val atlasTop:Int,val atlasMiddle:Int,val atlasBottom:Int,val warm:Int,val cool:Int,
    val floorOpacity:Int
)

/** A MiniAct palette changes color and material together, never the layout,
 * remote key map, artwork, account or playback policy. */
internal object MiniActThemes {
    const val DEFAULT_ID="cinema"
    val all=listOf(
        MiniActTheme("cinema","影院深色",0xFF0B0D12.toInt(),0xFF07090D.toInt(),0xFF050609.toInt(),
            0x0C31405A,0,0xFF222630.toInt(),0xFF12151E.toInt(),
            0xFF344051.toInt(),0xFF252936.toInt(),0xFF14171F.toInt(),0x728E786C,0xC2C9D5E4.toInt(),255),
        MiniActTheme("wine","酒红丝绒",0xFF311620.toInt(),0xFF231923.toInt(),0xFF160F19.toInt(),
            0xB4781234.toInt(),0x9C63162D.toInt(),0xFF49303C.toInt(),0xFF2E1925.toInt(),
            0xFF805063.toInt(),0xFF573F53.toInt(),0xFF311A2C.toInt(),0x9CA74C62.toInt(),0xB0B9A9C0.toInt(),82),
        MiniActTheme("ocean","深海蓝",0xFF142238.toInt(),0xFF111B2B.toInt(),0xFF080E1B.toInt(),
            0x9C245D88.toInt(),0x70452F76,0xFF2E4159.toInt(),0xFF19293D.toInt(),
            0xFF557994.toInt(),0xFF324F6C.toInt(),0xFF172A45.toInt(),0x605F699A,0xB0A5C6E1.toInt(),110),
        MiniActTheme("amber","琥珀棕",0xFF2B2017.toInt(),0xFF211B17.toInt(),0xFF110D0B.toInt(),
            0xA080512A.toInt(),0x80592E20.toInt(),0xFF4B3A2D.toInt(),0xFF30231C.toInt(),
            0xFF927552.toInt(),0xFF614A36.toInt(),0xFF32221B.toInt(),0xAAC49A62.toInt(),0xA0C5BBAD.toInt(),110)
    )
    var current=all.first();private set
    private fun prefs(context:Context)=context.getSharedPreferences("miniact_materials",Context.MODE_PRIVATE)
    private fun resolve(id:String?)=all.firstOrNull { it.id==id } ?: all.first { it.id==DEFAULT_ID }
    fun load(context:Context) { current=resolve(prefs(context).getString("visual_theme",DEFAULT_ID)) }
    fun select(context:Context,id:String) {
        current=resolve(id);prefs(context).edit().putString("visual_theme",current.id).apply()
    }
    fun alpha(color:Int,value:Int)=Color.argb(value,Color.red(color),Color.green(color),Color.blue(color))
    fun mix(a:Int,b:Int,f:Float)=Color.rgb(
        (Color.red(a)+(Color.red(b)-Color.red(a))*f).toInt(),
        (Color.green(a)+(Color.green(b)-Color.green(a))*f).toInt(),
        (Color.blue(a)+(Color.blue(b)-Color.blue(a))*f).toInt())
}
