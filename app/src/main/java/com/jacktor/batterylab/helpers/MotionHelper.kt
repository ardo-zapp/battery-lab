package com.jacktor.batterylab.helpers

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.jacktor.batterylab.R

object MotionHelper {

    val EMPHASIZED_DECELERATE: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
    val STANDARD_DECELERATE: Interpolator = PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f)
    val GENTLE_OVERSHOOT: Interpolator = OvershootInterpolator(1.15f)

    fun isAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    /**
     * Animates a single view in with Material 3 emphasized decelerate (fade + subtle rise + scale).
     */
    fun animateEntrance(
        view: View,
        delayMs: Long = 0L,
        durationMs: Long = 280L,
        translateDp: Float = 16f,
        startScale: Float = 0.97f
    ) {
        view.animate().cancel()
        if (!isAnimationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val density = view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = translateDp * density
        view.scaleX = startScale
        view.scaleY = startScale

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(durationMs)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .start()
    }

    /**
     * Staggers entrance animations across visible direct children (e.g. cards inside a ScrollView column).
     */
    fun animateStaggeredChildren(
        container: ViewGroup,
        initialDelayMs: Long = 15L,
        stepDelayMs: Long = 45L,
        maxStaggerItems: Int = 8
    ) {
        var visibleIndex = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val delay = if (visibleIndex < maxStaggerItems) {
                initialDelayMs + visibleIndex * stepDelayMs
            } else {
                initialDelayMs + maxStaggerItems * stepDelayMs
            }
            animateEntrance(child, delayMs = delay)
            visibleIndex++
        }
    }

    /**
     * Binds subtle Material 3 tactile press-scale micro-interaction without consuming clicks.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun bindPressScale(vararg views: View, pressedScale: Float = 0.975f) {
        for (view in views) {
            view.setOnTouchListener { v, event ->
                if (!isAnimationsEnabled() || !v.isEnabled) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().cancel()
                        v.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .setDuration(85L)
                            .setInterpolator(STANDARD_DECELERATE)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().cancel()
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(170L)
                            .setInterpolator(GENTLE_OVERSHOOT)
                            .start()
                    }
                }
                false
            }
        }
    }

    /**
     * Starts a modern smooth breathing pulse loop (scale + subtle alpha) for active charging or load test.
     */
    fun startBreathingPulse(
        view: View,
        minScale: Float = 1.0f,
        maxScale: Float = 1.14f,
        minAlpha: Float = 0.76f,
        durationMs: Long = 920L
    ): AnimatorSet? {
        if (!isAnimationsEnabled()) return null
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, minScale, maxScale).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, minScale, maxScale).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, minAlpha).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    /**
     * Gentle vertical floating animation for empty-state mascot illustration.
     */
    fun startFloatLoop(view: View, amplitudeDp: Float = 5f, durationMs: Long = 1600L): ObjectAnimator? {
        if (!isAnimationsEnabled()) return null
        val offsetPx = amplitudeDp * view.resources.displayMetrics.density
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -offsetPx, offsetPx).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun stopAnimator(animator: Animator?, vararg resetViews: View) {
        animator?.cancel()
        for (view in resetViews) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            view.translationY = 0f
        }
    }

    /**
     * Smoothly transitions an ImageView color filter tint when status color changes.
     */
    fun animateImageTint(view: ImageView, targetColor: Int, durationMs: Long = 280L) {
        val prevColor = view.getTag(R.id.tag_last_tint_color) as? Int
        if (prevColor == null || prevColor == targetColor || !isAnimationsEnabled()) {
            view.setTag(R.id.tag_last_tint_color, targetColor)
            view.setColorFilter(targetColor)
            return
        }
        view.setTag(R.id.tag_last_tint_color, targetColor)
        ValueAnimator.ofObject(ArgbEvaluator(), prevColor, targetColor).apply {
            duration = durationMs
            interpolator = STANDARD_DECELERATE
            addUpdateListener { va ->
                view.setColorFilter(va.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * Smoothly transitions a badge TextView text color and background tint.
     */
    fun animateBadgeColors(
        textView: TextView,
        targetColor: Int,
        bgAlpha: Int = 32,
        durationMs: Long = 280L
    ) {
        val prevColor = textView.getTag(R.id.tag_last_tint_color) as? Int
        if (prevColor == null || prevColor == targetColor || !isAnimationsEnabled()) {
            textView.setTag(R.id.tag_last_tint_color, targetColor)
            textView.setTextColor(targetColor)
            textView.backgroundTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(targetColor, bgAlpha)
            )
            return
        }
        textView.setTag(R.id.tag_last_tint_color, targetColor)
        ValueAnimator.ofObject(ArgbEvaluator(), prevColor, targetColor).apply {
            duration = durationMs
            interpolator = STANDARD_DECELERATE
            addUpdateListener { va ->
                val c = va.animatedValue as Int
                textView.setTextColor(c)
                textView.backgroundTintList = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(c, bgAlpha)
                )
            }
            start()
        }
    }
}
