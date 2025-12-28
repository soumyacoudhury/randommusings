# Random Musings

This project is a placeholder for educational purposes where I am experimenting with Android development, Gradle configurations, and game loops.

## Recent Changes

- **Android App Module**: Set up a new `:app` module with modern Gradle configuration using Version Catalogs (`libs.versions.toml`).
- **Pong Game Implementation**:
    - `PongView.kt`: A custom `SurfaceView` implementing a classic Pong game loop with simple AI and touch controls.
    - `MainActivity.kt`: Managed the game lifecycle and implemented modern immersive/fullscreen modes using `WindowInsetsController`.
    - `activity_main.xml`: Basic layout hosting the custom game view.
- **Gradle & Dependencies**:
    - Upgraded Android Gradle Plugin (AGP) to `8.13.2`.
    - Integrated `androidx.core` and `androidx.appcompat`.
    - Configured Java 17 and Kotlin 1.9 compatibility.

## Purpose
This repository serves as a playground for:
- Understanding custom view drawing and performance in Android.
- Experimenting with Gradle build systems and project structuring.
- Implementing robust game loops within the Android Activity lifecycle.
