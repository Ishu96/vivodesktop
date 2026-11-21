package com.brar.display

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            TextView(this).apply {
                text = "VivoDesktop"
                textSize = 24f
                gravity = Gravity.CENTER
            },
        )
    }
}
