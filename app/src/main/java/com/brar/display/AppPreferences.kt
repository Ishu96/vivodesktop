package com.brar.display

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPinnedApps(): Set<String> =
        preferences.getStringSet(PINNED_APPS_KEY, emptySet())?.toSet().orEmpty()

    fun addPinnedApp(packageName: String) {
        val pinnedApps = getPinnedApps().toMutableSet().apply { add(packageName) }
        preferences.edit().putStringSet(PINNED_APPS_KEY, pinnedApps).apply()
    }

    fun removePinnedApp(packageName: String) {
        val pinnedApps = getPinnedApps().toMutableSet().apply { remove(packageName) }
        preferences.edit().putStringSet(PINNED_APPS_KEY, pinnedApps).apply()
    }

    private companion object {
        const val PREFS_NAME = "AppLauncherPrefs"
        const val PINNED_APPS_KEY = "pinned_apps"
    }
}
