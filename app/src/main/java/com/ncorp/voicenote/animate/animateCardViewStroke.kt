package com.ncorp.voicenote.animate

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import com.google.android.material.card.MaterialCardView

fun animateCardViewStroke(cardView: MaterialCardView) {
	val colors = intArrayOf(
		Color.RED,
		Color.MAGENTA,
		Color.BLUE,
		Color.CYAN,
		Color.GREEN,
		Color.YELLOW,
		Color.RED
	)

	val colorAnimator = ValueAnimator.ofFloat(0f, (colors.size - 1).toFloat())
	colorAnimator.duration = 5000L  // 5 saniye döngü süresi
	colorAnimator.repeatCount = ValueAnimator.INFINITE
	colorAnimator.addUpdateListener { animator ->
		val position = animator.animatedValue as Float
		val index = position.toInt()
		val fraction = position - index

		val startColor = colors[index]
		val endColor = colors[(index + 1) % colors.size]

		val evaluator = ArgbEvaluator()
		val animatedColor = evaluator.evaluate(fraction, startColor, endColor) as Int

		cardView.strokeColor = animatedColor
	}
	colorAnimator.start()
}
