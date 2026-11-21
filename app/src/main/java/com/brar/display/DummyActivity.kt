package com.brar.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class DummyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val displayId = intent.getIntExtra(EXTRA_DISPLAY_ID, 0)

        if (packageName == null || !isValidDisplayId(displayId)) {
            Toast.makeText(this, "Display not connected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Unable to launch $packageName", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val options = ActivityOptions.makeBasic().apply { setLaunchDisplayId(displayId) }
        startActivity(launchIntent, options.toBundle())
        finish()
    }

    private fun isValidDisplayId(displayId: Int): Boolean {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        return displayManager.displays.any { it.displayId == displayId }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_DISPLAY_ID = "displayId"
    }
}
