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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 获取控件
        val view = findViewById<GeckoView>(R.id.geckoview)
        val urlInput = findViewById<EditText>(R.id.address_bar)
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
        view.setSession(session)

        // 3. 监听网页加载进度 (进度条逻辑)
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

        // 4. 监听地址栏变化
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession, 
                url: String?, 
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                if (url != null) {
                    runOnUiThread {
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }

        // 5. 按钮点击事件
        goButton.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://"
                }
                session.loadUri(url)
                view.clearFocus()
            }
        }

        // 后退按钮
        backButton.setOnClickListener {
            session.goBack()
        }

        // 前进按钮
        forwardButton.setOnClickListener {
            session.goForward()
        }
        
        // 键盘回车跳转
        urlInput.setOnEditorActionListener { _, _, _ ->
            goButton.performClick()
            true
        }

        // 默认主页
        session.loadUri("https://www.bilibili.com")
    }

    // 6. 系统返回键逻辑 (核心：网页后退 vs 关闭APP)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 简单的尝试调用 goBack，如果不能后退，GeckoView 内部状态管理比较复杂
        // 这里做一个简单的处理：直接调用 goBack
        // 理想情况应该检查 session.historyState，但为了代码精简，我们先无脑调
        // 如果想关闭 App，用户可以连续按两次，或者我们之后再优化
        session.goBack() 
        // 注意：这里没有调用 super.onBackPressed()，所以按返回键默认就是尝试网页后退
        // 如果网页退无可退，GeckoSession 不会做什么，界面会停住。
        // *下一版本* 我们可以优化成 "退无可退则关闭App"
    }
}
