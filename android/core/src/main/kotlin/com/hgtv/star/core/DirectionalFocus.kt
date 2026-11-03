// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.core

/** Spatial fallback for content navigation, independent of Android view state. */
object DirectionalFocus {
    enum class Direction { UP, DOWN, RIGHT }
    data class Box(val id:Int,val left:Int,val top:Int,val right:Int,val bottom:Int,val sidebar:Boolean=false)
    fun next(source:Box,candidates:List<Box>,direction:Direction):Int? {
        val vertical=direction!=Direction.RIGHT
        fun major(b:Box)=if(vertical) (b.top.toLong()+b.bottom) else (b.left.toLong()+b.right)
        fun minor(b:Box)=if(vertical) (b.left.toLong()+b.right) else (b.top.toLong()+b.bottom)
        val sign=if(direction==Direction.UP) -1 else 1
        return candidates.asSequence().filter { b ->
            b.id!=source.id && !b.sidebar && b.right>b.left && b.bottom>b.top &&
                sign*(major(b)-major(source))>0 && when(direction) {
                    Direction.UP -> b.top<source.top
                    Direction.DOWN -> b.bottom>source.bottom
                    Direction.RIGHT -> b.right>source.right
                }
        }.minWithOrNull(compareBy<Box>(
            { b -> if(if(vertical) b.right>source.left && b.left<source.right else b.bottom>source.top && b.top<source.bottom) 0 else 1 },
            { b -> when(direction) {
                Direction.UP -> (source.top.toLong()-b.bottom).coerceAtLeast(0)
                Direction.DOWN -> (b.top.toLong()-source.bottom).coerceAtLeast(0)
                Direction.RIGHT -> (b.left.toLong()-source.right).coerceAtLeast(0)
            } },
            { b -> kotlin.math.abs(minor(b)-minor(source)) }
        ))?.id
    }
}
