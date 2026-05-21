package com.vibeflow.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.input.TextFieldValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibeFlowPremiumTheme {
                MainScreen(
                    onSetWallpaper = {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(this@MainActivity, VibeFlowWallpaperService::class.java)
                        )
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

// ─── CUSTOM PRESETS DATA & SERIALIZATION ─────────────────────────────────────
data class CustomPreset(
    val name: String,
    val colorA: Color,
    val colorB: Color,
    val visualStyle: Int = 0,
    val speed: Float = 0.5f,
    val viscosity: Float = 0.6f,
    val scale: Float = 0.5f,
    val noise: Float = 0.3f
)

fun loadCustomPresets(prefs: android.content.SharedPreferences): List<CustomPreset> {
    val raw = prefs.getString("custom_presets", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    val list = mutableListOf<CustomPreset>()
    try {
        val parts = raw.split(";")
        for (part in parts) {
            if (part.isEmpty()) continue
            val tokens = part.split("|")
            if (tokens.size >= 3) {
                val name = tokens[0]
                val colorA = Color(tokens[1].toLong(16).toInt())
                val colorB = Color(tokens[2].toLong(16).toInt())
                val visualStyle = tokens.getOrNull(3)?.toIntOrNull() ?: 0
                val speed = tokens.getOrNull(4)?.toFloatOrNull() ?: 0.5f
                val viscosity = tokens.getOrNull(5)?.toFloatOrNull() ?: 0.6f
                val scale = tokens.getOrNull(6)?.toFloatOrNull() ?: 0.5f
                val noise = tokens.getOrNull(7)?.toFloatOrNull() ?: 0.3f
                list.add(CustomPreset(name, colorA, colorB, visualStyle, speed, viscosity, scale, noise))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveCustomPresets(prefs: android.content.SharedPreferences, presets: List<CustomPreset>) {
    val sb = java.lang.StringBuilder()
    for (i in presets.indices) {
        val p = presets[i]
        val hexA = java.lang.Long.toHexString(p.colorA.toArgb().toLong() and 0xFFFFFFFFL)
        val hexB = java.lang.Long.toHexString(p.colorB.toArgb().toLong() and 0xFFFFFFFFL)
        sb.append("${p.name}|$hexA|$hexB|${p.visualStyle}|${p.speed}|${p.viscosity}|${p.scale}|${p.noise}")
        if (i < presets.size - 1) {
            sb.append(";")
        }
    }
    prefs.edit().putString("custom_presets", sb.toString()).apply()
}

data class CustomShader(
    val name: String,
    val code: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

fun loadCustomShaders(prefs: android.content.SharedPreferences): List<CustomShader> {
    val raw = prefs.getString("custom_shaders", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    val list = mutableListOf<CustomShader>()
    try {
        val parts = raw.split(";")
        for (part in parts) {
            if (part.isEmpty()) continue
            val tokens = part.split("|")
            if (tokens.size >= 2) {
                val name = tokens[0]
                val base64Code = tokens[1]
                val code = String(android.util.Base64.decode(base64Code, android.util.Base64.DEFAULT), kotlin.text.Charsets.UTF_8)
                val id = if (tokens.size >= 3) tokens[2] else java.util.UUID.randomUUID().toString()
                list.add(CustomShader(name, code, id))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveCustomShaders(prefs: android.content.SharedPreferences, shaders: List<CustomShader>) {
    val sb = java.lang.StringBuilder()
    for (i in shaders.indices) {
        val s = shaders[i]
        val base64Code = android.util.Base64.encodeToString(s.code.toByteArray(kotlin.text.Charsets.UTF_8), android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        sb.append("${s.name}|$base64Code|${s.id}")
        if (i < shaders.size - 1) {
            sb.append(";")
        }
    }
    prefs.edit().putString("custom_shaders", sb.toString()).apply()
}

fun shareCustomShader(context: android.content.Context, shader: CustomShader) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "VibeFlow Custom Shader: ${shader.name}")
        putExtra(android.content.Intent.EXTRA_TEXT, shader.code)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share VibeFlow Shader"))
}

fun parseImportCode(code: String): CustomPreset? {
    val clean = code.trim()
    if (!clean.startsWith("VIBE:")) return null
    try {
        val data = clean.substring(5)
        val tokens = data.split("|")
        if (tokens.size >= 3) {
            val name = tokens[0]
            val colorA = Color(tokens[1].toLong(16).toInt())
            val colorB = Color(tokens[2].toLong(16).toInt())
            val visualStyle = tokens.getOrNull(3)?.toIntOrNull() ?: 0
            val speed = tokens.getOrNull(4)?.toFloatOrNull() ?: 0.5f
            val viscosity = tokens.getOrNull(5)?.toFloatOrNull() ?: 0.6f
            val scale = tokens.getOrNull(6)?.toFloatOrNull() ?: 0.5f
            val noise = tokens.getOrNull(7)?.toFloatOrNull() ?: 0.3f
            return CustomPreset(name, colorA, colorB, visualStyle, speed, viscosity, scale, noise)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun generateExportCode(preset: CustomPreset): String {
    val hexA = java.lang.Long.toHexString(preset.colorA.toArgb().toLong() and 0xFFFFFFFFL)
    val hexB = java.lang.Long.toHexString(preset.colorB.toArgb().toLong() and 0xFFFFFFFFL)
    return "VIBE:${preset.name}|$hexA|$hexB|${preset.visualStyle}|${preset.speed}|${preset.viscosity}|${preset.scale}|${preset.noise}"
}

fun applyCustomPreset(prefs: android.content.SharedPreferences, preset: CustomPreset) {
    prefs.edit()
        .putString("active_theme_name", preset.name)
        .putInt("theme_color_a", preset.colorA.toArgb())
        .putInt("theme_color_b", preset.colorB.toArgb())
        .putInt("visual_style", preset.visualStyle)
        .putFloat("animation_speed", preset.speed)
        .putFloat("fluid_viscosity", preset.viscosity)
        .putFloat("wave_scale", preset.scale)
        .putFloat("noise_intensity", preset.noise)
        .putBoolean("smart_sync_spotify", false)
        .putBoolean("time_of_day", false)
        .apply()
}

fun colorFromHue(hue: Float): Color {
    return Color.hsv(hue, 0.85f, 0.9f)
}

// ─── THEME & COLORS ──────────────────────────────────────────────────────────
val PremiumBlack = Color(0xFF050507)
val GlassDark = Color(0x661A1A24)
val AccentPrimary = Color(0xFF4A90E2) // iOS-like blue
val AccentSecondary = Color(0xFFA259FF) // Vibrant Purple
val TextWhite = Color(0xFFF5F5F7)
val TextMuted = Color(0xFF86868B)

@Composable
fun VibeFlowPremiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PremiumBlack,
            surface = GlassDark,
            primary = AccentPrimary,
            secondary = AccentSecondary,
            onBackground = TextWhite,
            onSurface = TextWhite
        ),
        content = content
    )
}

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    THEMES("Themes", Icons.Rounded.Palette),
    CREATOR("Creator", Icons.Rounded.Brush),
    TUNING("Tuning", Icons.Rounded.Tune)
}

@Composable
fun PremiumTabBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    colA: Color,
    colB: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            
            val backgroundBrush = if (isSelected) {
                Brush.horizontalGradient(
                    listOf(
                        colA,
                        colB
                    )
                )
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }
            
            val textColor = if (isSelected) Color.White else TextMuted
            val iconColor = if (isSelected) Color.White else TextMuted
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(backgroundBrush)
                    .then(
                        if (isSelected) Modifier
                            .background(Color.White.copy(alpha = 0.15f)) // Glassy overlay to brighten
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.25f),
                                RoundedCornerShape(28.dp)
                            ) else Modifier
                    )
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─── MAIN UI ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(onSetWallpaper: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE) }
    
    var bgColA by remember { mutableStateOf(Color(0xFF141E30)) }
    var bgColB by remember { mutableStateOf(Color(0xFF243B55)) }

    val updateBgColors = {
        val musicSyncOn = prefs.getBoolean("smart_sync_spotify", true)
        val musicSyncActive = prefs.getBoolean("is_spotify_active", false)
        val todOn = prefs.getBoolean("time_of_day", false)

        if (musicSyncOn && musicSyncActive) {
            bgColA = Color(prefs.getInt("spotify_color_a", 0xFF141E30.toInt()))
            bgColB = Color(prefs.getInt("spotify_color_b", 0xFF243B55.toInt()))
        } else if (todOn) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour in 6..10) { bgColA = Color(0xFFFFA07A); bgColB = Color(0xFFFF4500) }
            else if (hour in 11..16) { bgColA = Color(0xFF00BFFF); bgColB = Color(0xFF87CEFA) }
            else if (hour in 17..20) { bgColA = Color(0xFFFF4500); bgColB = Color(0xFF4B0082) }
            else { bgColA = Color(0xFF141E30); bgColB = Color(0xFF243B55) }
        } else {
            bgColA = Color(prefs.getInt("theme_color_a", 0xFF141E30.toInt()))
            bgColB = Color(prefs.getInt("theme_color_b", 0xFF243B55.toInt()))
        }
    }

    var customPresets by remember { mutableStateOf(loadCustomPresets(prefs)) }
    var customShaders by remember { mutableStateOf(loadCustomShaders(prefs)) }
    var showAdvancedSliders by remember { mutableStateOf(prefs.getBoolean("show_advanced_sliders", false)) }
    var showImportDialog by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            updateBgColors()
            if (key == "show_advanced_sliders") {
                showAdvancedSliders = prefs.getBoolean("show_advanced_sliders", false)
            }
            if (key == "custom_presets") {
                customPresets = loadCustomPresets(prefs)
            }
            if (key == "custom_shaders") {
                customShaders = loadCustomShaders(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        updateBgColors()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val animColA by animateColorAsState(targetValue = bgColA, animationSpec = tween(1500), label = "colA")
    val animColB by animateColorAsState(targetValue = bgColB, animationSpec = tween(1500), label = "colB")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(animColA, animColB), start = Offset(0f, 0f), end = Offset(1000f, 1000f)))
    ) {
        
        // Track currently selected theme, sync state and active tab for UI
        var activeThemeName by remember { mutableStateOf(prefs.getString("active_theme_name", "Deep Space") ?: "Deep Space") }
        var isMusicSyncEnabled by remember { mutableStateOf(prefs.getBoolean("smart_sync_spotify", true)) }
        var timeOfDayEnabled by remember { mutableStateOf(prefs.getBoolean("time_of_day", false)) }
        var creatorTabMode by remember { mutableIntStateOf(prefs.getInt("creator_tab_mode", 0)) }
        
        val pagerState = rememberPagerState(initialPage = 0) { AppTab.values().size }
        val coroutineScope = rememberCoroutineScope()
        val selectedTab = AppTab.values()[pagerState.currentPage]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header with the elegant compact Apply button next to Settings icon
            HeaderSection(animColA, animColB, onSetWallpaper)

            // Swipable Tab Content Area using HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val tab = AppTab.values()[page]
                when (tab) {
                    AppTab.THEMES -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // 1. Curated collections carousel
                            Text("Curated Collections", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                            ThemesCarousel(activeThemeName) { theme ->
                                activeThemeName = theme.name
                                isMusicSyncEnabled = false
                                timeOfDayEnabled = false
                                prefs.edit()
                                    .putString("active_theme_name", theme.name)
                                    .putInt("theme_color_a", theme.colorA.toArgb())
                                    .putInt("theme_color_b", theme.colorB.toArgb())
                                    .putBoolean("smart_sync_spotify", false)
                                    .putBoolean("time_of_day", false)
                                    .apply()
                            }

                            // 1b. Custom saved themes carousel
                            Text("My Custom Styles", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                            CustomThemesCarousel(
                                activeThemeName = activeThemeName,
                                presets = customPresets,
                                onPresetSelected = { preset ->
                                    activeThemeName = preset.name
                                    isMusicSyncEnabled = false
                                    timeOfDayEnabled = false
                                    applyCustomPreset(prefs, preset)
                                },
                                onDeletePreset = { preset ->
                                    val updated = customPresets.filter { it.name != preset.name }
                                    customPresets = updated
                                    saveCustomPresets(prefs, updated)
                                    if (activeThemeName == preset.name) {
                                        activeThemeName = "Deep Space"
                                        prefs.edit()
                                            .putString("active_theme_name", "Deep Space")
                                            .putInt("theme_color_a", 0xFF141E30.toInt())
                                            .putInt("theme_color_b", 0xFF243B55.toInt())
                                            .apply()
                                    }
                                },
                                onExportPreset = { preset ->
                                    val code = generateExportCode(preset)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("VibeFlow Preset", code)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Export code copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )

                            // 2. Visual Style selection
                            Text("Visual Style", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                            StyleSelectionRow(prefs)
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            // 3. Custom Saved Shaders Section
                            Text("My Custom Shaders", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var shaderToDeleteInThemes by remember { mutableStateOf<CustomShader?>(null) }
                            if (shaderToDeleteInThemes != null) {
                                ConfirmDeleteShaderDialog(
                                    shaderName = shaderToDeleteInThemes!!.name,
                                    onConfirm = {
                                        val list = loadCustomShaders(prefs)
                                        val updated = list.filter { it.id != shaderToDeleteInThemes!!.id }
                                        saveCustomShaders(prefs, updated)
                                        if (prefs.getString("active_custom_shader_id", "") == shaderToDeleteInThemes!!.id) {
                                            prefs.edit().putString("active_custom_shader_id", "").apply()
                                            // Reset theme/visual style if deleted active
                                            activeThemeName = "Deep Space"
                                            prefs.edit()
                                                .putInt("visual_style", 0) // Default liquid
                                                .putString("active_theme_name", "Deep Space")
                                                .putInt("theme_color_a", 0xFF141E30.toInt())
                                                .putInt("theme_color_b", 0xFF243B55.toInt())
                                                .apply()
                                        }
                                        shaderToDeleteInThemes = null
                                        android.widget.Toast.makeText(context, "Shader deleted.", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onDismiss = { shaderToDeleteInThemes = null }
                                )
                            }

                            CustomShadersCarousel(
                                activeShaderId = prefs.getString("active_custom_shader_id", "") ?: "",
                                shaders = customShaders,
                                onShaderSelected = { shader ->
                                    activeThemeName = shader.name
                                    isMusicSyncEnabled = false
                                    timeOfDayEnabled = false
                                    prefs.edit()
                                        .putInt("visual_style", 5) // Set style to custom shader
                                        .putString("custom_agsl_shader_code", shader.code)
                                        .putString("active_custom_shader_id", shader.id)
                                        .putString("active_theme_name", shader.name)
                                        .putBoolean("smart_sync_spotify", false)
                                        .putBoolean("time_of_day", false)
                                        .apply()
                                    android.widget.Toast.makeText(context, "Custom Shader Applied!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onDeleteShader = { shader ->
                                    shaderToDeleteInThemes = shader
                                },
                                onExportShader = { shader ->
                                    shareCustomShader(context, shader)
                                }
                            )

                            Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom TabBar
                        }
                    }
                    
                    AppTab.CREATOR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            CreatorSegmentControl(
                                selectedMode = creatorTabMode,
                                onModeSelected = { 
                                    creatorTabMode = it
                                    prefs.edit().putInt("creator_tab_mode", it).apply()
                                },
                                colA = animColA,
                                colB = animColB
                            )
                            
                            if (creatorTabMode == 0) {
                                Text("Custom Style Editor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                StyleEditorCard(
                                    prefs = prefs,
                                    customPresets = customPresets,
                                    colA = animColA,
                                    colB = animColB,
                                    onPresetsUpdated = { customPresets = it },
                                    onApplyPreset = { preset ->
                                        activeThemeName = preset.name
                                        isMusicSyncEnabled = false
                                        timeOfDayEnabled = false
                                        applyCustomPreset(prefs, preset)
                                    },
                                    onOpenImport = { showImportDialog = true }
                                )
                            } else {
                                Text("AGSL Shader Creator", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                ShaderCreatorCard(
                                    prefs = prefs,
                                    colA = animColA,
                                    colB = animColB,
                                    customShaders = customShaders
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom TabBar
                        }
                    }
                    
                    AppTab.TUNING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text("Smart Engine", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            SmartSyncCards(
                                context = context,
                                prefs = prefs,
                                isMusicSyncEnabled = isMusicSyncEnabled,
                                onMusicSyncChanged = { enabled ->
                                    isMusicSyncEnabled = enabled
                                    prefs.edit().putBoolean("smart_sync_spotify", enabled).apply()
                                    if (enabled) {
                                        activeThemeName = ""
                                        timeOfDayEnabled = false
                                        prefs.edit().putString("active_theme_name", "").putBoolean("time_of_day", false).apply()
                                    }
                                },
                                timeOfDayEnabled = timeOfDayEnabled,
                                onTimeOfDayChanged = { enabled ->
                                    timeOfDayEnabled = enabled
                                    prefs.edit().putBoolean("time_of_day", enabled).apply()
                                    if (enabled) {
                                        activeThemeName = ""
                                        isMusicSyncEnabled = false
                                        prefs.edit().putString("active_theme_name", "").putBoolean("smart_sync_spotify", false).apply()
                                    }
                                }
                            )

                            Text("Advanced Tuning", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            AdvancedTuningSection(prefs, showAdvancedSliders)

                            Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom TabBar
                        }
                    }
                }
            }
        }

        // Import Dialog Overlay
        if (showImportDialog) {
            ImportStyleDialog(
                prefs = prefs,
                customPresets = customPresets,
                onPresetsUpdated = { customPresets = it },
                onApplyPreset = { preset ->
                    activeThemeName = preset.name
                    isMusicSyncEnabled = false
                    timeOfDayEnabled = false
                    applyCustomPreset(prefs, preset)
                },
                onDismiss = { showImportDialog = false }
            )
        }

        // Bottom Premium Tab Bar (Glassmorphic pill selector)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, PremiumBlack.copy(alpha = 0.9f))))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            PremiumTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                },
                colA = animColA,
                colB = animColB
            )
        }
    }
}

@Composable
fun HeaderSection(
    colA: Color,
    colB: Color,
    onSetWallpaper: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "VibeFlow 2",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                color = TextWhite
            )
            Text(
                text = "Dynamic Environments",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AccentPrimary
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pill Apply Button (Premium dynamic neon gradient synced with wallpaper theme)
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Brush.horizontalGradient(listOf(colA, colB)))
                    .background(Color.White.copy(alpha = 0.15f)) // Glassy overlay to brighten
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(percent = 50))
                    .clickable { onSetWallpaper() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Apply",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            
            // Settings Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassDark)
                    .clickable { showSettings = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    if (showSettings) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE) }
        var isBatterySaver by remember { mutableStateOf(prefs.getBoolean("battery_saver", false)) }
        var isAdvancedSliders by remember { mutableStateOf(prefs.getBoolean("show_advanced_sliders", false)) }
        
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("App Settings", fontWeight = FontWeight.Bold, color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Battery Saver (30 FPS)", color = TextWhite, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isBatterySaver,
                            onCheckedChange = { isBatterySaver = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentPrimary
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show Advanced Sliders", color = TextWhite, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isAdvancedSliders,
                            onCheckedChange = { 
                                isAdvancedSliders = it 
                                prefs.edit().putBoolean("show_advanced_sliders", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            prefs.edit()
                                .putFloat("animation_speed", 0.5f)
                                .putFloat("fluid_viscosity", 0.6f)
                                .putFloat("particle_count", 0.8f)
                                .putFloat("wave_scale", 0.5f)
                                .putFloat("parallax_intensity", 0.5f)
                                .putFloat("contrast_level", 0.5f)
                                .putFloat("audio_reactivity", 0.5f)
                                .putFloat("bloom_intensity", 0.4f)
                                .putFloat("brightness", 1.0f)
                                .putFloat("saturation", 1.0f)
                                .putFloat("hue_shift", 0.0f)
                                .putFloat("vignette_intensity", 1.0f)
                                .putFloat("noise_intensity", 0.3f)
                                .apply()
                            showSettings = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Tuning Sliders", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    prefs.edit()
                        .putBoolean("battery_saver", isBatterySaver)
                        .putBoolean("show_advanced_sliders", isAdvancedSliders)
                        .apply()
                    showSettings = false 
                }) { 
                    Text("Save", color = AccentPrimary, fontWeight = FontWeight.Bold) 
                }
            },
            containerColor = PremiumBlack
        )
    }
}

// ─── CAROUSEL: THEMES ────────────────────────────────────────────────────────
data class ThemeItem(val name: String, val type: String, val colorA: Color, val colorB: Color)

@Composable
fun ThemesCarousel(activeThemeName: String, onThemeSelected: (ThemeItem) -> Unit) {
    val themes = listOf(
        ThemeItem("Liquid Gold", "Fluid Dynamics", Color(0xFFFFD700), Color(0xFFD2691E)),
        ThemeItem("Deep Space", "Particle Physics", Color(0xFF141E30), Color(0xFF243B55)),
        ThemeItem("Neon Vapor", "Retro Wave", Color(0xFFFF0099), Color(0xFF493240)),
        ThemeItem("Zen Garden", "Organic", Color(0xFF56AB2F), Color(0xFFA8E063)),
        ThemeItem("Crimson Core", "Volcanic", Color(0xFF8B0000), Color(0xFFDC143C)),
        ThemeItem("Arctic Ice", "Glacial", Color(0xFF00C9FF), Color(0xFF92FE9D)),
        ThemeItem("Cyberpunk", "Dystopian", Color(0xFFCCFF00), Color(0xFF9D00FF)),
        ThemeItem("Solar Flare", "Plasma", Color(0xFFFF512F), Color(0xFFF09819)),
        ThemeItem("Hyper Beast", "Acid Punk", Color(0xFF00FFCC), Color(0xFFFF007F)),
        ThemeItem("Deep Abyss", "Marine", Color(0xFF050515), Color(0xFF00FFFF)),
        ThemeItem("Vaporwave", "Synth Wave", Color(0xFF8A2387), Color(0xFFE94057)),
        ThemeItem("Forest Mystique", "Emerald Fog", Color(0xFF0F2027), Color(0xFF203A43)),
        ThemeItem("Electric Amethyst", "Cosmic Violet", Color(0xFF4776E6), Color(0xFF8E54E9)),
        ThemeItem("Toxic Waste", "Radioactive", Color(0xFF11998e), Color(0xFF38ef7d)),
        ThemeItem("Sunset Mirage", "Horizon Glow", Color(0xFFf12711), Color(0xFFf5af19)),
        ThemeItem("Candy Nebula", "Pastel Swirl", Color(0xFF83a4d4), Color(0xFFb6fbff)),
        ThemeItem("Obsidian Gold", "Luxury", Color(0xFF1a1a1a), Color(0xFFe6b800)),
        ThemeItem("Prism Shard", "Neon Fusion", Color(0xFF40E0D0), Color(0xFFFF0080))
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(themes) { theme ->
            ThemeCard(theme, isActive = theme.name == activeThemeName) {
                onThemeSelected(theme)
            }
        }
    }
}

@Composable
fun ThemeCard(theme: ThemeItem, isActive: Boolean, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered) 0.96f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .width(140.dp) // Slightly narrower to show next card
            .height(200.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(theme.colorA, theme.colorB)))
            .border(if (isActive) 2.dp else 0.dp, if (isActive) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable { 
                isHovered = !isHovered 
                onClick()
            }
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(theme.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(theme.type, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        
        // Active indicator
        if (isActive) {
            Box(modifier = Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color.White))
        }
    }
}

// ─── VISUAL STYLE SELECTION ──────────────────────────────────────────────────
@Composable
fun StyleSelectionRow(prefs: android.content.SharedPreferences) {
    var activeStyle by remember { mutableStateOf(prefs.getInt("visual_style", 0)) }
    val styles = listOf("Liquid Chrome", "Cosmic Plasma", "Frosted Aurora", "Pure Chrome", "Iridescent Pearl")
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        itemsIndexed(styles) { index, styleName ->
            val isActive = activeStyle == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isActive) AccentPrimary else GlassDark)
                    .border(1.dp, if (isActive) AccentPrimary else Color.White.copy(alpha=0.1f), RoundedCornerShape(16.dp))
                    .clickable { 
                        activeStyle = index
                        prefs.edit().putInt("visual_style", index).apply()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(styleName, color = if (isActive) PremiumBlack else TextWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── SMART SYNC SECTION (NEW FEATURES) ───────────────────────────────────────
@Composable
fun SmartSyncCards(
    context: Context, 
    prefs: android.content.SharedPreferences,
    isMusicSyncEnabled: Boolean, 
    onMusicSyncChanged: (Boolean) -> Unit,
    timeOfDayEnabled: Boolean,
    onTimeOfDayChanged: (Boolean) -> Unit
) {
    var fftEnabled by remember { mutableStateOf(prefs.getBoolean("fft_enabled", true)) }
    var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", true)) }

    // Dynamic launcher to request RECORD_AUDIO permission at runtime
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        fftEnabled = isGranted
        prefs.edit().putBoolean("fft_enabled", isGranted).apply()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            FeatureToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = "Music Sync",
                subtitle = "Sync ambient colors with active music player",
                checked = isMusicSyncEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                        if (!hasNotificationAccess) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    onMusicSyncChanged(checked)
                }
            )
        }
        GlassCard {
            FeatureToggleRow(
                icon = Icons.Rounded.Mic,
                title = "FFT Audio Visualizer",
                subtitle = "Reacts to music frequency spectrum",
                checked = fftEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasPermission) {
                            fftEnabled = true
                            prefs.edit().putBoolean("fft_enabled", true).apply()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        fftEnabled = false
                        prefs.edit().putBoolean("fft_enabled", false).apply()
                    }
                }
            )
        }
        GlassCard {
            FeatureToggleRow(
                icon = Icons.Rounded.Sensors,
                title = "Gyroscope Parallax",
                subtitle = "True 3D depth effect based on device tilt",
                checked = gyroEnabled,
                onCheckedChange = {
                    gyroEnabled = it
                    prefs.edit().putBoolean("gyro_enabled", it).apply()
                }
            )
        }
        GlassCard {
            FeatureToggleRow(
                icon = Icons.Rounded.WbSunny,
                title = "Time of Day Dynamics",
                subtitle = "Transitions lighting based on local time",
                checked = timeOfDayEnabled,
                onCheckedChange = onTimeOfDayChanged
            )
        }
    }
}

