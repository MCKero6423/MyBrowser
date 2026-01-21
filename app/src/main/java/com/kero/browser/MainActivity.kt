package com.kero.browser

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
    }

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private lateinit var urlEditText: EditText
    private lateinit var goButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        geckoView = findViewById(R.id.geckoview)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)

        // 初始化 GeckoView
        geckoSession = GeckoSession()

        // 设置导航代理，用于更新地址栏
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                // 当页面URL变化时，更新地址栏
                runOnUiThread {
                    urlEditText.setText(url ?: "")
                }
            }
        }

        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {}

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        geckoSession.open(sRuntime!!)
        geckoView.setSession(geckoSession)

        // 设置默认页面
        val defaultUrl = "https://www.bilibili.com"
        urlEditText.setText(defaultUrl)
        geckoSession.loadUri(defaultUrl)

        // 点击"前往"按钮加载网址
        goButton.setOnClickListener {
            loadUrl()
        }

        // 在输入框按下回车键（键盘上的"前往"）也可以加载网址
        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl()
                true
            } else {
                false
            }
        }
    }

    /**
     * 加载用户输入的网址
     */
    private fun loadUrl() {
        var url = urlEditText.text.toString().trim()
        
        if (url.isEmpty()) return

        // 智能补全：如果没有协议头，自动添加 https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 如果看起来像搜索词而不是网址，可以跳转到搜索引擎
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                // 使用 Bing 搜索
                "https://www.bing.com/search?q=${url.replace(" ", "+")}"
            }
        }

        geckoSession.loadUri(url)
        
        // 隐藏软键盘
        urlEditText.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
    }

    /**
     * 处理返回键：优先让浏览器后退
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        geckoSession.goBack()
    }
}