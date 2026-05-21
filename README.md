# VibeFlow 2 🌊✨

A premium, highly-optimized Live Wallpaper Engine for Android. VibeFlow 2 combines physics-based fluid dynamics, real-time audio reactivity, and context-aware styling with a built-in AGSL Shader Creator to turn your home screen into an interactive digital canvas.

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_app_logo.png" width="160" height="160" alt="VibeFlow 2 Logo" style="border-radius: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.3);">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_13%2B_--_API_33-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Version">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License">
</p>

---

## 🎨 Core Engine Features

*   **⚡ Native AGSL Shading:** Powered by Android Graphics Shading Language for near-zero CPU overhead and buttery-smooth rendering.
*   **🎭 Curated Premium Styles:**
    *   *Pure Chrome:* Raytraced liquid metallic folds with deep brushed reflections.
    *   *Iridescent Pearl:* Beautiful mother-of-pearl shifting pastel fluid style.
    *   *Cosmic Plasma:* Swirling high-density procedural space nebula.
    *   *Frosted Aurora:* Shimmering bands of northern lights filtered through textured frost.
*   **🎵 Real-time Audio Visualizer:** High-performance FFT analysis translates microphone input or system audio into fluid wave displacement.
*   **📱 Gyroscope Parallax:** Responsive 3D depth shifting that reacts instantly to device tilt.
*   **☀️ Context-Aware Environments:** Gradual lighting transitions synced with your local time of day (Sunrise, Noon, Sunset, Night).
*   **🎛️ Pro Tuning Dashboard:** Precise sliders for custom styling, including:
    *   *Animation Speed & Fluid Viscosity*
    *   *Particle Count & Wave Scale*
    *   *Parallax Intensity, Contrast, and Bloom*

---

## 🛠️ Built-in Custom Shader Creator (New!)

Write, compile, and run your own custom fragment shaders directly on your device.

<p align="center">
  <strong>💻 Develop on the go • 🚀 Instant GPU hot-reloading • 📂 Persistent Local Storage</strong>
</p>

*   **💾 Custom Shader Library:** Save custom AGSL shaders with unique names. They are persisted locally and automatically loaded into the theme selector.
*   **📤 Import & Share:** Import shader code from your clipboard, or share your custom shaders directly with friends using the native Android Share Sheet.
*   **🛡️ Overwrite Protection:** Built-in safety dialog confirmation protects you from accidentally discarding unsaved code changes when importing templates or loading saved files.
*   **💪 Resilient Hot-Reloading:** If compiling a custom shader fails due to syntax errors, the engine keeps running smoothly using the last successfully compiled shader rather than crashing or blacking out the wallpaper.
*   **🧹 Code Sandbox Utilities:** Dedicated editor controls such as "Clear Editor" to quickly wipe code and build fresh experiments.

---

## 🔋 Engineering & Battery Optimization

VibeFlow 2 is engineered to protect battery health while running heavy GPU calculations:
*   **No Active Polling:** Upgraded from power-hungry state-checking threads to lightweight, event-driven `MediaController.Callback` listeners for color extraction.
*   **Frame Capping:** Includes a Battery Saver Mode to lock the framerate at 30 FPS and bypass resource-intensive post-processing filters.
*   **Native Wallpaper Lifecycle:** Hooks directly into Android's native `WallpaperService`, ensuring rendering stops completely when the screen is turned off or when other apps are fullscreen.

---

## 🚀 Setup & Installation

### Build Requirements
*   **Android Studio** Jellyfish / Koala or newer.
*   **Android SDK 33+** (Android 13+ is required for AGSL shader execution).
*   **Gradle 8.0+**

### Local Build Steps

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/theStxve/vibeFlow2.git
    ```
2.  **Open in Android Studio:**
    Allow Gradle to sync and build project dependencies automatically.
3.  **Deploy to Device:**
    Run the `app` configuration on a physical device or emulator running Android 13+.

> [!NOTE]
> Physical devices with high-end GPUs (e.g. Snapdragon 8 Gen 1 or equivalent) are recommended to enjoy fluid simulation shaders with high viscosity settings and full Bloom enabled.

---

## 📄 License & Disclaimers

*   This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
*   **Third-Party Disclaimer:** This app includes integrations with active audio sessions. See [SPOTIFY_DISCLAIMER](SPOTIFY_DISCLAIMER.md) for details on the Spotify integration.

