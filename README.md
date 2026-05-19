# VibeFlow 2 🌊✨

A premium, highly-optimized Live Wallpaper Engine for Android, featuring physics-based fluid dynamics, real-time audio reactivity, and context-aware environments.

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_app_logo.png" width="128" height="128" alt="VibeFlow 2 Logo">
</p>

## ✨ Features

- **Dynamic Shaders:** Includes multiple high-performance AGSL shaders like *Pure Chrome* (raytraced liquid metal), *Cosmic Plasma*, and *Frosted Aurora*.
- **Spotify Color Sync:** Automatically extracts the dominant colors from your currently playing album art and injects them into the wallpaper gradient in real-time.
- **FFT Audio Visualizer:** The fluid physics react seamlessly to the frequency spectrum of your device's audio output.
- **Gyroscope Parallax:** A true 3D depth effect that shifts the environment based on your device's tilt.
- **Time of Day Dynamics:** Smoothly transitions the lighting and mood of the wallpaper based on your local time (Sunrise, Noon, Sunset, Night).
- **Pro Customization:** Deeply tune the engine with sliders for Animation Speed, Fluid Viscosity, Particle Count, Wave Scale, Parallax Intensity, Contrast, and Bloom.
- **Battery Friendly:** Engineered with Android's `WallpaperService` and a 30 FPS Battery Saver mode to ensure minimal power consumption.

## 🛠️ Tech Stack
- **Kotlin & Jetpack Compose:** Modern, declarative UI for the settings dashboard.
- **AGSL (Android Graphics Shading Language):** Custom GPU-accelerated shaders for fluid dynamics and procedural noise (Fractal Brownian Motion).
- **MediaSessionManager & Palette API:** For extracting album artwork and generating dynamic color schemes without battery drain.

## 🚀 Installation & Build

1. Clone the repository:
   ```bash
   git clone https://github.com/theStxve/vibeFlow2.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync and build the project.
4. Run the app on your device (Android 13+ recommended for AGSL support).

If you don't have Android Studio, you can download the APK from the [releases](https://github.com/theStxve/vibeFlow2/releases) page.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
