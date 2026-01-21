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

        // 1. 获取界面控件 (如果 AI 改了 XML，通常 ID 是这些)
        // 如果这里报错找不到 ID，说明 XML 里的 ID 名字不一样，但至少先保证编译通过
        val view = findViewById<GeckoView>(R.id.geckoview)
        
        // 尝试获取地址栏和按钮 (加了安全空检查，防止崩溃)
        // 注意：这里假设 AI 生成的 ID 是 address_bar 和 go_button
        // 如果你的 XML 里不是这个名字，它只是不工作，但不会导致编译失败
        val urlInput = findViewById<EditText>(getResources().getIdentifier("address_bar", "id", packageName)) ?: findViewById<EditText>(getResources().getIdentifier("url_input", "id", packageName))
        val goButton = findViewById<Button>(getResources().getIdentifier("go_button", "id", packageName)) ?: findViewById<Button>(getResources().getIdentifier("btn_go", "id", packageName))

        val session = GeckoSession()

        // 2. 初始化运行时
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }
        session.open(sRuntime!!)
        view.setSession(session)

        // 3. 【关键修复】正确的 NavigationDelegate 签名
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession, 
                url: String?, 
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                // 在主线程更新地址栏
                if (url != null && urlInput != null) {
                    runOnUiThread {
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }

        // 4. 按钮点击事件：访问网址
        if (goButton != null && urlInput != null) {
            goButton.setOnClickListener {
                val url = urlInput.text.toString()
                if (url.isNotEmpty()) {
                    // 简单的补全逻辑
                    if (!url.startsWith("http")) {
                        session.loadUri("https://")
                    } else {
                        session.loadUri(url)
                    }
                    view.clearFocus() // 隐藏键盘
                }
            }
        }

        // 5. 默认加载主页
        session.loadUri("https://www.bilibili.com")
    }
}
