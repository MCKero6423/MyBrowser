package com.kero.browser

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
    }

    private lateinit var session: GeckoSession
    private lateinit var urlInput: EditText
    private lateinit var geckoView: GeckoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        session = GeckoSession()

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }
        session.open(sRuntime!!)
        geckoView.setSession(session)

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    progressBar.progress = progress
                    progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
                }
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>) {
                if (url != null) {
                    runOnUiThread {
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }

        fun loadUrlFromInput() {
            var url = urlInput.text.toString().trim()
            
            if (url.isNotEmpty()) {
                // 1. 检查是否已经包含协议
                val hasProtocol = url.contains("://")
                
                // 2. 检查特殊协议
                val isSpecial = url.startsWith("about:") || 
                                url.startsWith("javascript:") || 
                                url.startsWith("file:") ||
                                url.startsWith("data:")

                // 3. 只有当既没有通用协议，也不是特殊协议时，才默认补全 HTTPS
                if (!hasProtocol && !isSpecial) {
                    // 【注意】这里是 Kotlin 的字符串模板，Shell 不会再吃掉它了
                    url = "https://$url"
                }

                urlInput.setText(url)
                session.loadUri(url)
                geckoView.clearFocus()
                geckoView.requestFocus()
            }
        }

        goButton.setOnClickListener { loadUrlFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        backButton.setOnClickListener { session.goBack() }
        forwardButton.setOnClickListener { session.goForward() }

        session.loadUri("https://www.bilibili.com")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        session.goBack()
    }
}
