// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star.list

import android.content.Context
import android.graphics.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hgtv.star.core.Series
import com.hgtv.star.tv.*

/** Television home: recycled shelves containing recycled cards. Only nearby content is laid
 * out; arriving account/feed updates are diffed instead of rebuilding the scrolling page. */
internal class MiniActHomeFeed(
    context: Context,
    private val top: View,
    private val contentWidth: Int,
    private val loadCover: (ImageView,String)->Unit,
    private val open: (Series,Boolean)->Unit,
    private val actions: (Series,View,String)->Unit,
    private val remember: (String)->Unit,
    private val onScroll: (Int)->Unit,
    private val settled: (Int)->Unit,
    private val headUp: ()->Unit
) : FrameLayout(context) {
    private fun dp(n: Number)=context.designPx(n)
    private val gap=dp(12)
    private val cardWidth=((contentWidth-gap*4)/5).coerceAtLeast(dp(120))
    private val posterHeight=((cardWidth-dp(20))/.70f).toInt()
    private val textHeight=dp(if(resources.configuration.fontScale>1.2f) 112 else 90)
    private val cardHeight=posterHeight+textHeight
    private val continueWidth=cardWidth
    private val continuePosterHeight=posterHeight
    private val continueHeight=continuePosterHeight+textHeight
    private val heroHeight=MiniActFeature.preferredHeight(context)
    private val pool=RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(1,12); setMaxRecycledViews(2,5); setMaxRecycledViews(3,3)
    }
    private var data=emptyList<HomeScreen.Shelf>()
    private var feature: Series?=null
    private var featureResume=false
    private var discover: (() -> Unit)?=null
    private var hero: MiniActFeature?=null
    private var focusRow=0
    private var focusColumn=0
    private var pendingFocus: Pair<Int,Int>?=null
    private var focusAttempts=0
    private var deferred: List<HomeScreen.Shelf>?=null
    private var lastKey=""
    private val offsets=HashMap<String,Int>()
    private val layout=object:LinearLayoutManager(context,VERTICAL,false) {
        override fun calculateExtraLayoutSpace(state: RecyclerView.State,extra: IntArray) {
            extra[0]=height/2; extra[1]=height/2
        }
    }
    private val list=QuietRecycler(context).apply {
        layoutManager=layout; itemAnimator=null; setHasFixedSize(true); overScrollMode=View.OVER_SCROLL_NEVER
        setItemViewCacheSize(1); descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS
        clipToPadding=false; setPadding(0,dp(8),0,dp(28))
        addOnScrollListener(object:RecyclerView.OnScrollListener() {
            override fun onScrolled(rv:RecyclerView,dx:Int,dy:Int) { onScroll(offset()) }
            override fun onScrollStateChanged(rv:RecyclerView,state:Int) {
                if(state==RecyclerView.SCROLL_STATE_IDLE) {
                    deferred?.let { deferred=null; submit(it) }
                    attemptPendingFocus(); settled(offset())
                }
            }
        })
    }
    private val feedAdapter=FeedAdapter()
    init { list.adapter=feedAdapter; addView(list,LayoutParams(-1,-1)) }
    private fun hasHero()=feature!=null
    private fun shelfRow(index:Int)=index+if(hasHero()) 1 else 0
    private fun shelfAt(row:Int)=data.getOrNull(row-if(hasHero()) 1 else 0)
    private fun rowHeight(row:Int)=if(hasHero() && row==0) heroHeight else dp(52+18)+if(shelfAt(row)?.key=="history") continueHeight else cardHeight
    private fun rowTop(row:Int):Int { var result=0; for(i in 0 until row) result+=rowHeight(i); return result }
    private fun offset():Int {
        val first=layout.findFirstVisibleItemPosition().coerceAtLeast(0)
        return (rowTop(first)+list.paddingTop-(layout.findViewByPosition(first)?.top ?: list.paddingTop)).coerceAtLeast(0)
    }
    fun render(next:List<HomeScreen.Shelf>,featured:HomeScreen.Entry?,discover:(()->Unit)?,key:String,wantFocus:Boolean,position:HomeScreen.Position?) {
        val nextFeature=featured?.series
        val resume=next.firstOrNull { it.key=="history" }?.entries?.any { it.series.id==nextFeature?.id && it.resume }==true
        if(feature!=nextFeature || hero==null || resume!=featureResume) { feature=nextFeature;featureResume=resume;this.discover=discover;hero=feature?.let { series -> makeHero(series) } }
        data=next.filter { it.entries.isNotEmpty() || it.more!=null }
        feedAdapter.notifyDataSetChanged()
        if(position!=null) {
            var row=0
            while(row<feedAdapter.itemCount-1 && rowTop(row+1)<=position.vertical) row++
            layout.scrollToPositionWithOffset(row,rowTop(row)-position.vertical)
            offsets.putAll(position.horizontal)
        }
        if(wantFocus) {
            val wanted=com.hgtv.star.core.HomeFocusKey.resolve(key,position?.focus.orEmpty())
            val index=data.indexOfFirst { shelf -> wanted.startsWith(shelf.key+":") || shelf.entries.any { it.series.id==wanted } }
            val column=if(index>=0) data[index].entries.indexOfFirst { wanted=="${data[index].key}:${it.series.id}" || wanted==it.series.id }.coerceAtLeast(0)
                else if(wanted=="featured:${feature?.id}" && position?.focus==wanted) position.column.coerceIn(0,1) else 0
            pendingFocus=(if(index>=0) shelfRow(index) else 0) to column
            post { attemptPendingFocus() }
        }
        post { settled(offset()) }
    }
    private fun makeHero(series:Series)=MiniActFeature(context,series,loadCover,
        { remember("featured:${series.id}");open(series,false) },
        { remember("featured:${series.id}");open(series,true) },featureResume).apply {
        primary.tag="featured:${series.id}"
        primary.onFocus={ focused -> if(focused) { focusRow=0; focusColumn=0; lastKey="featured:${series.id}"; remember(lastKey); reveal(0) } }
        secondary?.onFocus={ focused -> if(focused) { focusRow=0;focusColumn=1;lastKey="featured:${series.id}";remember(lastKey);reveal(0) } }
    }
    /** Preserve root identity, scroll, and focus for normal history/account updates. */
    fun submit(next:List<HomeScreen.Shelf>) {
        if(list.scrollState!=RecyclerView.SCROLL_STATE_IDLE) { deferred=next; return }
        val old=data; val fresh=next.filter { it.entries.isNotEmpty() || it.more!=null }
        val previousFeature=feature
        val previousResume=featureResume
        val chosen=(fresh.firstOrNull { it.key=="rank-real" } ?: fresh.firstOrNull { it.key.startsWith("rank-") })?.entries?.firstOrNull()?.series
        val resume=fresh.firstOrNull { it.key=="history" }?.entries?.any { it.series.id==chosen?.id && it.resume }==true
        if(chosen!=null && (chosen!=feature || resume!=featureResume)) { feature=chosen;featureResume=resume;hero=makeHero(chosen) }
        val oldShift=if(previousFeature!=null) 1 else 0
        val newShift=if(feature!=null) 1 else 0
        val diff=DiffUtil.calculateDiff(object:DiffUtil.Callback() {
            override fun getOldListSize()=old.size+oldShift
            override fun getNewListSize()=fresh.size+newShift
            override fun areItemsTheSame(a:Int,b:Int)=if(a<oldShift || b<newShift) a<oldShift && b<newShift else old[a-oldShift].key==fresh[b-newShift].key
            override fun areContentsTheSame(a:Int,b:Int)=if(a<oldShift) previousFeature==feature && previousResume==featureResume else old[a-oldShift].entries==fresh[b-newShift].entries && old[a-oldShift].title==fresh[b-newShift].title
        })
        data=fresh; diff.dispatchUpdatesTo(feedAdapter)
    }
    fun refreshEntries(next:List<HomeScreen.Shelf>):List<String> {
        val old=keys(); val fresh=next.flatMap { shelf -> shelf.entries.map { "${shelf.key}:${it.series.id}" } }
        submit(next)
        return fresh.filterNot { it in old }
    }
    fun updateEntry(shelf:String,entry:HomeScreen.Entry):Boolean {
        if(data.none { it.key==shelf && it.entries.any { e -> e.series.id==entry.series.id } }) return false
        submit(data.map { if(it.key==shelf) it.copy(entries=it.entries.map { e -> if(e.series.id==entry.series.id) entry else e }) else it })
        return true
    }
    fun keys():Set<String> = buildSet {
        data.forEach { shelf -> shelf.entries.forEach { add("${shelf.key}:${it.series.id}") }; if(shelf.more!=null) add("${shelf.key}:more") }
    }
    fun capture():HomeScreen.Position {
        synchronizeFocus()
        val shelf=shelfAt(focusRow)
        visibleShelves().forEach { holder -> offsets[holder.shelf.key]=holder.horizontal.computeHorizontalScrollOffset() }
        return HomeScreen.Position(lastKey,shelf?.key,focusColumn,(focusRow-if(hasHero()) 1 else 0).coerceAtLeast(0),offset(),offsets.toMap())
    }
    fun scrollOffset()=offset()
    fun focusedRow()=if(!hasFocus()) -1 else focusRow-if(hasHero()) 1 else 0
    fun firstCard():View? {
        val current=(list.findViewHolderForAdapterPosition(focusRow) as? ShelfHolder)?.cell(focusColumn)
        return current ?: hero?.primary?.takeIf { it.parent!=null && it.isShown }
            ?: visibleShelves().firstOrNull()?.cell(0) ?: hero?.primary
    }
    fun firstId()=firstCard()?.id ?: top.id
    fun handleRemote(event:KeyEvent):Boolean {
        if(event.action!=KeyEvent.ACTION_DOWN || !hasFocus()) return false
        synchronizeFocus()
        val row=focusRow
        val column=focusColumn
        return when(event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> { if(row<feedAdapter.itemCount-1) focusAt(row+1,if(row==0 && hasHero()) 0 else column); true }
            KeyEvent.KEYCODE_DPAD_UP -> { if(row>0) focusAt(row-1,column) else headUp(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if(row==0 && hasHero()) {
                    if(hero?.secondary?.hasFocus()==true) hero?.primary?.requestFocus() else top.requestFocus()
                } else if(column>0) focusAt(row,column-1) else top.requestFocus()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if(row==0 && hasHero()) hero?.secondary?.requestFocus()
                else {
                    val shelf=shelfAt(row)
                    val count=(shelf?.entries?.size ?: 0)+if(shelf?.more!=null) 1 else 0
                    if(column+1<count) focusAt(row,column+1)
                }; true
            }
            else -> false
        }
    }
    private fun synchronizeFocus() {
        val focused=findFocus() ?: return
        val holder=list.findContainingViewHolder(focused) as? ShelfHolder ?: return
        if(holder.bindingAdapterPosition<0) return
        focusRow=holder.bindingAdapterPosition
        val card=holder.horizontal.findContainingViewHolder(focused)
        if(card!=null && card.bindingAdapterPosition>=0) focusColumn=card.bindingAdapterPosition
    }
    private fun focusAt(row:Int,column:Int) {
        focusAttempts=0
        pendingFocus=row.coerceIn(0,(feedAdapter.itemCount-1).coerceAtLeast(0)) to column
        attemptPendingFocus()
    }
    private fun attemptPendingFocus() {
        if(windowToken==null || width==0) return
        val target=pendingFocus ?: return
        if(hasHero() && target.first==0) {
            if(hero?.primary?.isAttachedCompat()==true) { pendingFocus=null;(if(target.second==1) hero?.secondary ?: hero?.primary else hero?.primary)?.requestFocus();reveal(0) }
            else { layout.scrollToPositionWithOffset(0,0); retryFocus(target) }
            return
        }
        val holder=list.findViewHolderForAdapterPosition(target.first) as? ShelfHolder
        if(holder==null) {
            layout.scrollToPositionWithOffset(target.first,dp(8))
            retryFocus(target); return
        }
        val count=holder.cards.itemCount
        if(count==0) { pendingFocus=null; return }
        val index=target.second.coerceIn(0,count-1)
        val card=holder.cell(index)
        if(card==null) {
            holder.manager.scrollToPosition(index)
            retryFocus(target); return
        }
        pendingFocus=null; card.requestFocus(); holder.reveal(index); reveal(target.first)
    }
    private fun retryFocus(target:Pair<Int,Int>) {
        if(++focusAttempts<=4) list.postOnAnimation { if(pendingFocus==target) attemptPendingFocus() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post { attemptPendingFocus() } }
    private fun reveal(row:Int) {
        val view=layout.findViewByPosition(row) ?: return
        val delta=when {
            view.top<list.paddingTop -> view.top-list.paddingTop
            view.bottom>list.height-list.paddingBottom -> view.bottom-list.height+list.paddingBottom
            else -> 0
        }
        if(delta!=0) if(TvStyle.animate) list.smoothScrollBy(0,delta,DecelerateInterpolator(1.5f),180) else list.scrollBy(0,delta)
    }
    private fun View.isAttachedCompat()=windowToken!=null
    private fun visibleShelves():List<ShelfHolder> = (0 until list.childCount).mapNotNull { list.getChildViewHolder(list.getChildAt(it)) as? ShelfHolder }
    private fun attachedCards():List<CardHolder> = visibleShelves().flatMap { shelf -> (0 until shelf.horizontal.childCount).mapNotNull { shelf.horizontal.getChildViewHolder(shelf.horizontal.getChildAt(it)) as? CardHolder } }
    fun reloadArtwork() { attachedCards().forEach { it.reload() } }
    fun suspendOutside(rect:Rect):Int { var count=0; attachedCards().forEach { card -> if(card.poster!=null && !card.itemView.getGlobalVisibleRect(rect) && card.poster.suspendCover()) count++ }; return count }
    fun resumeInside(rect:Rect):Int { var count=0; attachedCards().forEach { card -> if(card.itemView.getGlobalVisibleRect(rect) && card.poster?.resumeCover()==true) count++ }; return count }
    fun suspendedCount()=attachedCards().count { it.poster?.coverSuspended==true }

    private inner class FeedAdapter:RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        init { setHasStableIds(true) }
        override fun getItemCount()=data.size+if(hasHero()) 1 else 0
        override fun getItemViewType(position:Int)=if(hasHero() && position==0) 0 else 1
        override fun getItemId(position:Int)=if(getItemViewType(position)==0) Long.MIN_VALUE else shelfAt(position)!!.key.hashCode().toLong()
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):RecyclerView.ViewHolder = if(type==0) object:RecyclerView.ViewHolder(FrameLayout(context)) {} else ShelfHolder()
        override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int) {
            if(holder is ShelfHolder) holder.bind(shelfAt(position)!!,position)
            else (holder.itemView as FrameLayout).apply {
                layoutParams=RecyclerView.LayoutParams(-1,heroHeight)
                if(hero?.parent!==this) { (hero?.parent as? ViewGroup)?.removeView(hero); removeAllViews(); hero?.let { addView(it,LayoutParams(-1,-1)) } }
            }
        }
    }
    private inner class ShelfHolder:RecyclerView.ViewHolder(LinearLayout(context)) {
        lateinit var shelf:HomeScreen.Shelf
        val row:Int get()=bindingAdapterPosition
        val heading=context.tvText("",20f,TvStyle.text).apply { typeface=Typeface.create("sans-serif-medium",0); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(6),0,0,0) }
        val manager=object:LinearLayoutManager(context,HORIZONTAL,false) {
            override fun calculateExtraLayoutSpace(state:RecyclerView.State,extra:IntArray) { extra[0]=cardWidth; extra[1]=cardWidth }
        }.apply { initialPrefetchItemCount=2 }
        val horizontal=QuietRecycler(context).apply {
            layoutManager=manager; itemAnimator=null; setHasFixedSize(true); setRecycledViewPool(pool); setItemViewCacheSize(1)
            overScrollMode=View.OVER_SCROLL_NEVER; clipToPadding=false
        }
        val cards=CardsAdapter(this)
        init {
            (itemView as LinearLayout).apply {
                orientation=LinearLayout.VERTICAL
                addView(heading,LinearLayout.LayoutParams(-1,dp(52)))
                horizontal.adapter=cards; addView(horizontal,LinearLayout.LayoutParams(-1,cardHeight))
                setPadding(0,0,0,dp(18))
            }
        }
        fun bind(next:HomeScreen.Shelf,index:Int) {
            val changed=!::shelf.isInitialized || shelf.key!=next.key
            shelf=next
            heading.text=next.title
            itemView.layoutParams=RecyclerView.LayoutParams(-1,rowHeight(index))
            horizontal.layoutParams=horizontal.layoutParams.apply { height=if(next.key=="history") continueHeight else cardHeight }
            cards.submit(next)
            if(changed) manager.scrollToPositionWithOffset(0,-(offsets[next.key] ?: 0))
        }
        fun cell(index:Int)=horizontal.findViewHolderForAdapterPosition(index)?.itemView
        fun reveal(index:Int) {
            val item=manager.findViewByPosition(index) ?: return
            val delta=when { item.left<0 -> item.left; item.right>horizontal.width -> item.right-horizontal.width; else -> 0 }
            if(delta!=0) if(TvStyle.animate) horizontal.smoothScrollBy(delta,0,DecelerateInterpolator(1.5f),150) else horizontal.scrollBy(delta,0)
        }
    }
    private inner class CardsAdapter(private val holder:ShelfHolder):RecyclerView.Adapter<CardHolder>() {
        private var entries=emptyList<HomeScreen.Entry>()
        private var more=false
        init { setHasStableIds(true) }
        override fun getItemCount()=entries.size+if(more) 1 else 0
        override fun getItemViewType(position:Int)=if(position>=entries.size) 3 else if(holder.shelf.key=="history") 2 else 1
        override fun getItemId(position:Int)=entries.getOrNull(position)?.series?.id?.hashCode()?.toLong() ?: Long.MAX_VALUE
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=CardHolder(type)
        override fun onBindViewHolder(card:CardHolder,position:Int) { card.bind(holder,entries.getOrNull(position),position) }
        override fun onViewAttachedToWindow(holder:CardHolder) { holder.reload() }
        override fun onViewRecycled(holder:CardHolder) { holder.poster?.releaseCover() }
        fun submit(shelf:HomeScreen.Shelf) {
            val previous=entries; val previousMore=more
            val diff=DiffUtil.calculateDiff(object:DiffUtil.Callback() {
                override fun getOldListSize()=previous.size+if(previousMore) 1 else 0
                override fun getNewListSize()=shelf.entries.size+if(shelf.more!=null) 1 else 0
                override fun areItemsTheSame(a:Int,b:Int)=previous.getOrNull(a)?.series?.id==shelf.entries.getOrNull(b)?.series?.id
                override fun areContentsTheSame(a:Int,b:Int)=previous.getOrNull(a)==shelf.entries.getOrNull(b)
            })
            entries=shelf.entries; more=shelf.more!=null; diff.dispatchUpdatesTo(this)
        }
    }
    private inner class CardHolder(type:Int):RecyclerView.ViewHolder(when(type) {
        1 -> MiniActPosterTile(context,cardWidth,posterHeight,loadCover)
        2 -> MiniActPosterTile(context,continueWidth,continuePosterHeight,loadCover,history=true)
        else -> MoreCard(context)
    }) {
        val poster=itemView as? MiniActPosterTile
        private val moreCard=itemView as? MoreCard
        private var owner:ShelfHolder?=null
        private var entry:HomeScreen.Entry?=null
        private var index=0
        init {
            itemView.id=View.generateViewId(); itemView.isFocusable=true; itemView.isFocusableInTouchMode=true
            itemView.setOnFocusChangeListener { _,focused ->
                poster?.setFocused(focused)
                moreCard?.plate?.focus(focused)
                if(focused) owner?.let { shelf ->
                    focusRow=shelf.row; focusColumn=index
                    lastKey="${shelf.shelf.key}:${entry?.series?.id ?: "more"}"; remember(lastKey)
                    shelf.reveal(index); reveal(shelf.row)
                }
            }
            itemView.setOnClickListener { entry?.let { open(it.series,it.resume) } ?: owner?.shelf?.more?.invoke() }
            itemView.setOnKeyListener { _,key,event -> if(key==KeyEvent.KEYCODE_MENU && event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0) itemView.performLongClick() else false }
            itemView.setOnLongClickListener { entry?.let { item -> owner?.let { actions(item.series,itemView,it.shelf.key) }; true } ?: false }
        }
        fun bind(shelf:ShelfHolder,item:HomeScreen.Entry?,position:Int) {
            owner=shelf; entry=item; index=position
            itemView.tag="${shelf.shelf.key}:${item?.series?.id ?: "more"}"
            val wide=shelf.shelf.key=="history"
            itemView.layoutParams=RecyclerView.LayoutParams(if(wide) continueWidth else cardWidth,if(wide) continueHeight else cardHeight).apply { rightMargin=gap }
            if(item!=null) {
                poster?.bind(item.series,item.label,item.badge,item.progress,false)
                itemView.contentDescription="${shelf.shelf.title}，${item.series.title}，${item.label}"
            } else itemView.contentDescription="${shelf.shelf.title}，查看全部"
        }
        fun reload() { entry?.let { if(poster!=null) { if(poster.coverSuspended) poster.resumeCover() else loadCover(poster.image,it.series.cover) }  } }
    }
    private inner class MoreCard(context:Context):FrameLayout(context) {
        val plate=MiniActContentSurface(this,14)
        init {
            background=plate
            val content=context.tvColumn().apply { gravity=Gravity.CENTER }
            val arrow=context.tvText("›",38f,TvStyle.text).apply {
                gravity=Gravity.CENTER
                background=MiniActControlSurface(this,radius=32).apply { appearance(false) }
            }
            content.addView(arrow,LinearLayout.LayoutParams(dp(68),dp(68)).apply { gravity=Gravity.CENTER_HORIZONTAL })
            content.addView(context.tvText("查看全部",18f,TvStyle.text).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16) })
            addView(content,LayoutParams(-1,-2,Gravity.CENTER))
        }
    }
    private class QuietRecycler(context:Context):RecyclerView(context) {
        override fun requestChildRectangleOnScreen(child:View,rect:Rect,immediate:Boolean)=false
    }
}
