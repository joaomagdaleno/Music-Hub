package com.joaomagdaleno.music_hub.utils.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.Interpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.color.MaterialColors
import com.google.android.material.motion.MotionUtils
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.utils.ContextUtils.SETTINGS_NAME
import kotlin.math.absoluteValue
import kotlin.math.sign

object AnimationUtils {

    private fun startAnimation(
        view: View, animation: ViewPropertyAnimator, durationMultiplier: Float = 1f
    ) = view.run {
        clearAnimation()
        val interpolator = getInterpolator(context)
        val duration = getAnimationDuration(view) * durationMultiplier
        animation.setInterpolator(interpolator).setDuration(duration.toLong()).start()
    }

    private fun getInterpolator(context: Context) = MotionUtils.resolveThemeInterpolator(
        context, com.google.android.material.R.attr.motionEasingStandardInterpolator,
        FastOutSlowInInterpolator()
    )

    fun animateMarginTop(view: View, hide: Boolean, onEnd: (() -> Unit)? = null) {
        if (!getIsEnabled(view)) {
            view.isVisible = hide
            return
        }
        view.isVisible = true
        val fromMargin = (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        val toMargin = if (hide) 0 else -view.height
        val fromAlpha = view.alpha
        val toAlpha = if (hide) 1f else 0f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedFraction
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = (fromMargin + (toMargin - fromMargin) * fraction).toInt()
            view.layoutParams = params
            view.alpha = fromAlpha + (toAlpha - fromAlpha) * fraction
        }
        animator.duration = getAnimationDuration(view)
        animator.interpolator = getInterpolator(view.context)
        animator.doOnEnd {
            view.isVisible = hide
            onEnd?.invoke()
        }
        animator.start()
    }

    fun animateTranslation(
        navView: NavigationBarView,
        isRail: Boolean,
        isMainFragment: Boolean,
        isPlayerCollapsed: Boolean,
        animate: Boolean = true,
        action: (Float) -> Unit
    ) = navView.doOnLayout {
        val visible = isMainFragment && isPlayerCollapsed
        val value = if (visible) 0f
        else if (isRail) -navView.width.toFloat() else navView.height.toFloat()
        if (getIsEnabled(navView) && animate) {
            var animation = if (isRail) navView.animate().translationX(value)
            else navView.animate().translationY(value)
            animation = if (visible) animation.withStartAction { action(value) }
            else animation.withEndAction { action(value) }
            startAnimation(navView, animation)

            val delay = if (!visible) 0L else getAnimationDurationSmall(navView)
            navView.menu.forEachIndexed { index, item ->
                val view = navView.findViewById<View>(item.itemId)
                if (view != null) {
                    val anim = view.animate().setStartDelay(index * delay)
                    if (isRail) anim.translationX(value)
                    else anim.translationY(value)
                    startAnimation(view, anim, 0.5f)
                }
            }
        } else {
            if (isRail) navView.translationX = value
            else navView.translationY = value

            navView.menu.forEach {
                navView.findViewById<View>(it.itemId)?.apply {
                    translationX = 0f
                    translationY = 0f
                }
            }
            action(value)
        }
    }

    fun animateVisibility(view: View, visible: Boolean, animate: Boolean = true) {
        if (getIsEnabled(view) && animate && view.isVisible != visible) {
            view.isVisible = true
            startAnimation(
                view,
                view.animate().alpha(if (visible) 1f else 0f).withEndAction {
                    view.alpha = if (visible) 1f else 0f
                    view.isVisible = visible
                }
            )
        } else {
            view.alpha = if (visible) 1f else 0f
            view.isVisible = visible
        }
    }

    fun animateTranslation(view: View, old: Int, newHeight: Int) = view.run {
        if (getIsEnabled(view)) {
            clearAnimation()
            view.translationY = newHeight.toFloat() - old
            startAnimation(this, animate().translationY(0f))
        }
    }

    private fun getAnimationDuration(view: View): Long = view.context.applicationContext.run {
            MotionUtils.resolveThemeDuration(
                this, com.google.android.material.R.attr.motionDurationMedium1, 350
            ).toLong()
        }

    private fun getAnimationDurationSmall(view: View): Long = view.context.applicationContext.run {
            MotionUtils.resolveThemeDuration(
                this, com.google.android.material.R.attr.motionDurationShort1, 100
            ).toLong()
        }

    const val ANIMATIONS_KEY = "animations"
    const val SCROLL_ANIMATIONS_KEY = "shared_element"

    private fun getIsEnabled(view: View): Boolean = view.context.applicationContext.run {
            getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).getBoolean(ANIMATIONS_KEY, true)
        }

    private fun getScrollIsEnabled(view: View): Boolean = view.context.applicationContext.run {
            getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
                .getBoolean(SCROLL_ANIMATIONS_KEY, false)
        }

    fun setupTransition(
        fragment: Fragment, view: View, applyBackground: Boolean = true, axis: Int = MaterialSharedAxis.Z
    ) {
        if (applyBackground) {
            val color = MaterialColors.getColor(view, R.attr.echoBackground, 0)
            view.setBackgroundColor(color)
        }

        if (getIsEnabled(view)) {
            (view as? ViewGroup)?.isTransitionGroup = true
            fragment.exitTransition = MaterialSharedAxis(axis, true)
            fragment.reenterTransition = MaterialSharedAxis(axis, false)
            fragment.enterTransition = MaterialSharedAxis(axis, true)
            fragment.returnTransition = MaterialSharedAxis(axis, false)

            fragment.postponeEnterTransition()
            view.doOnPreDraw { fragment.startPostponedEnterTransition() }
        }
    }

    fun animatedWithAlpha(view: View, delay: Long = 0, vararg anim: Animation) {
        if (!getIsEnabled(view)) return
        val set = AnimationSet(true)
        set.interpolator = getInterpolator(view.context) as Interpolator
        val alpha = AlphaAnimation(0.0f, 1.0f)
        alpha.duration = getAnimationDurationSmall(view)
        alpha.startOffset = delay
        set.addAnimation(alpha)
        anim.forEach { set.addAnimation(it) }
        view.startAnimation(set)
    }

    fun applyTranslationAndScaleAnimation(
        view: View, amount: Int, delay: Long = 0
    ) {
        if (!getIsEnabled(view)) return
        if (!getScrollIsEnabled(view)) return
        val multiplier = amount.sign
        val rotateAnimation = RotateAnimation(
            5f * multiplier, 0f,
            view.width.toFloat() / 2, view.height.toFloat() / 2
        )
        rotateAnimation.duration = getAnimationDuration(view)
        val translate = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, multiplier * 0.5f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
        )
        translate.duration = getAnimationDuration(view)
        val from = 1f - 0.5f * multiplier.absoluteValue
        val scale = ScaleAnimation(
            from, 1f, from, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scale.duration = getAnimationDuration(view)
        animatedWithAlpha(view, delay, rotateAnimation, translate, scale)
    }

    fun applyTranslationYAnimation(view: View, amount: Int, delay: Long = 0) {
        if (!getIsEnabled(view)) return
        if (!getScrollIsEnabled(view)) return
        val multiplier = amount.sign
        val translate = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, multiplier * 1.5f,
            Animation.RELATIVE_TO_SELF, 0f,
        )
        translate.duration = getAnimationDuration(view)
        translate.startOffset = delay
        animatedWithAlpha(view, delay, translate)
    }
}