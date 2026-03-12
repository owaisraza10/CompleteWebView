package owaisraza.completewebview_sandbox

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.owaisraza.completewebview.CompleteWebView

class MainActivity : AppCompatActivity() {

    // NEW: Register the permission launcher
    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val locGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted && locGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ask for both Camera and Location at once
        requestMultiplePermissions.launch(arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ))

        val myWebView = findViewById<CompleteWebView>(R.id.myCustomWebView)

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data?.data
            myWebView.handleFileChosen(data)
        }

        // Changed the URL to a specific geolocation testing site so you can see it work!
        myWebView.loadUrlString("https://www.google.com")
            .enableJavaScript(true)
            .enableJsBridge("NativeApp")
            .enableDownloads()
            .enableCookies()
            .enableGeolocation(true)
            .enableCameraSupport(true)
            .enableAdBlock(true)
            .enableSmartDarkMode(true)
            .enablePullToRefresh()
            .supportGoogleLoginBypass(true)
            .setDesktopMode(false) // Turned off desktop mode so it fits your phone screen better

        myWebView.onFileChooserRequested = { intent ->
            if (intent != null) filePickerLauncher.launch(intent)
        }

        myWebView.onDownloadStarted = { fileName ->
            Toast.makeText(this, "Downloading: $fileName", Toast.LENGTH_LONG).show()
        }

        myWebView.onWebMessageReceived = { message ->
            Toast.makeText(this, "From Web: $message", Toast.LENGTH_SHORT).show()
        }

        myWebView.addHttpHeader("MyHeader", "MyValue")

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!myWebView.handleBackPress()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}