package com.jacktor.batterylab.views

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * NestedScrollView that prevents periodic content/layout refreshes from waking
 * the vertical scrollbar indicator unless the user is actively scrolling.
 */
class QuietNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var inLayoutPass = false
    private var lastUserScrollMs = 0L

    init {
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            lastUserScrollMs = SystemClock.uptimeMillis()
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        inLayoutPass = true
        try {
            super.onLayout(changed, l, t, r, b)
        } finally {
            inLayoutPass = false
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        if (!inLayoutPass && t != oldt) {
            lastUserScrollMs = SystemClock.uptimeMillis()
        }
        super.onScrollChanged(l, t, oldl, oldt)
    }

    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean {
        if (inLayoutPass) return false
        val isUserScrolling = SystemClock.uptimeMillis() - lastUserScrollMs <= 250L
        return if (isUserScrolling) super.awakenScrollBars(startDelay, invalidate) else false
    }
}
