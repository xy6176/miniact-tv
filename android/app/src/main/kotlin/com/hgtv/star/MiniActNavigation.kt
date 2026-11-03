// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.hgtv.star.compat.attachedToWindow
import com.hgtv.star.core.DirectionalFocus
import com.hgtv.star.tv.TvStyle

internal fun miniActFocusTarget(view:View):Boolean = view.id!=View.NO_ID && view.isFocusable && view.isEnabled && view.isShown &&
    view.width>0 && view.height>0 && view.alpha>0.01f &&
    (view !is ViewGroup || view.getFocusables(View.FOCUS_FORWARD).none { it!==view && it.isFocusable && it.isShown })

/** Runs after local grid/pager/input handlers, so it does not replace their
 * paging, cursor or virtual-row behavior. Only repair an unintended rail exit. */
internal fun MainActivity.repairMiniActFocus(event:KeyEvent,origin:View?):Boolean {
    if(!TvStyle.television || !TvStyle.miniactMaterial || event.action!=KeyEvent.ACTION_DOWN ||
        screen !in listOf("catalog","detail") || origin==null || origin in nav || !origin.attachedToWindow) return false
    val direction=when(event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> DirectionalFocus.Direction.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DirectionalFocus.Direction.DOWN
        KeyEvent.KEYCODE_DPAD_RIGHT -> DirectionalFocus.Direction.RIGHT
        else -> return false
    }
    if(root.findFocus() !in nav) return false
    fun box(view:View):DirectionalFocus.Box {
        val r=Rect();view.getDrawingRect(r);root.offsetDescendantRectToMyCoords(view,r)
        return DirectionalFocus.Box(view.id,r.left,r.top,r.right,r.bottom,view in nav)
    }
    val candidates=root.getFocusables(View.FOCUS_FORWARD).filter { it !in nav && miniActFocusTarget(it) }
    val id=DirectionalFocus.next(box(origin),candidates.map(::box),direction)
    val target=candidates.firstOrNull { it.id==id } ?: origin
    target.requestFocus()
    miniContentFocus=target
    return true
}
internal fun MainActivity.miniActRailFocused()=nav.any { it.hasFocus() }
internal fun MainActivity.focusMiniActRail(): Boolean {
    val current=root.findFocus()
    if(current!=null && !miniActRailFocused()) miniContentFocus=current
    val target=(if(collection!=null) navKeyed["history"] else navEntry()) ?: nav.firstOrNull() ?: return false
    target.requestFocus()
    return true
}
internal fun MainActivity.focusMiniActContent(): Boolean {
    val previous=miniContentFocus?.takeIf { it.attachedToWindow && miniActFocusTarget(it) && it !in nav }
    val target=previous ?: (if(screen=="catalog") firstHeadControl()?.takeIf { it !in nav } else null)
        ?: homeScreen?.firstCard()
        ?: root.getFocusables(View.FOCUS_FORWARD).firstOrNull { it !in nav && miniActFocusTarget(it) }
    return target?.requestFocus()==true
}
/** A permanent escape path independent of category-chip and grid paging listeners. */
internal fun MainActivity.handleMiniActNavigation(event: KeyEvent): Boolean {
    if(screen!="catalog" && screen!="detail") return false
    if(event.action!=KeyEvent.ACTION_DOWN) return false
    if(homeScreen?.handleRemote(event)==true) return true
    val at=nav.indexOfFirst { it.hasFocus() }
    if(at>=0) return when(event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> { nav[(at-1).coerceAtLeast(0)].requestFocus(); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { nav[(at+1).coerceAtMost(nav.lastIndex)].requestFocus(); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { focusMiniActContent(); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> true
        else -> false
    }
    if(event.keyCode==KeyEvent.KEYCODE_DPAD_LEFT) {
        val current=root.findFocus() ?: return focusMiniActRail()
        // Editing text takes priority over the rail's geometric left-edge shortcut.
        // At the start of an empty selection, left can still leave the input normally.
        if(current is EditText && (current.selectionStart>0 || current.selectionEnd>0)) return false
        val rect=Rect(); current.getGlobalVisibleRect(rect)
        val next=current.focusSearch(View.FOCUS_LEFT)
        if(rect.left<=miniActLeftInset()+dp(32) || next==null || next===current || next in nav) return focusMiniActRail()
    }
    return false
}