// ─── ADVANCED TUNING ─────────────────────────────────────────────────────────
@Composable
fun AdvancedTuningSection(prefs: android.content.SharedPreferences, showAdvanced: Boolean) {
    GlassCard {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            PremiumSliderRow("Animation Speed", "animation_speed", prefs, 0.5f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Fluid Viscosity", "fluid_viscosity", prefs, 0.6f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Particle Count", "particle_count", prefs, 0.8f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Wave Scale", "wave_scale", prefs, 0.5f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Parallax Intensity", "parallax_intensity", prefs, 0.5f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Contrast", "contrast_level", prefs, 0.5f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Audio Reactivity", "audio_reactivity", prefs, 0.5f)
            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
            PremiumSliderRow("Bloom Intensity", "bloom_intensity", prefs, 0.4f)

            if (showAdvanced) {
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                PremiumSliderRow("Brightness", "brightness", prefs, 1.0f, valueRange = 0.2f..2.0f, unit = "x")
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                PremiumSliderRow("Saturation", "saturation", prefs, 1.0f, valueRange = 0.0f..2.5f, unit = "x")
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                PremiumSliderRow("Hue Shift", "hue_shift", prefs, 0.0f, valueRange = 0.0f..360.0f, unit = "°")
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                PremiumSliderRow("Vignette Strength", "vignette_intensity", prefs, 1.0f, valueRange = 0.0f..2.0f, unit = "%")
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                PremiumSliderRow("Grain Intensity", "noise_intensity", prefs, 0.3f, valueRange = 0.0f..1.0f, unit = "%")
            }
        }
    }
}

