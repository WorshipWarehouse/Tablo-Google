package com.example.ui.util

import androidx.compose.ui.focus.FocusRequester

/**
 * Safely requests focus on a [FocusRequester] without crashing with
 * [IllegalStateException] if the Composable is unattached, hidden, or in transition.
 */
fun FocusRequester?.safeRequest(): Boolean {
    if (this == null) return false
    return try {
        requestFocus()
        true
    } catch (_: Throwable) {
        false
    }
}
