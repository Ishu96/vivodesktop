package com.brar.display

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = AppPreferences(this)

        setContent {
            VivoDesktopTheme {
                LauncherScreen(
                    onLaunch = ::launchApp,
                    onCreateShortcut = ::createPinShortcut,
                    preferences = preferences,
                    onDisplayInfo = ::showDisplayInfo,
                )
            }
        }
    }

    private fun launchApp(app: AppItem, display: Display) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
            val options = ActivityOptions.makeBasic().apply { setLaunchDisplayId(display.displayId) }
            startActivity(intent, options.toBundle())
        } catch (error: Exception) {
            Toast.makeText(this, "Error launching app: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPinShortcut(app: AppItem, display: Display) {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        if (!shortcutManager.isRequestPinShortcutSupported) return

        val intent = Intent(this, DummyActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(DummyActivity.EXTRA_PACKAGE, app.packageName)
            putExtra(DummyActivity.EXTRA_DISPLAY_ID, display.displayId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }

        val shortcut = ShortcutInfo.Builder(this, app.packageName)
            .setShortLabel(app.name)
            .setIcon(android.graphics.drawable.Icon.createWithBitmap(Utils.drawableToBitmap(app.icon)))
            .setIntent(intent)
            .build()

        shortcutManager.requestPinShortcut(shortcut, null)
    }

    private fun showDisplayInfo(display: Display) {
        // handled in the Compose dialog by display details
    }
}

@Composable
private fun VivoDesktopTheme(content: @Composable () -> Unit) {
    val colorScheme = dynamicColorScheme()
    MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, content = content)
}

@Composable
private fun dynamicColorScheme(): ColorScheme {
    val context = LocalContext.current
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        androidx.compose.material3.dynamicDarkColorScheme(context)
            .let { if (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) it else androidx.compose.material3.dynamicLightColorScheme(context) }
    } else {
        lightColorScheme()
    }
}

@Composable
private fun LauncherScreen(
    onLaunch: (AppItem, Display) -> Unit,
    onCreateShortcut: (AppItem, Display) -> Unit,
    preferences: AppPreferences,
    onDisplayInfo: (Display) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var displayAction by remember { mutableStateOf<DisplayAction?>(null) }
    var infoDisplay by remember { mutableStateOf<Display?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        apps = loadApps(context, preferences)
        loading = false
    }

    fun refreshApps() {
        coroutineScope.launch {
            apps = loadApps(context, preferences)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("VivoDesktop", maxLines = 1) },
                actions = {
                    IconButton(onClick = { infoDisplay = getDisplays(context).firstOrNull() }) {
                        Icon(Icons.Default.Info, contentDescription = "Display information")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            apps.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("No launcher apps found")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        onClick = { selectedApp = app },
                        onTogglePin = {
                            if (app.pinned) preferences.removePinnedApp(app.packageName)
                            else preferences.addPinnedApp(app.packageName)
                            refreshApps()
                        },
                    )
                }
            }
        }

        if (selectedApp != null) {
            val app = selectedApp!!
            ModalBottomSheet(onDismissRequest = { selectedApp = null }) {
                Text(app.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(8.dp))
                ListItem(headlineContent = { Text("Launch") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { displayAction = DisplayAction.Launch(app); selectedApp = null }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) { Text("Choose display") }
                OutlinedButton(onClick = {
                    if (app.pinned) preferences.removePinnedApp(app.packageName) else preferences.addPinnedApp(app.packageName)
                    selectedApp = null
                    refreshApps()
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Icon(Icons.Outlined.PushPin, null)
                    Spacer(Modifier.width(8.dp)); Text(if (app.pinned) "Unpin app" else "Pin app")
                }
                OutlinedButton(onClick = { displayAction = DisplayAction.Shortcut(app); selectedApp = null }, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp)) { Text("Create shortcut") }
            }
        }

        if (displayAction != null) {
            val action = displayAction!!
            val displays = getDisplays(context)
            AlertDialog(
                onDismissRequest = { displayAction = null },
                title = { Text("Select display") },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(displays) { display ->
                            FilledTonalButton(onClick = {
                                when (action) {
                                    is DisplayAction.Launch -> onLaunch(action.app, display)
                                    is DisplayAction.Shortcut -> onCreateShortcut(action.app, display)
                                }
                                displayAction = null
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("${display.name} (ID: ${display.displayId})")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { displayAction = null }) { Text("Cancel") } },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            )
        }

        if (infoDisplay != null) {
            val display = infoDisplay!!
            val details = Utils.getDisplayInfo(display, context)
            AlertDialog(
                onDismissRequest = { infoDisplay = null },
                title = { Text("Display info") },
                text = {
                    Column {
                        Text(details)
                        Spacer(Modifier.height(12.dp))
                        val displays = getDisplays(context)
                        if (displays.size > 1) {
                            Text("Choose display", style = MaterialTheme.typography.labelLarge)
                            displays.forEach { item ->
                                TextButton(onClick = { infoDisplay = item }) {
                                    Text("${item.name} (ID: ${item.displayId})")
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { infoDisplay = null }) { Text("OK") } },
            )
        }
    }
}

private sealed interface DisplayAction {
    data class Launch(val app: AppItem) : DisplayAction
    data class Shortcut(val app: AppItem) : DisplayAction
}

@Composable
private fun AppCard(app: AppItem, onClick: () -> Unit, onTogglePin: () -> Unit) {
    val bitmap = remember(app.icon) { Utils.drawableToBitmap(app.icon).asImageBitmap() }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillFillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap, null, Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Text(app.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.pinned) {
                FilledTonalIconButton(onClick = onTogglePin) {
                    Icon(Icons.Outlined.PushPin, contentDescription = "Unpin app")
                }
            }
        }
    }
}

private fun getDisplays(context: android.content.Context): Array<Display> {
    val displayManager = context.getSystemService(android.hardware.display.DisplayManager::class.java)
    return displayManager?.displays ?: emptyArray()
}

private suspend fun loadApps(context: android.content.Context, preferences: AppPreferences): List<AppItem> =
    withContext(Dispatchers.IO) {
        val pinned = preferences.getPinnedApps()
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)

        resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val name = resolveInfo.loadLabel(context.packageManager).toString()
            val icon: Drawable = resolveInfo.loadIcon(context.packageManager)
            try {
                val applicationInfo: ApplicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
                AppItem(applicationInfo.packageName, name, icon, pinned.contains(applicationInfo.packageName))
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareByDescending<AppItem> { it.pinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
