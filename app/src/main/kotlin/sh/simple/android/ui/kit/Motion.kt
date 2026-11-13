package sh.simple.android.ui.kit

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object Motion {
    private val ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    private val morphEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> hover(): FiniteAnimationSpec<T> = tween(120, easing = ease)
    fun <T> fade(): FiniteAnimationSpec<T> = tween(120, easing = ease)
    fun <T> press(): FiniteAnimationSpec<T> = tween(50, easing = ease)
    fun <T> morph(): FiniteAnimationSpec<T> = tween(200, easing = morphEase)
    fun <T> layout(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, stiffness = 440f)
    fun <T> conversation(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, stiffness = 224f)
    fun <T> present(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, stiffness = 158f)

    val layoutOffset = spring(Spring.DampingRatioNoBouncy, stiffness = 440f, visibilityThreshold = IntOffset(1, 1))
    val layoutSize = spring(Spring.DampingRatioNoBouncy, stiffness = 440f, visibilityThreshold = IntSize(1, 1))

    const val PRESS_SCALE = 0.98f
}
