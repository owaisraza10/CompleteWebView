# CompleteWebView

Enhanced WebView component for Android that works as intended out of the box, featuring a modern, chainable Fluent API.

## ✨ New in v1.0.1
* **Predictive Back Support:** Fully compatible with Android 14+ system back gestures via `.setupPredictiveBack()`.
* **Domain Whitelisting:** Lock your WebView to specific domains for better security.
* **Fragment Ready:** Fixed context issues so fullscreen video and permissions work perfectly inside Fragments.
* **Architecture Upgrade:** Resolved Gradle cache and manifest merger conflicts.

## Requirements

* Android 5.0+ (API level 21+)
* AndroidX enabled project

## Installation

* Add this library to your project
  * Declare the Gradle repository in your root `settings.gradle.kts`

    ```kotlin
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
            maven { url = uri("[https://jitpack.io](https://jitpack.io)") }
        }
    }
    ```

  * Declare the Gradle dependency in your app module's `build.gradle.kts`

    ```kotlin
    dependencies {
	        implementation 'com.github.owaisraza10:CompleteWebView:1.0.0'
	}
    ```

## Usage

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Layout (XML)
* XML

```
<com.owaisraza.completewebview.CompleteWebView
    android:id="@+id/webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```    
## Activity (Kotlin)
* Kotlin
```
class MainActivity : AppCompatActivity() {

    private lateinit var mWebView: CompleteWebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWebView = findViewById(R.id.webview)

        // Setup the File Picker (Required if you want to support <input type="file">)
        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            mWebView.handleFileChosen(result.data?.data)
        }

        mWebView.onFileChooserRequested = { intent ->
            if (intent != null) filePickerLauncher.launch(intent)
        }

        // Configure and load in one clean chain
        mWebView.loadUrlString("[https://www.example.org/](https://www.example.org/)")
            .enableJavaScript(true)
            .enableCookies(true)
            .enablePullToRefresh(true)
            .enableSmartDarkMode(true)

        // Setup the back button to handle browser history and fullscreen video
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!mWebView.handleBackPress()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
```
## Features
* Fullscreen HTML5 video support (YouTube, Vimeo, etc.) is handled automatically.

* Deep linking to native apps (YouTube, WhatsApp, Twitter, mailto:) is handled automatically.

* Offline custom error pages are handled automatically (No more Android dinosaur screens).

* Features are configured via a chainable Fluent API.

* Receive callbacks when pages start/finish loading, or when file downloads begin.

## Kotlin
```
mWebView.onPageStarted = { url -> 
    // a new page started loading 
}
```
```
mWebView.onPageFinished = { url -> 
    // the new page finished loading 
}
```
```
mWebView.onDownloadStarted = { fileName ->
    // the Android DownloadManager has started downloading a file
}
```
## Wrap the WebView in a native Swipe-to-Refresh layout

* Kotlin
```
mWebView.enablePullToRefresh(true)
```
## Force websites to invert colors and match the device Dark Mode 
* (Requires AndroidX Webkit support)

* Kotlin
```
mWebView.enableSmartDarkMode(true)
```
## Inject an Ad-Blocker to remove ads and tracking scripts

* Kotlin
```
mWebView.enableAdBlock(true)
```
## Bypass the 403: disallowed_useragent error to allow "Sign in with Google" prompts inside the WebView

* Kotlin
```
mWebView.supportGoogleLoginBypass(true)
```
## Enable geolocation support (needs <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />)

* Kotlin
```
mWebView.enableGeolocation(true)
```
## Enable camera and microphone support (needs <uses-permission android:name="android.permission.CAMERA" />)

* Kotlin
```
mWebView.enableCameraSupport(true)
```

## Add custom HTTP headers in addition to the ones sent by the web browser implementation

* Kotlin
```
mWebView.addHttpHeader("Authorization", "Bearer token123")
```
## Define a custom set of permitted hostnames and receive callbacks for all other hostnames

* Kotlin
```
mWebView.addPermittedHostname("example.org")
```

## And
```
mWebView.onExternalPageRequest = { url -> 
    // the user tried to open a page from a non-permitted hostname 
}
```
## Prevent caching of HTML pages

* Kotlin
```
mWebView.preventCaching(true)
```
## Disable cookies

* Kotlin
```
mWebView.enableCookies(false)
```
## Allow or disallow mixed content (HTTP content being loaded inside HTTPS sites)

* Kotlin
 ```
mWebView.setMixedContentAllowed(true)
```

## Or
```
mWebView.setMixedContentAllowed(false)
```
## Switch between mobile and desktop mode

* Kotlin
```
mWebView.setDesktopMode(true)
```

## Or
```
mWebView.setDesktopMode(false)
```
## Load HTML source text and display as page

* Kotlin
```
mWebView.loadHtml("<html>...</html>")
```

## Or

```
val myBaseUrl = "[http://www.example.com/](http://www.example.com/)"
mWebView.loadHtml("<html>...</html>", myBaseUrl)
```
## Setup a JavaScript Bridge to communicate between Kotlin and Web

* Kotlin
* Expose Kotlin to JS
```
mWebView.enableJsBridge("AndroidBridge")
```
```
mWebView.onWebMessageReceived = { message ->
    // Received a message from Javascript
}
```

* Send data from Kotlin to a JS function
```
mWebView.sendDataToWeb("myJavascriptFunction", "{'key': 'value'}")
```
## Contributing
All contributions are welcome! If you wish to contribute, please create an issue first so that your feature, problem or question can be discussed.

## License
This project is licensed under the terms of the MIT License.
