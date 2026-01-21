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

        // 1. 获取控件
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        session = GeckoSession()

        // 2. 初始化 Runtime
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }
        session.open(sRuntime!!)
        geckoView.setSession(session)

        // 3. 进度条逻辑
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    progressBar.progress = progress
                    if (progress < 100) {
                        progressBar.visibility = View.VISIBLE
                    } else {
                        progressBar.visibility = View.INVISIBLE
                    }
                }
            }
        }

        // 4. 地址栏同步逻辑
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession, 
                url: String?, 
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                if (url != null) {
                    runOnUiThread {
                        // 只有当用户没在打字时，才更新地址栏
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }

        // 5. 核心修复：提取统一的加载函数
        fun loadUrlFromInput() {
            var url = urlInput.text.toString().trim() // 去除首尾空格
            
            if (url.isNotEmpty()) {
                // 【修复逻辑】只要没有 :// 就强制加 https://
                // 这能同时解决 baidu.com 和 www.baidu.com 的问题
                if (!url.contains("://")) {
                    url = "https://"
                }
                
                session.loadUri(url)
                geckoView.clearFocus() // 收起键盘
                
                // 暂时把焦点移回 WebView，防止键盘再次弹出
                geckoView.requestFocus()
            }
        }

        // 按钮点击
        goButton.setOnClickListener {
            loadUrlFromInput()
        }

        // 键盘回车
        urlInput.setOnEditorActionListener { _, _, _ ->
            loadUrlFromInput()
            true
        }

        // 后退/前进
        backButton.setOnClickListener { session.goBack() }
        forwardButton.setOnClickListener { session.goForward() }

        // 默认主页
        session.loadUri("https://www.bilibili.com")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        session.goBack()
    }
}
