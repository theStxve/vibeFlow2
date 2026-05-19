package com.vibeflow.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max

class VibeFlowWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return VibeFlowEngine()
    }

    private inner class VibeFlowEngine : Engine(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        
        private lateinit var prefs: SharedPreferences
        private var currentColorA = floatArrayOf(0.078f, 0.117f, 0.188f)
        private var currentColorB = floatArrayOf(0.141f, 0.231f, 0.333f)

        // Advanced Tuning
        private var animationSpeed = 0.5f
        private var fluidViscosity = 0.6f
        private var particleCount = 0.8f
        private var waveScale = 0.5f
        private var parallaxIntensity = 0.5f
        private var contrastLevel = 0.5f
        private var audioReactivity = 0.5f
        private var bloomIntensity = 0.4f

        private var currentStyle = -1

        // Sensor
        private lateinit var sensorManager: SensorManager
        private var gyroSensor: Sensor? = null
        private var gyroX = 0f
        private var gyroY = 0f

        // Audio
        private var visualizer: Visualizer? = null
        private var audioAmplitude = 0f

        // Smart Engine Toggles
        private var fftEnabled = true
        private var gyroEnabled = true

        // Rendering
        private var shader: RuntimeShader? = null
        private val paint = Paint()
        private var startTime = System.currentTimeMillis()


        private val AGSL_LIQUID_SHADER = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform float2 iOffset;
            uniform float iAudio;
            uniform float iSpeed;
            uniform float iViscosity;
            uniform float iComplexity;
            uniform float iScale;
            uniform float iParallax;
            uniform float iAudioReact;
            uniform float iBloom;
            uniform float iContrast;
            uniform float3 colorA;
            uniform float3 colorB;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution.xy;
                uv = uv * 2.0 - 1.0;
                uv.x *= iResolution.x / iResolution.y;
                
                float2 p = uv; // Keep original for vignette
                
                uv *= (0.5 + iScale * 1.5);
                uv += iOffset * (iParallax * 0.1);

                for(float i = 1.0; i < 5.0; i++) {
                    float t = iTime * iSpeed * iViscosity + (iAudio * iAudioReact * 1.5);
                    uv.x += 0.5 / i * cos(i * (1.0 + iComplexity*1.5) * uv.y + t);
                    uv.y += 0.5 / i * cos(i * (1.0 + iComplexity*1.0) * uv.x + t);
                }
                
                // Create soft depth/shadows instead of harsh RGB math
                float depth = sin(uv.x * 2.0) * cos(uv.y * 2.0) * 0.5 + 0.5;
                depth += (iAudio * iAudioReact * 0.6); // Softer audio react
                
                // Smooth blending for eye comfort
                depth = smoothstep(0.0, 1.2, depth);
                
                // Darken Color A for natural shadows, brighten Color B for highlights
                float3 col = mix(colorA * 0.4, colorB * 1.1, depth);
                
                // Soft Bloom
                col += float3(depth * iAudio * iBloom * 1.5);
                
                // Softer contrast application
                col = mix(col, (col - 0.5) * (1.0 + iContrast * 0.8) + 0.5, iContrast);
                
                // Gentle vignette to focus the eyes
                float vignette = 1.0 - length(p) * 0.3;
                col *= vignette;
                
                return half4(col.r, col.g, col.b, 1.0);
            }
        """.trimIndent()

        private val AGSL_PLASMA_SHADER = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform float2 iOffset;
            uniform float iAudio;
            uniform float iSpeed;
            uniform float iViscosity;
            uniform float iComplexity;
            uniform float iScale;
            uniform float iParallax;
            uniform float iAudioReact;
            uniform float iBloom;
            uniform float iContrast;
            uniform float3 colorA;
            uniform float3 colorB;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution.xy;
                uv = uv * 2.0 - 1.0;
                uv.x *= iResolution.x / iResolution.y;
                
                float2 p = uv; // For vignette
                
                uv *= (0.5 + iScale * 1.5);
                uv += iOffset * (iParallax * 0.15);

                float radius = length(uv);
                float angle = atan(uv.y, uv.x);
                
                for(float i = 1.0; i < 4.0; i++) {
                    float t = iTime * iSpeed * iViscosity + (iAudio * iAudioReact * 1.5);
                    radius += sin(angle * (2.0 + iComplexity*3.0) + t) * 0.15;
                    angle += cos(radius * (1.5 + iComplexity*2.0) - t) * 0.25;
                }
                
                // Smooth waves instead of sharp rings
                float depth = sin(radius * 5.0 - iTime * iSpeed * 2.0) * 0.5 + 0.5;
                depth += (iAudio * iAudioReact * 0.6);
                depth = smoothstep(-0.2, 1.2, depth);
                
                // Deepen the background, highlight the rings
                float3 col = mix(colorA * 0.3, colorB * 1.1, depth);
                
                col += float3(depth * iAudio * iBloom * 1.5);
                col = mix(col, (col - 0.5) * (1.0 + iContrast * 0.8) + 0.5, iContrast);
                
                float vignette = 1.0 - length(p) * 0.5;
                col *= vignette;
                
                return half4(col.r, col.g, col.b, 1.0);
            }
        """.trimIndent()

        private val AGSL_GRID_SHADER = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform float2 iOffset;
            uniform float iAudio;
            uniform float iSpeed;
            uniform float iViscosity;
            uniform float iComplexity;
            uniform float iScale;
            uniform float iParallax;
            uniform float iAudioReact;
            uniform float iBloom;
            uniform float iContrast;
            uniform float3 colorA;
            uniform float3 colorB;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution.xy;
                float2 p = uv * 2.0 - 1.0;
                p.x *= iResolution.x / iResolution.y;
                
                p *= (0.5 + iScale * 0.8);
                p += iOffset * (iParallax * 0.1);
                
                float time = iTime * iSpeed * iViscosity;
                float audioLevel = iAudio * iAudioReact;
                
                // iOS 18 Aurora / Frosted Glass Metaballs
                float blob1 = length(p - float2(sin(time*0.7)*0.8, cos(time*0.5)*0.8));
                float blob2 = length(p - float2(cos(time*0.4)*-0.8, sin(time*0.6)*-0.6));
                float blob3 = length(p - float2(sin(time*0.3)*0.5, cos(time*0.8)*-0.5));
                
                float m1 = smoothstep(1.5 - iComplexity*0.5, 0.0, blob1);
                float m2 = smoothstep(1.5 - iComplexity*0.5, 0.0, blob2);
                float m3 = smoothstep(1.5 - iComplexity*0.5, 0.0, blob3 + audioLevel);
                
                float3 col = mix(colorA, colorB, m1);
                col = mix(col, float3(colorA.g, colorB.b, colorA.r), m2);
                col = mix(col, colorB * 1.5, m3);
                
                // Premium frosted glass grain
                float grain = fract(sin(dot(uv, float2(12.9898, 78.233))) * 43758.5453) * 0.03;
                col += grain;
                
                col += (m1 * m2 * m3) * iBloom * 5.0;
                
                col = (col - 0.5) * (0.5 + iContrast * 1.5) + 0.5;
                return half4(col.r, col.g, col.b, 1.0);
            }
        """.trimIndent()

        private val AGSL_PURE_CHROME_SHADER = """
            uniform float2 iResolution;
            uniform float iTime;
            uniform float2 iOffset;
            uniform float iAudio;
            uniform float iSpeed;
            uniform float iViscosity;
            uniform float iComplexity;
            uniform float iScale;
            uniform float iParallax;
            uniform float iAudioReact;
            uniform float iBloom;
            uniform float iContrast;
            uniform float3 colorA;
            uniform float3 colorB;

            // Stable Hash
            float hash(float2 p) {
                float2 p2 = fract(p * float2(0.1031, 0.1136));
                p2 += dot(p2, p2.yx + 19.19);
                return fract(p2.x * p2.y);
            }

            // High-quality 2D Value Noise
            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                
                // Smooth interpolation
                float2 u = f * f * (3.0 - 2.0 * f);
                
                float a = hash(i);
                float b = hash(i + float2(1.0, 0.0));
                float c = hash(i + float2(0.0, 1.0));
                float d = hash(i + float2(1.0, 1.0));
                
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }

            // Fractal Brownian Motion (fBM)
            float fbm(float2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += a * noise(p);
                    // Rotate and scale
                    float nx = p.x * 0.8 - p.y * 0.6;
                    float ny = p.x * 0.6 + p.y * 0.8;
                    p = float2(nx, ny) * 2.0;
                    a *= 0.5;
                }
                return v;
            }

            float map(float2 p, float time) {
                // Domain Warping: Displacing noise with noise for deep liquid folds
                float2 q = float2(
                    fbm(p + time * 0.15), 
                    fbm(p + float2(5.2, 1.3) - time * 0.12)
                );
                
                float2 r = float2(
                    fbm(p + 3.0 * q + time * 0.2), 
                    fbm(p + 3.0 * q + float2(8.3, 2.8) + time * 0.18)
                );
                
                // Scale complexity parameter
                float comp = 2.0 + iComplexity * 3.0;
                return fbm(p + comp * r);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / iResolution.xy;
                float2 p = uv * 2.0 - 1.0;
                p.x *= iResolution.x / iResolution.y;
                
                // Zoom out slightly to see thick liquid strands
                p *= (0.7 + iScale * 0.8);
                p += iOffset * (iParallax * 0.15);
                
                float time = iTime * iSpeed * iViscosity * 0.8;
                float audioLevel = iAudio * iAudioReact * 0.8;
                
                float eps = 0.015;
                float h = map(p, time + audioLevel);
                float hx = map(p + float2(eps, 0.0), time + audioLevel);
                float hy = map(p + float2(0.0, eps), time + audioLevel);
                
                // Steep normal (very small Z) for razor-sharp liquid metal reflections
                float3 normal = normalize(float3(hx - h, hy - h, eps * 0.25));
                
                float3 viewDir = normalize(float3(0.0, 0.0, 1.0));
                float3 ref = reflect(-viewDir, normal);
                
                // Fake Studio Box Reflections
                float env = 0.0;
                env += pow(sin(ref.x * 6.0 + time * 0.3) * 0.5 + 0.5, 6.0) * 0.9;
                env += pow(cos(ref.y * 5.0 - time * 0.2) * 0.5 + 0.5, 6.0) * 0.7;
                env += pow(sin(ref.z * 8.0) * 0.5 + 0.5, 4.0) * 0.5;
                env = clamp(env, 0.0, 1.0);
                
                // Monochrome Chrome Colors
                float3 colHighlight = float3(0.92, 0.95, 1.0); // Icy bright peak
                float3 colShadow = float3(0.04, 0.05, 0.06);   // Deep black/gunmetal
                float3 chromeColor = mix(colShadow, colHighlight, env);
                
                // Intense Specular Lighting
                float3 lightDir1 = normalize(float3(0.5, 0.8, 0.4));
                float3 lightDir2 = normalize(float3(-0.7, -0.3, 0.6));
                
                float3 halfVector1 = normalize(lightDir1 + viewDir);
                float3 halfVector2 = normalize(lightDir2 + viewDir);
                
                float spec1 = pow(max(dot(normal, halfVector1), 0.0), 90.0);
                float spec2 = pow(max(dot(normal, halfVector2), 0.0), 45.0);
                
                chromeColor += float3(1.0) * spec1 * 1.8;
                chromeColor += float3(0.8, 0.9, 1.0) * spec2 * 0.9;
                
                // Fresnel Edges
                float fresnel = pow(1.0 - max(dot(normal, viewDir), 0.0), 5.0);
                chromeColor += float3(0.9, 0.95, 1.0) * fresnel * 0.7;
                
                // --- Deep Valley Masking (The carbon/brushed metal in the cracks) ---
                // h is usually between 0.2 and 0.8. We isolate the low areas.
                float valley = smoothstep(0.25, 0.65, h);
                
                // Brushed Metal Texture for Valleys
                float2 grainUV = fragCoord * float2(1.8, 0.1); 
                float grain = hash(grainUV) * 0.12;
                float3 valleyColor = float3(0.02, 0.02, 0.02) + grain; // Pitch black with grain
                
                // Combine: Chrome on hills, brushed texture in valleys
                float3 finalColor = mix(valleyColor, chromeColor, valley);
                
                // Audio Bloom
                finalColor += float3(iAudio * iBloom * 3.0 * spec1);
                
                // High Contrast
                finalColor = (finalColor - 0.5) * (1.2 + iContrast * 1.4) + 0.5;
                finalColor = clamp(finalColor, 0.0, 1.0);
                
                // Dummy to keep SkSL happy
                finalColor += (colorA + colorB) * 0.000001;
                
                return half4(finalColor.r, finalColor.g, finalColor.b, 1.0);
            }
        """.trimIndent()

        private val drawRunnable = object : Runnable {
            override fun run() {
                draw()
                if (visible) {
                    val isBatterySaver = prefs.getBoolean("battery_saver", false)
                    val fpsDelay = if (isBatterySaver) 1000L / 30L else 1000L / 60L
                    handler.postDelayed(this, fpsDelay)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            
            // Setup SharedPreferences
            prefs = getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            updatePrefs()

            // Sensor setup
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

            setupAudioVisualizer()
            updatePrefs()
        }

        private fun setupAudioVisualizer() {
            try {
                // Initialize visualizer on output mix (session 0)
                visualizer = Visualizer(0).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform == null) return
                            // Calculate amplitude RMS
                            var sum = 0f
                            for (byte in waveform) {
                                val amplitude = byte.toFloat()
                                sum += amplitude * amplitude
                            }
                            val rms = Math.sqrt((sum / waveform.size).toDouble()).toFloat()
                            
                            // Smooth the amplitude transition
                            audioAmplitude = audioAmplitude * 0.8f + (rms / 128f) * 0.2f
                        }

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            // FFT can be used for more advanced spectral analysis
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                }
            } catch (e: Exception) {
                e.printStackTrace() // Handle permission denial or disabled Visualizer gracefully
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                gyroSensor?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                try {
                    visualizer?.enabled = true
                } catch (e: Exception) {}
                handler.post(drawRunnable)
            } else {
                sensorManager.unregisterListener(this)
                try {
                    visualizer?.enabled = false
                } catch (e: Exception) {}
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            updatePrefs()
        }

        private fun updatePrefs() {
            val isSpotifySyncEnabled = prefs.getBoolean("smart_sync_spotify", true)
            val isSpotifyActive = prefs.getBoolean("is_spotify_active", false)
            val isTimeOfDayEnabled = prefs.getBoolean("time_of_day", false)

            var colorAInt: Int
            var colorBInt: Int

            if (isSpotifySyncEnabled && isSpotifyActive) {
                colorAInt = prefs.getInt("spotify_color_a", 0xFF141E30.toInt())
                colorBInt = prefs.getInt("spotify_color_b", 0xFF243B55.toInt())
            } else {
                if (isTimeOfDayEnabled) {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    if (hour in 6..10) { // Sunrise
                        colorAInt = 0xFFFFA07A.toInt() 
                        colorBInt = 0xFFFF4500.toInt() 
                    } else if (hour in 11..16) { // Day
                        colorAInt = 0xFF00BFFF.toInt() 
                        colorBInt = 0xFF87CEFA.toInt() 
                    } else if (hour in 17..20) { // Sunset
                        colorAInt = 0xFFFF4500.toInt() 
                        colorBInt = 0xFF4B0082.toInt() 
                    } else { // Night
                        colorAInt = 0xFF141E30.toInt()
                        colorBInt = 0xFF243B55.toInt()
                    }
                } else {
                    colorAInt = prefs.getInt("theme_color_a", 0xFF141E30.toInt())
                    colorBInt = prefs.getInt("theme_color_b", 0xFF243B55.toInt())
                }
            }

            currentColorA = floatArrayOf(
                Color.red(colorAInt) / 255f,
                Color.green(colorAInt) / 255f,
                Color.blue(colorAInt) / 255f
            )
            currentColorB = floatArrayOf(
                Color.red(colorBInt) / 255f,
                Color.green(colorBInt) / 255f,
                Color.blue(colorBInt) / 255f
            )

            // Advanced Tuning
            animationSpeed = prefs.getFloat("animation_speed", 0.5f)
            fluidViscosity = prefs.getFloat("fluid_viscosity", 0.6f)
            particleCount = prefs.getFloat("particle_count", 0.8f)
            waveScale = prefs.getFloat("wave_scale", 0.5f)
            parallaxIntensity = prefs.getFloat("parallax_intensity", 0.5f)
            contrastLevel = prefs.getFloat("contrast_level", 0.5f)
            audioReactivity = prefs.getFloat("audio_reactivity", 0.5f)
            bloomIntensity = prefs.getFloat("bloom_intensity", 0.4f)
            
            fftEnabled = prefs.getBoolean("fft_enabled", true)
            gyroEnabled = prefs.getBoolean("gyro_enabled", true)
            
            try {
                visualizer?.enabled = fftEnabled && visible
            } catch (e: Exception) {}

            // Visual Style Compilation
            val newStyle = prefs.getInt("visual_style", 0)
            if (newStyle != currentStyle) {
                currentStyle = newStyle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val shaderString = when (currentStyle) {
                        1 -> AGSL_PLASMA_SHADER
                        2 -> AGSL_GRID_SHADER
                        3 -> AGSL_PURE_CHROME_SHADER
                        else -> AGSL_LIQUID_SHADER
                    }
                    shader = RuntimeShader(shaderString)
                    paint.shader = shader
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            sensorManager.unregisterListener(this)
            visualizer?.release()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacks(drawRunnable)
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                        val time = (System.currentTimeMillis() - startTime) / 1000f
                        
                        shader?.setFloatUniform("iResolution", canvas.width.toFloat(), canvas.height.toFloat())
                        shader?.setFloatUniform("iTime", time)
                        val currentGyroX = if (gyroEnabled) gyroX else 0f
                        val currentGyroY = if (gyroEnabled) gyroY else 0f
                        shader?.setFloatUniform("iOffset", currentGyroX, currentGyroY)
                        
                        val currentAudio = if (fftEnabled) audioAmplitude else 0f
                        shader?.setFloatUniform("iAudio", currentAudio)

                        shader?.setFloatUniform("iSpeed", animationSpeed)
                        shader?.setFloatUniform("iViscosity", fluidViscosity)
                        shader?.setFloatUniform("iComplexity", particleCount)
                        shader?.setFloatUniform("iScale", waveScale)
                        shader?.setFloatUniform("iParallax", parallaxIntensity)
                        shader?.setFloatUniform("iContrast", contrastLevel)
                        shader?.setFloatUniform("iAudioReact", audioReactivity)
                        shader?.setFloatUniform("iBloom", bloomIntensity)
                        
                        // Dynamic Colors from Prefs
                        shader?.setFloatUniform("colorA", currentColorA[0], currentColorA[1], currentColorA[2])
                        shader?.setFloatUniform("colorB", currentColorB[0], currentColorB[1], currentColorB[2])

                        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                    } else {
                        // Fallback for older devices
                        canvas.drawColor(Color.parseColor("#141E30"))
                        val p = Paint().apply { color = Color.parseColor("#243B55"); strokeWidth = 20f + (audioAmplitude*100f) }
                        canvas.drawLine(0f, canvas.height/2f, canvas.width.toFloat(), canvas.height/2f + (gyroY*100f), p)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        // ─── SENSOR EVENTS (Gyroscope Parallax) ──────────────────────────────
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                // Integrate gyro data for smooth offset
                gyroX += event.values[1] * 0.5f // Swap X/Y for screen orientation mapping
                gyroY += event.values[0] * 0.5f
                
                // Clamp and decay to center slowly
                gyroX = max(-10f, Math.min(10f, gyroX))
                gyroY = max(-10f, Math.min(10f, gyroY))
                gyroX *= 0.95f
                gyroY *= 0.95f
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
