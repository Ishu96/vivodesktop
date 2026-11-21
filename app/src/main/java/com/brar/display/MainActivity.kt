package com.brar.display

import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.SharedPreferences
import android.util.DisplayMetrics
import java.util.Locale

private const val PREFS_NAME = "vivo_desktop_settings"
private const val PREF_LAST_DISPLAY_ID = "last_display_id"
const val EXTRA_DISPLAY_ID = "com.brar.display.extra.DISPLAY_ID"
const val EXTRA_DISPLAY_NAME = "com.brar.display.extra.DISPLAY_NAME"

private data class DisplayInfo(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

class MainActivity : ComponentActivity() {
    private lateinit var displayManager: DisplayManager
    private lateinit var preferences: SharedPreferences
    private var displays by mutableStateOf<List<DisplayInfo>>(emptyList())
    private var selectedDisplayId by mutableStateOf(-1)
    private var lastSelectionUnavailable by mutableStateOf(false)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplays()
        override fun onDisplayRemoved(displayId: Int) = refreshDisplays()
        override fun onDisplayChanged(displayId: Int) = refreshDisplays()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(DisplayManager::class.java)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedDisplayId = preferences.getInt(PREF_LAST_DISPLAY_ID, -1)
        setContent {
            VivoDesktopTheme {
                DisplayPickerScreen(
                    displays = displays,
                    selectedDisplayId = selectedDisplayId,
                    lastSelectionUnavailable = lastSelectionUnavailable,
                    onSelectDisplay = { selectedDisplayId = it },
                    onRefresh = ::refreshDisplays,
                    onContinue = ::openSelectedDisplay,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, null)
        refreshDisplays()
    }

    override fun onStop() {
        displayManager.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::displayManager.isInitialized) refreshDisplays()
    }

    private fun refreshDisplays() {
        val available = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.state == Display.STATE_ON }
            .map(::toDisplayInfo)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }

        displays = available
        val rememberedId = preferences.getInt(PREF_LAST_DISPLAY_ID, -1)
        val rememberedDisplayIsAvailable = available.any { it.id == rememberedId }
        lastSelectionUnavailable = rememberedId >= 0 && !rememberedDisplayIsAvailable
        selectedDisplayId = when {
            available.any { it.id == selectedDisplayId } -> selectedDisplayId
            rememberedDisplayIsAvailable -> rememberedId
            else -> available.firstOrNull()?.id ?: -1
        }
    }

    private fun toDisplayInfo(display: Display): DisplayInfo {
        val mode = display.mode
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return DisplayInfo(
            id = display.displayId,
            name = display.name.takeIf { it.isNotBlank() } ?: getString(R.string.unnamed_display),
            width = mode?.physicalWidth ?: metrics.widthPixels,
            height = mode?.physicalHeight ?: metrics.heightPixels,
            refreshRate = display.refreshRate,
        )
    }

    private fun openSelectedDisplay() {
        val displayInfo = displays.firstOrNull { it.id == selectedDisplayId }
        val display = displayInfo?.let { displayManager.getDisplay(it.id) }
        if (displayInfo == null || display == null || display.state != Display.STATE_ON) {
            Toast.makeText(this, R.string.display_no_longer_available, Toast.LENGTH_SHORT).show()
            refreshDisplays()
            return
        }

        preferences.edit().putInt(PREF_LAST_DISPLAY_ID, displayInfo.id).apply()
        val intent = Intent(this, DisplayPreviewActivity::class.java).apply {
            putExtra(EXTRA_DISPLAY_ID, displayInfo.id)
            putExtra(EXTRA_DISPLAY_NAME, displayInfo.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayInfo.id)
            startActivity(intent, options.toBundle())
        } else {
            DisplayPreviewPresentation(this, display, displayInfo.name).show()
        }
    }
}

@Composable
private fun DisplayPickerScreen(
    displays: List<DisplayInfo>,
    selectedDisplayId: Int,
    lastSelectionUnavailable: Boolean,
    onSelectDisplay: (Int) -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(
                        text = "V I V O D E S K T O P",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = "Choose your\ndisplay",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Pick a connected screen to open VivoDesktop on it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("AVAILABLE SCREENS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Refresh",
                    modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (lastSelectionUnavailable) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp)) {
                    Text(
                        "Your previous display is unavailable. Select a connected screen to continue.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (displays.isEmpty()) {
                EmptyDisplayState(onRefresh = onRefresh, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displays, key = { it.id }) { display ->
                        DisplayCard(
                            display = display,
                            selected = display.id == selectedDisplayId,
                            onClick = { onSelectDisplay(display.id) },
                        )
                    }
                }
                Button(
                    onClick = onContinue,
                    enabled = displays.any { it.id == selectedDisplayId },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Open on this display", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyDisplayState(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(82.dp)) {
            BoxCenter { Text("↗", fontSize = 34.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(20.dp))
        Text("No external display yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect a screen that supports Android presentations, then refresh to see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh, shape = RoundedCornerShape(20.dp)) { Text("Check again") }
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun DisplayCard(display: DisplayInfo, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(54.dp)) {
                BoxCenter { Text("▣", fontSize = 24.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(display.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${display.width} × ${display.height}  ·  ${String.format(Locale.getDefault(), "%.0f", display.refreshRate)} Hz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Display ${display.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                    Text("✓", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun VivoDesktopTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFB9C6FF),
            secondaryContainer = Color(0xFF35436B),
            primaryContainer = Color(0xFF27365F),
            tertiaryContainer = Color(0xFF463B26),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4355A8),
            secondaryContainer = Color(0xFFE1E6FF),
            primaryContainer = Color(0xFFDCE2FF),
            tertiaryContainer = Color(0xFFFFE8C2),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

class DisplayPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayId = intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        val manager = getSystemService(DisplayManager::class.java)
        val display = manager.getDisplay(displayId)
        if (display == null || display.state != Display.STATE_ON) {
            Toast.makeText(this, R.string.display_no_longer_available, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { display.name }
        setContent {
            VivoDesktopTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("VivoDesktop", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Display connected", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(displayName, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(24.dp))
                        Text("Display ID  $displayId", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = { finish() }, shape = RoundedCornerShape(20.dp)) { Text("Done") }
                    }
                }
            }
        }
    }
}

private class DisplayPreviewPresentation(
    context: Context,
    display: Display,
    displayName: String,
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(context).apply {
            text = "VivoDesktop"
            textSize = 32f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(context).apply {
            text = "Display connected\n$displayName"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(context).apply {
            text = "Done"
            setOnClickListener { dismiss() }
        })
        setContentView(root)
    }
}
