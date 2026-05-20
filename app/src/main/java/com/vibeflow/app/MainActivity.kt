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

// ─── MAIN UI ─────────────────────────────────────────────────────────────────
@Composable
fun MainScreen(onSetWallpaper: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE) }
    
    var bgColA by remember { mutableStateOf(Color(0xFF141E30)) }
    var bgColB by remember { mutableStateOf(Color(0xFF243B55)) }

    val updateBgColors = {
        val spotifyOn = prefs.getBoolean("smart_sync_spotify", true)
        val spotifyActive = prefs.getBoolean("is_spotify_active", false)
        val todOn = prefs.getBoolean("time_of_day", false)

        if (spotifyOn && spotifyActive) {
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

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateBgColors() }
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
        
        // Track currently selected theme and sync state for UI
        var activeThemeName by remember { mutableStateOf(prefs.getString("active_theme_name", "Deep Space") ?: "Deep Space") }
        var isSpotifySyncEnabled by remember { mutableStateOf(prefs.getBoolean("smart_sync_spotify", true)) }
        var timeOfDayEnabled by remember { mutableStateOf(prefs.getBoolean("time_of_day", false)) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header
            HeaderSection()

            // Main Content (Scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                
                // 1. Featured Themes Carousel
                Text("Curated Collections", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                ThemesCarousel(activeThemeName) { theme ->
                    activeThemeName = theme.name
                    isSpotifySyncEnabled = false
                    timeOfDayEnabled = false
                    prefs.edit()
                        .putString("active_theme_name", theme.name)
                        .putInt("theme_color_a", theme.colorA.toArgb())
                        .putInt("theme_color_b", theme.colorB.toArgb())
                        .putBoolean("smart_sync_spotify", false)
                        .putBoolean("time_of_day", false)
                        .apply()
                }

                // 2. Visual Style Selection (New!)
                Text("Visual Style", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 20.dp))
                StyleSelectionRow(prefs)

                // 3. Smart Sync Features
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Smart Engine", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    SmartSyncCards(
                        context = context,
                        prefs = prefs,
                        isSpotifySyncEnabled = isSpotifySyncEnabled,
                        onSpotifySyncChanged = { enabled ->
                            isSpotifySyncEnabled = enabled
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
                                isSpotifySyncEnabled = false
                                prefs.edit().putString("active_theme_name", "").putBoolean("smart_sync_spotify", false).apply()
                            }
                        }
                    )

                    // 4. Pro Customization
                    Text("Advanced Tuning", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    AdvancedTuningSection(prefs)

                    Spacer(modifier = Modifier.height(100.dp)) // Space for FAB
                }
            }
        }

        // Apply Button (Floating at bottom, Apple style)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, PremiumBlack.copy(alpha = 0.9f))))
                .padding(24.dp)
        ) {
            ApplyButton(onSetWallpaper)
        }
    }
}

@Composable
fun HeaderSection() {
    var showSettings by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "VibeFlow 2",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                color = TextWhite
            )
            Text(
                text = "Dynamic Environments",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AccentPrimary
            )
        }
        
        // Profile / Settings Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassDark)
                .clickable { showSettings = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextWhite)
        }
    }
    
    if (showSettings) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE) }
        var isBatterySaver by remember { mutableStateOf(prefs.getBoolean("battery_saver", false)) }
        
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("App Settings", fontWeight = FontWeight.Bold, color = TextWhite) },
            text = {
                Column {
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
                    Spacer(modifier = Modifier.height(16.dp))
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
                                .apply()
                            showSettings = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Advanced Tuning Sliders", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    prefs.edit().putBoolean("battery_saver", isBatterySaver).apply()
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
        ThemeItem("Solar Flare", "Plasma", Color(0xFFFF512F), Color(0xFFF09819))
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
    val styles = listOf("Liquid Chrome", "Cosmic Plasma", "Frosted Aurora", "Pure Chrome")
    
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
    isSpotifySyncEnabled: Boolean, 
    onSpotifySyncChanged: (Boolean) -> Unit,
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
                title = "Spotify Color Sync",
                subtitle = "Extracts colors from current album art",
                checked = isSpotifySyncEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                        if (!hasNotificationAccess) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    onSpotifySyncChanged(checked)
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
fun AdvancedTuningSection(prefs: android.content.SharedPreferences) {
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
        }
    }
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
fun PremiumSliderRow(label: String, prefKey: String, prefs: android.content.SharedPreferences, default: Float) {
    var value by remember { mutableStateOf(prefs.getFloat(prefKey, default)) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextWhite)
            Text("${(value * 100).toInt()}%", fontSize = 14.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = { 
                value = it 
                prefs.edit().putFloat(prefKey, it).apply()
            },
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
