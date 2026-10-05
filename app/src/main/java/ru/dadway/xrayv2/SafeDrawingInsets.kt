package ru.dadway.xrayv2

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

/** Keeps controls clear of system bars, cutouts and the keyboard in every window size. */
internal fun View.applySafeDrawingInsets(
    includeIme: Boolean = true,
    includeTop: Boolean = true,
) {
    // Insets are physical left/right values, so preserve physical padding as well (also in RTL).
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { target, insets ->
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        val safe = insets.getInsets(types)
        target.updatePadding(
            left = initialLeft + safe.left,
            top = initialTop + if (includeTop) safe.top else 0,
            right = initialRight + safe.right,
            bottom = initialBottom + safe.bottom,
        )
        insets
    }
    // Dialog content is not attached when inflated. Request its first insets after attachment.
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
