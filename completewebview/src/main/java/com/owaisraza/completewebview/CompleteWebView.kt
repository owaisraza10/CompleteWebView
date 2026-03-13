package com.owaisraza.completewebview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class CompleteWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private fun getActivityFromContext(): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    private val blockedHostnames = mutableListOf<String>()

    fun addBlockedHostname(hostname: String): CompleteWebView {
        blockedHostnames.add(hostname)
        return this
    }

    // --- CALLBACK LAMBDAS (AdvancedWebView Feature) ---
    var onPageStarted: ((url: String?) -> Unit)? = null
    var onPageFinished: ((url: String?) -> Unit)? = null
    var onExternalPageRequest: ((url: String) -> Unit)? = null
    var onDownloadStarted: ((fileName: String) -> Unit)? = null
    var onWebMessageReceived: ((String) -> Unit)? = null
    var onFileChooserRequested: ((Intent?) -> Unit)? = null

    // --- INTERNAL STATE ---
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val customHttpHeaders = mutableMapOf<String, String>()
    private val permittedHostnames = mutableListOf<String>()
    private var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout? = null
    private val adDomains = hashSetOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "adnxs.com", "adform.net", "adtech.de",
        "taboola.com", "outbrain.com", "amazon-adsystem.com", "casalemedia.com"
    )
    private var isAdBlockEnabled = false
    fun enableAdBlock(enable: Boolean): CompleteWebView {
        isAdBlockEnabled = enable
        return this
    }

    // --- FULLSCREEN VIDEO STATE ---
    private var customView: android.view.View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val progressBar = android.widget.ProgressBar(
        context, null, android.R.attr.progressBarStyleHorizontal
    ).apply {
        layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, 10, Gravity.TOP)
        visibility = android.view.View.GONE
    }

    private var errorView: android.view.View? = null

    init {
        addView(progressBar)
        this.isFocusable = true
        this.isFocusableInTouchMode = true
        this.webViewClient = InternalWebViewClient()
        this.webChromeClient = CompleteWebChromeClient()
        this.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_UP -> if (!v.hasFocus()) v.requestFocus()
            }
            false
        }
    }

    // --- ADVANCED WEBVIEW FEATURES (FLUENT API) ---

    fun setDesktopMode(enabled: Boolean): CompleteWebView {
        val webSettings = this.settings
        if (enabled) {
            // Spoof a desktop browser
            webSettings.userAgentString = webSettings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA") + " Desktop"
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.displayZoomControls = false
        } else {
            // Revert to mobile
            webSettings.userAgentString = WebSettings.getDefaultUserAgent(context)
            webSettings.useWideViewPort = false
            webSettings.loadWithOverviewMode = false
        }
        return this
    }

    fun setMixedContentAllowed(allowed: Boolean): CompleteWebView {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            this.settings.mixedContentMode = if (allowed) {
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            } else {
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }
        return this
    }

    fun preventCaching(prevent: Boolean): CompleteWebView {
        this.settings.cacheMode = if (prevent) {
            WebSettings.LOAD_NO_CACHE
        } else {
            WebSettings.LOAD_DEFAULT
        }
        return this
    }

    fun addHttpHeader(name: String, value: String): CompleteWebView {
        customHttpHeaders[name] = value
        return this
    }

    fun addPermittedHostname(hostname: String): CompleteWebView {
        permittedHostnames.add(hostname)
        return this
    }

    fun enableMultiWindowSupport(enable: Boolean): CompleteWebView {
        this.settings.setSupportMultipleWindows(enable)
        this.settings.javaScriptCanOpenWindowsAutomatically = enable
        return this
    }

    fun loadHtml(htmlString: String, baseUrl: String? = null): CompleteWebView {
        this.loadDataWithBaseURL(baseUrl, htmlString, "text/html", "UTF-8", null)
        return this
    }

    override fun loadUrl(url: String) {
        if (customHttpHeaders.isNotEmpty()) {
            super.loadUrl(url, customHttpHeaders)
        } else {
            super.loadUrl(url)
        }
    }

    // Tricks Google into allowing OAuth logins inside the WebView
    fun supportGoogleLoginBypass(enable: Boolean = true): CompleteWebView {
        if (enable) {
            // Google blocks WebViews by looking for the "; wv" tag in the User-Agent.
            // We simply remove that tag.
            val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
            this.settings.userAgentString = defaultUserAgent.replace("; wv", "")
        } else {
            this.settings.userAgentString = WebSettings.getDefaultUserAgent(context)
        }
        return this
    }

    fun enablePullToRefresh(enable: Boolean = true): CompleteWebView {
        // We use post {} to ensure the WebView is fully drawn on the screen first
        post {
            if (enable && swipeRefreshLayout == null) {
                val parentView = this.parent as? android.view.ViewGroup
                if (parentView != null) {
                    // 1. Create the refresh layout
                    swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
                        setOnRefreshListener { this@CompleteWebView.reload() }
                    }

                    // 2. Safely swap the WebView with the RefreshLayout in the UI tree
                    val index = parentView.indexOfChild(this)
                    val params = this.layoutParams
                    parentView.removeView(this)

                    swipeRefreshLayout?.addView(this, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    parentView.addView(swipeRefreshLayout, index, params)
                }
            } else if (!enable && swipeRefreshLayout != null) {
                swipeRefreshLayout?.isEnabled = false
            }
        }
        return this
    }

    // Forces the webpage to render in Dark Mode
    fun enableSmartDarkMode(enable: Boolean): CompleteWebView {
        // We must check if the phone's Android version supports this specific feature first
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
            val mode = if (enable) {
                androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
            } else {
                androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
            }
            androidx.webkit.WebSettingsCompat.setForceDark(this.settings, mode)
        }
        return this
    }

    // Enables the WebView to use the device Camera and Microphone
    fun enableCameraSupport(enable: Boolean = true): CompleteWebView {
        // No specific settings change needed here, we handle it in WebChromeClient
        return this
    }

    // --- EXISTING FEATURES ---

    @SuppressLint("SetJavaScriptEnabled")
    fun enableJavaScript(enable: Boolean): CompleteWebView {
        this.settings.javaScriptEnabled = enable
        this.settings.domStorageEnabled = enable
        this.settings.databaseEnabled = enable
        return this
    }

    fun loadUrlString(url: String): CompleteWebView {
        this.loadUrl(url)
        return this
    }

    fun enableCookies(enable: Boolean = true): CompleteWebView {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(enable)
        cookieManager.setAcceptThirdPartyCookies(this, enable)
        return this
    }

    fun enableGeolocation(enable: Boolean = true): CompleteWebView {
        this.settings.setGeolocationEnabled(enable)
        return this
    }

    @SuppressLint("JavascriptInterface")
    fun enableJsBridge(interfaceName: String = "AndroidBridge"): CompleteWebView {
        enableJavaScript(true)
        this.addJavascriptInterface(WebAppInterface(), interfaceName)
        return this
    }

    fun handleBackPress(): Boolean {
        return if (this.canGoBack()) {
            this.goBack()
            true
        } else {
            false
        }
    }

    fun enableDownloads(): CompleteWebView {
        this.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = android.app.DownloadManager.Request(Uri.parse(url))
            val cookie = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("Cookie", cookie)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(fileName)
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            onDownloadStarted?.invoke(fileName)
        }
        return this
    }

    fun handleFileChosen(uri: Uri?) {
        if (uri != null) {
            fileUploadCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // --- ERROR VIEW LOGIC ---

    private fun showErrorPage() {
        post {
            if (errorView == null) {
                errorView = android.view.LayoutInflater.from(context)
                    .inflate(R.layout.layout_webview_error, this, false)
                errorView?.findViewById<android.widget.Button>(R.id.btnRetry)?.setOnClickListener {
                    hideErrorPage()
                    this.reload()
                }
            }
            if (errorView?.parent == null) {
                this.addView(errorView)
            }
        }
    }

    private fun hideErrorPage() {
        post {
            errorView?.let { this.removeView(it) }
        }
    }

    // --- CLIENTS ---

    private inner class InternalWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onPageStarted?.invoke(url)
            hideErrorPage()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (isAdBlockEnabled) {
                injectAdKiller(view)
            }
            CookieManager.getInstance().flush()
            // NEW: Stop the swipe-to-refresh spinning animation
            swipeRefreshLayout?.isRefreshing = false
            onPageFinished?.invoke(url)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (!isAdBlockEnabled) return super.shouldInterceptRequest(view, request)

            val url = request?.url?.toString()?.lowercase() ?: ""
            val host = request?.url?.host?.lowercase() ?: ""

            // Check if the host matches our blacklist
            val isAd = adDomains.any { host.contains(it) }

            if (isAd) {
                // Return an empty 1x1 pixel image or just empty text to "kill" the ad
                return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
            }

            return super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false

            // HOSTNAME BLACKLIST CHECK
            if (blockedHostnames.isNotEmpty()) {
                val host = request.url?.host ?: ""
                val isBlocked = blockedHostnames.any { host == it || host.endsWith(".$it") }
                if (isBlocked) {
                    onExternalPageRequest?.invoke(url) // Trigger the callback
                    return true // Block the load
                }
            }

            // HOSTNAME RESTRICTION CHECK
            if (permittedHostnames.isNotEmpty()) {
                val host = request.url?.host ?: ""
                val isPermitted = permittedHostnames.any { host == it || host.endsWith(".$it") }
                if (!isPermitted) {
                    onExternalPageRequest?.invoke(url)
                    return true // Block the load
                }
            }

            try {
                if (url.startsWith("intent://")) {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                        if (fallbackUrl != null) view?.loadUrl(fallbackUrl)
                        else if (intent.`package` != null) {
                            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${intent.`package`}"))
                            context.startActivity(playStoreIntent)
                        }
                    }
                    return true
                }

                if (url.contains("youtube.com") || url.contains("youtu.be") ||
                    url.contains("twitter.com") || url.contains("x.com") ||
                    url.contains("instagram.com") || url.contains("play.google.com")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    return true
                }

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    return true
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) showErrorPage()
        }

        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            showErrorPage()
        }

        private fun injectAdKiller(view: WebView?) {
            val js = """
            (function() {
                // Remove common ad selectors
                const adSelectors = ['.ad-showing', '.ad-container', '.video-ads', '.ytp-ad-module', 'div#player-ads'];
                const removeAds = () => {
                    adSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach(el => el.remove());
                    });
                    // Auto-click 'Skip Ad' buttons
                    const skipBtn = document.querySelector('.ytp-ad-skip-button');
                    if (skipBtn) skipBtn.click();
                };
                // Run every 500ms to catch dynamic ads
                setInterval(removeAds, 500);
            })();
        """.trimIndent()
            view?.evaluateJavascript(js, null)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // Tell the back button to re-evaluate whether it should be active!
            updateBackState()
        }
    }

    private inner class CompleteWebChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
        ): Boolean {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback
            onFileChooserRequested?.invoke(fileChooserParams?.createIntent())
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            callback?.invoke(origin, hasLocationPermission, false)
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress == 100) android.view.View.GONE else android.view.View.VISIBLE
        }

        // Triggered when a video enters fullscreen
        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
            super.onShowCustomView(view, callback)

            // If a view is already fullscreen, terminate the new one
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }

            customView = view
            customViewCallback = callback

            // Find the absolute root window of the app and paste the video on top
            val activity = getActivityFromContext()
            val decorView = activity?.window?.decorView as? FrameLayout
            decorView?.addView(customView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // Hide the normal WebView (and the swipe refresh wrapper if it's active)
            this@CompleteWebView.visibility = android.view.View.GONE
            swipeRefreshLayout?.visibility = android.view.View.GONE
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            val activity = getActivityFromContext() ?: return

            // Check if the app actually has hardware permissions
            val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasCamera) {
                // Grant whatever the web page is asking for (Camera, Audio, etc.)
                post {
                    request?.grant(request.resources)
                }
            } else {
                // If app doesn't have it, we must deny to avoid a crash
                request?.deny()
            }
        }

        // Triggered when a video exits fullscreen
        override fun onHideCustomView() {
            super.onHideCustomView()

            val activity = getActivityFromContext()
            val decorView = activity?.window?.decorView as? FrameLayout
            decorView?.removeView(customView)

            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null

            // Bring the WebView back to the screen
            this@CompleteWebView.visibility = android.view.View.VISIBLE
            swipeRefreshLayout?.visibility = android.view.View.VISIBLE
        }


    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            post { onWebMessageReceived?.invoke(message) }
        }
    }

    // --- PREDICTIVE BACK SUPPORT ---
    private var backPressCallback: OnBackPressedCallback? = null

    fun setupPredictiveBack(activity: ComponentActivity): CompleteWebView {
        backPressCallback = object : OnBackPressedCallback(false) { // Starts disabled
            override fun handleOnBackPressed() {
                // If video is fullscreen, close it. Otherwise, go back in history.
                if (customView != null) {
                    webChromeClient?.onHideCustomView()
                } else {
                    handleBackPress()
                }
                updateBackState()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, backPressCallback!!)
        return this
    }

    internal fun updateBackState() {
        // Enable the callback only if we can go back OR a video is playing fullscreen
        backPressCallback?.isEnabled = this.canGoBack() || customView != null
    }
}