package com.kero.browser

import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 精确获取控件 (这次 ID 绝对对得上)
        val view = findViewById<GeckoView>(R.id.geckoview)
        val urlInput = findViewById<EditText>(R.id.address_bar)
        val goButton = findViewById<Button>(R.id.go_button)

        val session = GeckoSession()

        // 2. 初始化 Runtime
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }
        session.open(sRuntime!!)
        view.setSession(session)

        // 3. 监听地址栏变化 (网页变了，地址栏也要变)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession, 
                url: String?, 
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                if (url != null) {
                    runOnUiThread {
                        // 只有当用户没有在输入时，才更新地址栏
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }

        // 4. 点击按钮逻辑 (核心修复)
        goButton.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                // 如果没写 http 头，自动补全
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://"
                }
                session.loadUri(url)
                view.clearFocus() // 收起键盘
            }
        }
        
        // 5. 键盘回车键逻辑 (按键盘上的 Go 也能跳转)
        urlInput.setOnEditorActionListener { _, _, _ ->
            goButton.performClick()
            true
        }

        // 默认主页
        session.loadUri("https://www.bilibili.com")
    }
}
