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

        // 3. 进度条
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    progressBar.progress = progress
                    progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
                }
            }
        }

        // 4. 地址栏同步
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

        // 5. 加载逻辑 (通用版，无弹窗)
        fun loadUrlFromInput() {
            var url = urlInput.text.toString().trim()
            
            if (url.isNotEmpty()) {
                // 【通用补全逻辑】
                // 只要不是以标准协议开头，也不是 about: 开头，全部视为网址，强制加 HTTPS
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:")) {
                    url = "https://"
                }

                // 填回输入框，让用户知道我们帮他补全了
                urlInput.setText(url)
                
                // 加载
                session.loadUri(url)
                
                // 收起键盘并聚焦浏览器
                geckoView.clearFocus()
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

        // 导航按钮
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