// ─── NEW COMPOSABLE COMPONENTS FOR CUSTOM PRESETS & SHARING ────────────────
@Composable
fun CustomThemesCarousel(
    activeThemeName: String,
    presets: List<CustomPreset>,
    onPresetSelected: (CustomPreset) -> Unit,
    onDeletePreset: (CustomPreset) -> Unit,
    onExportPreset: (CustomPreset) -> Unit
) {
    if (presets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassDark)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No custom styles saved yet. Create one below!", color = TextMuted, fontSize = 14.sp)
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(presets) { preset ->
                CustomThemeCard(
                    preset = preset,
                    isActive = preset.name == activeThemeName,
                    onClick = { onPresetSelected(preset) },
                    onDelete = { onDeletePreset(preset) },
                    onExport = { onExportPreset(preset) }
                )
            }
        }
    }
}

@Composable
fun CustomThemeCard(
    preset: CustomPreset,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(preset.colorA, preset.colorB)))
            .border(if (isActive) 2.dp else 0.dp, if (isActive) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        // Active indicator
        if (isActive) {
            Box(modifier = Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Color.White))
        }

        // Action Buttons Overlay
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onExport() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1)
            Text("Custom Style", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun CustomShadersCarousel(
    activeShaderId: String,
    shaders: List<CustomShader>,
    onShaderSelected: (CustomShader) -> Unit,
    onDeleteShader: (CustomShader) -> Unit,
    onExportShader: (CustomShader) -> Unit
) {
    if (shaders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassDark)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No custom shaders saved yet. Create one in the Creator tab!", color = TextMuted, fontSize = 14.sp)
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(shaders) { shader ->
                val isActive = shader.id == activeShaderId
                CustomShaderCard(
                    shader = shader,
                    isActive = isActive,
                    onClick = { onShaderSelected(shader) },
                    onDelete = { onDeleteShader(shader) },
                    onExport = { onExportShader(shader) }
                )
            }
        }
    }
}

