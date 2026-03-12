CompleteWebView 🚀
CompleteWebView is a powerful, modern, and lightweight Android WebView library written in Kotlin. It solves common WebView headaches (File uploads, Ad-blocking, Fullscreen video, etc.) with a clean Fluent API.

✨ Features
🚫 Built-in AdBlocker: Blocks common ad domains and trackers automatically.

🎥 HTML5 Fullscreen Video: Proper support for fullscreen YouTube and web video players.

🌑 Smart Dark Mode: Automatically forces websites to match the app's dark theme.

🌍 Geolocation & Camera: Simplified hardware permission handling.

🔄 Pull-to-Refresh: Drop-in support for "Swipe to Refresh" without changing your XML.

📂 File Uploads & Downloads: Fully managed file picking and system downloads.

🔑 Google Login Bypass: Fixes the dreaded 403: disallowed_useragent error.

📱 Desktop Mode: One-tap toggle between mobile and desktop rendering.

📦 Installation
1. Add JitPack to your settings.gradle.kts:

Kotlin
dependencyResolutionManagement {
repositories {
maven { url = uri("https://jitpack.io") }
}
}
2. Add the dependency to your app/build.gradle.kts:

Kotlin
dependencies {
implementation("com.github.YOUR_GITHUB_USERNAME:CompleteWebView:1.0.0")
}
🚀 Quick Usage
In your Activity:

Kotlin
val myWebView = findViewById<CompleteWebView>(R.id.myWebView)

myWebView.loadUrlString("https://www.google.com")
.enableJavaScript(true)
.enableAdBlock(true)
.enableSmartDarkMode(true)
.enablePullToRefresh(true)
.enableCameraSupport(true)
.supportGoogleLoginBypass(true)
🛠️ Requirements
Android 5.0 (API 24) or higher.

AndroidX enabled project.

📄 License
MIT License. Feel free to use it in any project!