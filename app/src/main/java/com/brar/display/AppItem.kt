package com.brar.display

import android.graphics.drawable.Drawable

/** A launcher entry and its persisted pinned state. */
data class AppItem(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    val pinned: Boolean = false,
)