@Composable
fun CustomShaderCard(
    shader: CustomShader,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFF000000))))
            .border(if (isActive) 2.dp else 0.dp, if (isActive) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        // Glowing overlay if active
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // Action Buttons Overlay
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onExport() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Icon(Icons.Rounded.Code, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(shader.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 2)
            Text("AGSL Shader", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun StyleEditorCard(
    prefs: android.content.SharedPreferences,
    customPresets: List<CustomPreset>,
    colA: Color,
    colB: Color,
    onPresetsUpdated: (List<CustomPreset>) -> Unit,
    onApplyPreset: (CustomPreset) -> Unit,
    onOpenImport: () -> Unit
) {
    val context = LocalContext.current
    var presetName by remember { mutableStateOf("") }
    var hueA by remember { mutableFloatStateOf(0f) }
    var hueB by remember { mutableFloatStateOf(180f) }
    
    var selectedStyleIndex by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(0.5f) }
    var viscosity by remember { mutableFloatStateOf(0.6f) }
    var scale by remember { mutableFloatStateOf(0.5f) }
    var noise by remember { mutableFloatStateOf(0.3f) }
    
    val colorA = remember(hueA) { colorFromHue(hueA) }
    val colorB = remember(hueB) { colorFromHue(hueB) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "ShaderPreview")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )
    
    val rainbowColors = listOf(
        Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFD700), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFF8B00FF), Color(0xFFFF0000)
    )

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Live Gradient Preview Box & Color Bubbles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Organic real-time animating Shader Creator Preview
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    val width = size.width
                    val height = size.height
                    
                    when (selectedStyleIndex) {
                        0 -> { // Liquid Chrome
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(colorA, colorB),
                                    start = Offset(0f, 0f),
                                    end = Offset(width, height)
                                )
                            )
                            val waveTime = time * speed
                            val waveY = (height / 2f) + (height * 0.15f) * kotlin.math.sin(waveTime)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                                    center = Offset(width * 0.5f + width * 0.2f * kotlin.math.cos(waveTime), waveY),
                                    radius = width * scale
                                ),
                                center = Offset(width * 0.5f + width * 0.2f * kotlin.math.cos(waveTime), waveY),
                                radius = width * scale
                            )
                        }
                        1 -> { // Cosmic Plasma
                            drawRect(color = Color.Black)
                            val waveTime = time * speed
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(colorA.copy(alpha = 0.8f), Color.Transparent),
                                    radius = width * 0.7f * scale
                                ),
                                center = Offset(
                                    x = width / 2f + (width * 0.2f) * kotlin.math.sin(waveTime),
                                    y = height / 2f + (height * 0.2f) * kotlin.math.cos(waveTime)
                                ),
                                radius = width * 0.7f * scale
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(colorB.copy(alpha = 0.8f), Color.Transparent),
                                    radius = width * 0.6f * scale
                                ),
                                center = Offset(
                                    x = width / 2f + (width * 0.25f) * kotlin.math.cos(waveTime + 2f),
                                    y = height / 2f + (height * 0.25f) * kotlin.math.sin(waveTime + 2f)
                                ),
                                radius = width * 0.6f * scale
                            )
                        }
                        2 -> { // Frosted Aurora
                            drawRect(color = PremiumBlack)
                            val waveTime = time * speed
                            for (i in 0 until 3) {
                                val phase = waveTime + i * 1.5f
                                val startX = width * (0.2f + i * 0.3f)
                                val brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, colorA.copy(alpha = 0.5f), Color.Transparent),
                                    start = Offset(startX + (width * 0.1f) * kotlin.math.sin(phase), 0f),
                                    end = Offset(startX + (width * 0.1f) * kotlin.math.sin(phase + 1f), height)
                                )
                                drawRect(brush = brush)
                            }
                        }
                        3 -> { // Pure Chrome
                            val waveTime = time * speed
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(colorA, Color.White, colorB, Color.Black),
                                    start = Offset(0f, 0f),
                                    end = Offset(
                                        width + width * 0.3f * kotlin.math.cos(waveTime),
                                        height + height * 0.3f * kotlin.math.sin(waveTime)
                                    )
                                )
                            )
                        }
                        4 -> { // Iridescent Pearl
                            val waveTime = time * speed
                            val pastelA = colorA.copy(alpha = 0.6f)
                            val pastelB = colorB.copy(alpha = 0.6f)
                            val pearlPink = Color(0xFFFFD1DC).copy(alpha = 0.5f)
                            
                            drawRect(color = Color.White.copy(alpha = 0.1f))
                            drawCircle(
                                brush = Brush.radialGradient(listOf(pastelA, Color.Transparent), radius = width * scale),
                                center = Offset(width * 0.3f + width * 0.15f * kotlin.math.cos(waveTime), height * 0.5f),
                                radius = width * scale
                            )
                            drawCircle(
                                brush = Brush.radialGradient(listOf(pearlPink, Color.Transparent), radius = width * 0.8f),
                                center = Offset(width * 0.7f + width * 0.1f * kotlin.math.sin(waveTime), height * 0.3f),
                                radius = width * 0.8f
                            )
                            drawCircle(
                                brush = Brush.radialGradient(listOf(pastelB, Color.Transparent), radius = width * scale),
                                center = Offset(width * 0.5f + width * 0.2f * kotlin.math.sin(waveTime + 1f), height * 0.7f),
                                radius = width * scale
                            )
                        }
                    }
                    if (noise > 0.05f) {
                        val rand = java.util.Random(42)
                        for (i in 0 until (noise * 300).toInt()) {
                            val px = rand.nextFloat() * width
                            val py = rand.nextFloat() * height
                            val r = rand.nextFloat() * 1.5f + 0.5f
                            drawCircle(
                                color = Color.White.copy(alpha = noise * 0.35f),
                                center = Offset(px, py),
                                radius = r
                            )
                        }
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorA)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Text("A: ${hueA.toInt()}°", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorB)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Text("B: ${hueB.toInt()}°", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Hue Sliders
            Column {
                Text("Color A Hue", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(rainbowColors))
                    )
                    Slider(
                        value = hueA,
                        onValueChange = { hueA = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
            }

            Column {
                Text("Color B Hue", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(rainbowColors))
                    )
                    Slider(
                        value = hueB,
                        onValueChange = { hueB = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
            }

            // Shader Style Picker
            Column {
                Text("Base Shader", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                val stylesList = listOf("Liquid", "Cosmic", "Frosted", "Pure Chrome", "Iridescent Pearl")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(stylesList) { index, name ->
                        val isActive = selectedStyleIndex == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) AccentPrimary else GlassDark)
                                .border(1.dp, if (isActive) AccentPrimary else Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                                .clickable { selectedStyleIndex = index }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(name, color = if (isActive) PremiumBlack else TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Shader Tuning Sliders
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preset Tuning", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                
                // Animation Speed
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Animation Speed", fontSize = 12.sp, color = TextMuted)
                        Text(String.format("%.1fx", speed * 2f), fontSize = 12.sp, color = TextMuted)
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.1f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentPrimary)
                    )
                }

                // Viscosity
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Viscosity (Fluidity)", fontSize = 12.sp, color = TextMuted)
                        Text(String.format("%.1f", viscosity), fontSize = 12.sp, color = TextMuted)
                    }
                    Slider(
                        value = viscosity,
                        onValueChange = { viscosity = it },
                        valueRange = 0.1f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentPrimary)
                    )
                }

                // Complexity/Scale
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Complexity / Scale", fontSize = 12.sp, color = TextMuted)
                        Text(String.format("%.1f", scale), fontSize = 12.sp, color = TextMuted)
                    }
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.1f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentPrimary)
                    )
                }

                // Grain Noise
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grain Intensity", fontSize = 12.sp, color = TextMuted)
                        Text(String.format("%d%%", (noise * 100).toInt()), fontSize = 12.sp, color = TextMuted)
                    }
                    Slider(
                        value = noise,
                        onValueChange = { noise = it },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentPrimary)
                    )
                }
            }

            // Name Field
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text("Style Name", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = AccentPrimary,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Buttons: Save, Export, Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save Button (Premium custom gradient synced with background)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(colA, colB)))
                        .background(Color.White.copy(alpha = 0.15f)) // Glassy overlay to brighten
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable {
                            val name = presetName.trim().ifEmpty { "Custom Style ${customPresets.size + 1}" }
                            if (customPresets.any { it.name.equals(name, ignoreCase = true) }) {
                                android.widget.Toast.makeText(context, "A preset with this name already exists!", android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val newPreset = CustomPreset(name, colorA, colorB, selectedStyleIndex, speed, viscosity, scale, noise)
                            val updated = customPresets + newPreset
                            onPresetsUpdated(updated)
                            saveCustomPresets(prefs, updated)
                            onApplyPreset(newPreset)
                            presetName = ""
                            android.widget.Toast.makeText(context, "Style saved!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val exportPreset = CustomPreset(presetName.trim().ifEmpty { "Custom" }, colorA, colorB, selectedStyleIndex, speed, viscosity, scale, noise)
                        val code = generateExportCode(exportPreset)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("VibeFlow Preset", code)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied code to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onOpenImport,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ImportStyleDialog(
    prefs: android.content.SharedPreferences,
    customPresets: List<CustomPreset>,
    onPresetsUpdated: (List<CustomPreset>) -> Unit,
    onApplyPreset: (CustomPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var importCode by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString() ?: ""
                if (text.startsWith("VIBE:")) {
                    importCode = text
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Custom Style", fontWeight = FontWeight.Bold, color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a style code below. If you copied one to your clipboard, we've prefilled it for you.",
                    fontSize = 14.sp,
                    color = TextMuted,
                    lineHeight = 20.sp
                )
                
                OutlinedTextField(
                    value = importCode,
                    onValueChange = { importCode = it },
                    label = { Text("Share Code", color = TextMuted) },
                    placeholder = { Text("VIBE:Name|HexA|HexB", color = TextMuted.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val preset = parseImportCode(importCode)
                    if (preset != null) {
                        if (customPresets.any { it.name.equals(preset.name, ignoreCase = true) }) {
                            android.widget.Toast.makeText(context, "A preset with this name already exists!", android.widget.Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val updated = customPresets + preset
                        onPresetsUpdated(updated)
                        saveCustomPresets(prefs, updated)
                        onApplyPreset(preset)
                        android.widget.Toast.makeText(context, "Style imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(context, "Invalid share code format!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Import", color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PremiumBlack
    )
}

// ─── COMPONENTS ──────────────────────────────────────────────────────────────

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun FeatureToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextWhite)
            Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = PremiumBlack
            )
        )
    }
}

@Composable
fun PremiumSliderRow(
    label: String,
    prefKey: String,
    prefs: android.content.SharedPreferences,
    default: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    displayMultiplier: Float = 100f,
    unit: String = "%"
) {
    var value by remember { mutableStateOf(prefs.getFloat(prefKey, default)) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextWhite)
            val displayValue = if (unit == "x") {
                String.format(java.util.Locale.US, "%.1fx", value)
            } else if (unit == "°") {
                "${value.toInt()}°"
            } else {
                "${(value * displayMultiplier).toInt()}$unit"
            }
            Text(displayValue, fontSize = 14.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = { 
                value = it 
                prefs.edit().putFloat(prefKey, it).apply()
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentSecondary,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun ApplyButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "btnScale")

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TextWhite),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
    ) {
        Text("SET ENVIRONMENT", color = PremiumBlack, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp)
    }
}

// ─── SHADER CREATOR CUSTOM AGSL SANDBOX ────────────────────────────────────────

val AGSL_BOILERPLATE_WAVES = """
// 1. Declare Uniforms
uniform float2 iResolution;
uniform float iTime;
uniform float iScale;
uniform float3 colorA;
uniform float3 colorB;

// 2. Fragment Shading Function
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.xy;
    float2 p = uv * 2.0 - 1.0;
    p.x *= iResolution.x / iResolution.y;
    
    // Wave Math
    float wave = sin(p.x * (2.0 + iScale * 4.0) + iTime) * 0.3;
    float dist = abs(p.y - wave);
    float glow = 0.05 / (dist + 0.01);
    
    float3 col = mix(colorA, colorB, uv.y + wave);
    col += glow * colorA;
    
    return half4(col, 1.0);
}
""".trimIndent()

val AGSL_BOILERPLATE_PLASMA = """
// 1. Declare Uniforms
uniform float2 iResolution;
uniform float iTime;
uniform float3 colorA;
uniform float3 colorB;

// 2. Fragment Shading Function
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.xy;
    float2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;
    
    float r = length(p);
    float a = atan(p.y, p.x);
    
    float pulse = sin(r * 10.0 - iTime * 2.0) * 0.5 + 0.5;
    float swirl = sin(a * 3.0 + iTime) * 0.3;
    
    float3 col = mix(colorA, colorB, pulse + swirl);
    col += (0.02 / (r + 0.05)) * colorA;
    
    return half4(col, 1.0);
}
""".trimIndent()

val AGSL_BOILERPLATE_GRID = """
// 1. Declare Uniforms
uniform float2 iResolution;
uniform float iTime;
uniform float3 colorA;
uniform float3 colorB;

// 2. Fragment Shading Function
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.xy;
    float2 p = uv * 2.0 - 1.0;
    p.x *= iResolution.x / iResolution.y;
    
    // Retro grid projection
    float z = 1.0 / (p.y + 1.001);
    float x = p.x * z;
    float y = z + iTime * 0.5;
    
    float gridX = abs(fract(x * 10.0 - 0.5) - 0.5) / (10.0 * z);
    float gridY = abs(fract(y * 10.0 - 0.5) - 0.5) / (10.0 * z);
    float grid = 1.0 - min(gridX, gridY);
    grid = smoothstep(0.85, 1.0, grid) * step(p.y, 0.0);
    
    float3 bg = mix(colorA, colorB, uv.y);
    float3 col = bg + grid * colorA * 1.5;
    
    return half4(col, 1.0);
}
""".trimIndent()

@Composable
fun CreatorSegmentControl(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
    colA: Color,
    colB: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf("Style Editor", "Shader Creator")
        modes.forEachIndexed { index, title ->
            val isSelected = selectedMode == index
            val backgroundBrush = if (isSelected) {
                Brush.horizontalGradient(listOf(colA, colB))
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundBrush)
                    .then(
                        if (isSelected) Modifier
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .clickable { onModeSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SaveShaderDialog(
    currentCode: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Custom Shader", fontWeight = FontWeight.Bold, color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter a name for your custom AGSL shader to store it permanently.",
                    fontSize = 14.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shader Name", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(name.trim())
                    }
                }
            ) {
                Text("Save", color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PremiumBlack
    )
}

@Composable
fun ImportShaderDialog(
    onImport: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString() ?: ""
                // Prefill if it looks like AGSL code (e.g. contains main/void/half4/float/uniform)
                if (text.contains("main") || text.contains("uniform") || text.contains("half4")) {
                    code = text
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Custom Shader", fontWeight = FontWeight.Bold, color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Give it a name and paste the AGSL code below.",
                    fontSize = 14.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shader Name", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("AGSL Shader Code", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 10,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isEmpty()) {
                        android.widget.Toast.makeText(context, "Please enter a name!", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (code.trim().isEmpty()) {
                        android.widget.Toast.makeText(context, "Please enter shader code!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onImport(name.trim(), code.trim())
                    }
                }
            ) {
                Text("Import", color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PremiumBlack
    )
}

@Composable
fun ConfirmTemplateOverwriteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overwrite current code?", fontWeight = FontWeight.Bold, color = TextWhite) },
        text = {
            Text(
                "You have edited the current shader code. Loading this template will overwrite and delete your current edits.",
                fontSize = 14.sp,
                color = TextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Overwrite", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PremiumBlack
    )
}

@Composable
fun ConfirmDeleteShaderDialog(
    shaderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Shader?", fontWeight = FontWeight.Bold, color = TextWhite) },
        text = {
            Text(
                "Are you sure you want to permanently delete \"$shaderName\"?",
                fontSize = 14.sp,
                color = TextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PremiumBlack
    )
}

@Composable
fun ShaderCreatorCard(
    prefs: android.content.SharedPreferences,
    colA: Color,
    colB: Color,
    customShaders: List<CustomShader>
) {
    val context = LocalContext.current
    
    var lastValidCode by remember {
        mutableStateOf(
            prefs.getString("custom_agsl_shader_code", "").let {
                if (!it.isNullOrEmpty()) it else AGSL_BOILERPLATE_WAVES
            }
        )
    }
    
    var codeValue by remember { mutableStateOf(TextFieldValue(lastValidCode)) }
    var compileError by remember { mutableStateOf<String?>(null) }
    
    // Recompile check whenever the text changes
    val compiledCode = remember(codeValue.text) {
        val currentCode = codeValue.text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                // Test compile the shader
                RuntimeShader(currentCode)
                compileError = null
                // Save immediately to preferences if valid
                prefs.edit().putString("custom_agsl_shader_code", currentCode).apply()
                lastValidCode = currentCode
                currentCode
            } catch (e: IllegalArgumentException) {
                compileError = e.message
                lastValidCode // Fallback to last successful or standard template
            } catch (e: Exception) {
                compileError = e.message
                lastValidCode
            }
        } else {
            compileError = "Preview requires Android 13+"
            currentCode
        }
    }

    val activeUniforms = remember(compiledCode) {
        val uniforms = java.util.HashSet<String>()
        val cleanCode = compiledCode
            .replace("/\\*[\\s\\S]*?\\*/".toRegex(), "")
            .replace("//.*".toRegex(), "")
            
        val uniformRegex = "\\buniform\\s+\\w+\\s+(\\w+)\\b".toRegex()
        uniformRegex.findAll(cleanCode).forEach { match ->
            match.groups[1]?.value?.let { uniforms.add(it) }
        }
        uniforms
    }

    fun insertTextAtCursor(textToInsert: String) {
        val selection = codeValue.selection
        val originalText = codeValue.text
        val newText = StringBuilder(originalText)
            .replace(selection.min, selection.max, textToInsert)
            .toString()
        val newSelectionIndex = selection.min + textToInsert.length
        codeValue = TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newSelectionIndex)
        )
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showImportDialogLocal by remember { mutableStateOf(false) }
    var showOverwriteWarning by remember { mutableStateOf(false) }
    var pendingTemplateCode by remember { mutableStateOf("") }
    var showDeleteWarning by remember { mutableStateOf(false) }
    var shaderToDelete by remember { mutableStateOf<CustomShader?>(null) }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Live Preview Canvas Area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val infiniteTransition = rememberInfiniteTransition(label = "ShaderPreview")
                val previewTime by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "PreviewTime"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint()
                            try {
                                val s = RuntimeShader(compiledCode)
                                
                                fun setPreviewUniform(name: String, vararg values: Float) {
                                    if (activeUniforms.contains(name)) {
                                        try {
                                            when (values.size) {
                                                1 -> s.setFloatUniform(name, values[0])
                                                2 -> s.setFloatUniform(name, values[0], values[1])
                                                3 -> s.setFloatUniform(name, values[0], values[1], values[2])
                                                4 -> s.setFloatUniform(name, values[0], values[1], values[2], values[3])
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }

                                setPreviewUniform("iResolution", size.width, size.height)
                                setPreviewUniform("iTime", previewTime)
                                setPreviewUniform("iOffset", 0f, 0f)
                                setPreviewUniform("iAudio", 0.3f + 0.15f * kotlin.math.sin(previewTime * 2f).toFloat())
                                setPreviewUniform("iSpeed", 0.5f)
                                setPreviewUniform("iViscosity", 0.6f)
                                setPreviewUniform("iComplexity", 0.5f)
                                setPreviewUniform("iScale", 0.5f)
                                setPreviewUniform("iParallax", 0f)
                                setPreviewUniform("iAudioReact", 0.5f)
                                setPreviewUniform("iBloom", 0.4f)
                                setPreviewUniform("iContrast", 0.5f)
                                
                                setPreviewUniform("colorA", colA.red, colA.green, colA.blue)
                                setPreviewUniform("colorB", colB.red, colB.green, colB.blue)
                                
                                setPreviewUniform("iBrightness", 1.0f)
                                setPreviewUniform("iSaturation", 1.0f)
                                setPreviewUniform("iHueShift", 0.0f)
                                setPreviewUniform("iVignette", 1.0f)
                                setPreviewUniform("iNoiseIntensity", 0.3f)
                                
                                paint.shader = s
                                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                            } catch (e: Exception) {
                                // Graceful render on compile error
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AGSL shader preview requires Android 13+", color = TextMuted, fontSize = 14.sp)
                }
            }

            // Compiler Status Indicators
            if (compileError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FF3B30))
                        .border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shader Compiler Output", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = compileError ?: "",
                            color = Color(0xFFFFD60A),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3334C759))
                        .border(1.dp, Color(0xFF34C759).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircleOutline,
                        contentDescription = null,
                        tint = Color(0xFF30D158),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compiled Successfully", color = Color(0xFF30D158), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Quick Starter Boilerplates Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Boilerplate Templates", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val templates = listOf(
                        Triple("Waves", AGSL_BOILERPLATE_WAVES, Icons.Rounded.Waves),
                        Triple("Plasma", AGSL_BOILERPLATE_PLASMA, Icons.Rounded.BubbleChart),
                        Triple("Grid", AGSL_BOILERPLATE_GRID, Icons.Rounded.GridOn)
                    )
                    templates.forEach { (name, templateCode, icon) ->
                        OutlinedButton(
                            onClick = {
                                val standardTemplates = listOf(AGSL_BOILERPLATE_WAVES, AGSL_BOILERPLATE_PLASMA, AGSL_BOILERPLATE_GRID)
                                val hasCustomEdits = codeValue.text.isNotEmpty() &&
                                        !standardTemplates.contains(codeValue.text) &&
                                        customShaders.none { it.code == codeValue.text }
                                if (hasCustomEdits) {
                                    pendingTemplateCode = templateCode
                                    showOverwriteWarning = true
                                } else {
                                    codeValue = TextFieldValue(templateCode)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = colA)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(name, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Saved Custom Shaders Row Selector
            if (customShaders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("My Saved Shaders", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(customShaders) { shader ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassDark)
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        val standardTemplates = listOf(AGSL_BOILERPLATE_WAVES, AGSL_BOILERPLATE_PLASMA, AGSL_BOILERPLATE_GRID)
                                        val hasCustomEdits = codeValue.text.isNotEmpty() &&
                                                !standardTemplates.contains(codeValue.text) &&
                                                customShaders.none { it.code == codeValue.text }
                                        if (hasCustomEdits) {
                                            pendingTemplateCode = shader.code
                                            showOverwriteWarning = true
                                        } else {
                                            codeValue = TextFieldValue(shader.code)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = colA,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = shader.name,
                                        fontSize = 12.sp,
                                        color = TextWhite,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                shaderToDelete = shader
                                                showDeleteWarning = true
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Monospaced Code Editor Box
            val scrollState = rememberScrollState()
            OutlinedTextField(
                value = codeValue,
                onValueChange = { codeValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(scrollState),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextWhite
                ),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                    imeAction = androidx.compose.ui.text.input.ImeAction.None,
                    autoCorrect = false
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colA,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = colA
                ),
                placeholder = { 
                    Text("Write your AGSL fragment shader...", color = TextMuted, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp) 
                },
                trailingIcon = {
                    if (codeValue.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                codeValue = TextFieldValue("")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear Editor",
                                tint = TextMuted
                            )
                        }
                    }
                }
            )

            // Dynamic Uniform reference (clickable inserts)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Uniform Reference (Tap to Insert)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val uniforms = listOf(
                        "iResolution" to "uniform float2 iResolution;\n",
                        "iTime" to "uniform float iTime;\n",
                        "iScale" to "uniform float iScale;\n",
                        "colorA" to "uniform float3 colorA;\n",
                        "colorB" to "uniform float3 colorB;\n",
                        "iAudio" to "uniform float iAudio;\n",
                        "iSpeed" to "uniform float iSpeed;\n",
                        "iViscosity" to "uniform float iViscosity;\n"
                    )
                    items(uniforms) { (name, fullDecl) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassDark)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val toInsert = if (!codeValue.text.contains(name)) fullDecl else name
                                    insertTextAtCursor(toInsert)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(name, color = colA, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }

            // GPU Math Snippet Injectors
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Math Snippets (Tap to Insert)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val snippets = listOf(
                        "Value Noise" to """
                            float hash(float2 p) {
                                return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
                            }
                            float noise(float2 p) {
                                float2 i = floor(p);
                                float2 f = fract(p);
                                float2 u = f*f*(3.0-2.0*f);
                                return mix(mix(hash(i + float2(0.0,0.0)), hash(i + float2(1.0,0.0)), u.x),
                                           mix(hash(i + float2(0.0,1.0)), hash(i + float2(1.0,1.0)), u.x), u.y);
                            }
                        """.trimIndent(),
                        "Rainbow Shift" to """
                            float3 rainbow(float t) {
                                return 0.5 + 0.5 * cos(6.28318 * (t + float3(0.0, 0.33, 0.67)));
                            }
                        """.trimIndent(),
                        "Vignette Edge" to """
                            float vignette(float2 uv) {
                                float2 d = abs(uv - 0.5) * 1.5;
                                return clamp(1.0 - dot(d, d), 0.0, 1.0);
                            }
                        """.trimIndent()
                    )
                    
                    items(snippets) { (name, code) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassDark)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable {
                                    insertTextAtCursor(code + "\n")
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(name, color = colB, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Library Actions: Save & Import Shader
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save Shader button
                OutlinedButton(
                    onClick = {
                        showSaveDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Shader", fontSize = 13.sp)
                }

                // Import Shader button
                OutlinedButton(
                    onClick = {
                        showImportDialogLocal = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Shader", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons: Apply Shader & Export Spec To AI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Apply Custom Shader button (Gradient matches background)
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(colA, colB)))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (compileError != null) {
                                android.widget.Toast.makeText(context, "Please fix compilation errors first!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                prefs.edit()
                                    .putInt("visual_style", 5) // Set style to custom shader
                                    .putString("custom_agsl_shader_code", codeValue.text)
                                    .apply()
                                android.widget.Toast.makeText(context, "Custom AGSL Shader Applied!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Shader", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // AI Spec Exporter Share Sheet Button
                OutlinedButton(
                    onClick = {
                        shareAiSpec(context, codeValue.text)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Export", fontSize = 13.sp)
                }
            }
        }
    }

    // Dialog overlays
    if (showOverwriteWarning) {
        ConfirmTemplateOverwriteDialog(
            onConfirm = {
                codeValue = TextFieldValue(pendingTemplateCode)
                showOverwriteWarning = false
                pendingTemplateCode = ""
            },
            onDismiss = {
                showOverwriteWarning = false
                pendingTemplateCode = ""
            }
        )
    }

    if (showDeleteWarning && shaderToDelete != null) {
        ConfirmDeleteShaderDialog(
            shaderName = shaderToDelete!!.name,
            onConfirm = {
                val updated = customShaders.filter { it.id != shaderToDelete!!.id }
                saveCustomShaders(prefs, updated)
                if (prefs.getString("active_custom_shader_id", "") == shaderToDelete!!.id) {
                    prefs.edit().putString("active_custom_shader_id", "").apply()
                }
                showDeleteWarning = false
                shaderToDelete = null
                android.widget.Toast.makeText(context, "Shader deleted.", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showDeleteWarning = false
                shaderToDelete = null
            }
        )
    }

    if (showSaveDialog) {
        SaveShaderDialog(
            currentCode = codeValue.text,
            onSave = { name ->
                val newShader = CustomShader(name = name, code = codeValue.text)
                val updated = customShaders + newShader
                saveCustomShaders(prefs, updated)
                showSaveDialog = false
                android.widget.Toast.makeText(context, "Shader saved!", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showImportDialogLocal) {
        ImportShaderDialog(
            onImport = { name, code ->
                val newShader = CustomShader(name = name, code = code)
                val updated = customShaders + newShader
                saveCustomShaders(prefs, updated)
                codeValue = TextFieldValue(code)
                showImportDialogLocal = false
                android.widget.Toast.makeText(context, "Shader imported!", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showImportDialogLocal = false }
        )
    }
}

fun shareAiSpec(context: Context, currentCode: String) {
    val specText = """
        You are an expert Graphics Shader Developer. I am using the VibeFlow 2 Android wallpaper app.
        This app runs custom AGSL (Android Graphics Shading Language) fragment shaders.
        
        Here is the exact API documentation for the environment:
        1. Entry point MUST be: `half4 main(float2 fragCoord)`
        2. Supported Uniforms (Do not redeclare unless you use them, but if used, declare exactly like this):
           - `uniform float2 iResolution;` // Screen resolution
           - `uniform float iTime;`        // Time elapsed in seconds
           - `uniform float2 iOffset;`     // Gyro parallax offset
           - `uniform float iAudio;`       // Live audio amplitude
           - `uniform float iSpeed;`       // User-controlled speed
           - `uniform float iViscosity;`   // User-controlled fluid viscosity
           - `uniform float iScale;`       // User-controlled scale
           - `uniform float3 colorA;`      // User-selected Theme Color A
           - `uniform float3 colorB;`      // User-selected Theme Color B
           - `uniform float iBrightness;`  // User-selected brightness
           - `uniform float iSaturation;`  // User-selected saturation
           - `uniform float iHueShift;`    // User-selected hue shift
           - `uniform float iVignette;`    // User-selected vignette intensity
           - `uniform float iNoiseIntensity;` // User-selected noise intensity

        3. AGSL rules:
           - Use standard GLSL syntax, but note that AGSL does not support texture lookups or custom vertex shading.
           - Use float2, float3, float4, half4 instead of vec2, vec3, vec4.
           - Use `mix` instead of `lerp`.
           - The main function returns a `half4` representing the pixel color.

        My current shader code is:
        ```glsl
        $currentCode
        ```

        Please help me write, optimize, or debug this shader.
        Please output a complete, copy-pasteable AGSL shader starting with the necessary uniforms. Make it look incredible!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "VibeFlow 2 AGSL Shader AI Prompt")
        putExtra(Intent.EXTRA_TEXT, specText)
    }
    context.startActivity(Intent.createChooser(intent, "Share AI Prompt"))
}
