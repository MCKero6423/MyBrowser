package com.kero.browser

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
    }

    // 多窗口管理
    private val sessions = ArrayList<GeckoSession>()
    private var currentTabIndex = -1

    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tabsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化控件
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        tabsButton = findViewById(R.id.btn_tabs)
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)

        // 2. 初始化 Runtime
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        // 3. 创建第一个标签页
        createNewTab("https://www.bilibili.com")

        // 4. 事件绑定
        goButton.setOnClickListener { loadUrlFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        backButton.setOnClickListener { getCurrentSession()?.goBack() }
        forwardButton.setOnClickListener { getCurrentSession()?.goForward() }

        // 【核心】多窗口按钮点击事件
        tabsButton.setOnClickListener { showTabSwitcher() }
    }

    // --- 核心逻辑区 ---

    private fun getCurrentSession(): GeckoSession? {
        if (currentTabIndex >= 0 && currentTabIndex < sessions.size) {
            return sessions[currentTabIndex]
        }
        return null
    }

    // 创建新标签页
    private fun createNewTab(url: String) {
        val session = GeckoSession()
        session.open(sRuntime!!)
        
        // 绑定监听器 (每个 Session 都要绑定)
        initSessionListeners(session)
        
        sessions.add(session)
        switchToTab(sessions.size - 1) // 切换到新创建的 tab
        
        session.loadUri(url)
        updateTabsButton()
    }

    // 切换标签页
    private fun switchToTab(index: Int) {
        if (index < 0 || index >= sessions.size) return
        
        currentTabIndex = index
        val session = sessions[index]
        
        // 把 View 指向新的 Session
        geckoView.releaseSession()
        geckoView.setSession(session)
        
        // 更新 UI 状态
        urlInput.setText(session.currentUri ?: "")
        updateTabsButton()
    }

    // 关闭标签页
    private fun closeTab(index: Int) {
        if (index < 0 || index >= sessions.size) return
        
        val session = sessions[index]
        session.close()
        sessions.removeAt(index)
        
        if (sessions.isEmpty()) {
            // 如果关完了，自动新建一个空白页
            createNewTab("about:blank")
        } else {
            // 如果关的是当前的，或者是前面的，需要修正 index
            if (index <= currentTabIndex) {
                currentTabIndex = maxOf(0, currentTabIndex - 1)
            }
            switchToTab(currentTabIndex)
        }
        updateTabsButton()
    }

    // 初始化 Session 的监听器
    private fun initSessionListeners(session: GeckoSession) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                // 只有当前显示的 Session 才有资格更新进度条
                if (session == getCurrentSession()) {
                    runOnUiThread {
                        progressBar.progress = progress
                        progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
                    }
                }
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>) {
                // 只有当前显示的 Session 才有资格更新地址栏
                if (session == getCurrentSession() && url != null) {
                    runOnUiThread {
                        if (!urlInput.hasFocus()) {
                            urlInput.setText(url)
                        }
                    }
                }
            }
        }
    }

    // 弹出窗口切换列表
    private fun showTabSwitcher() {
        // 获取所有标题 (如果没有标题就显示 URL，还没有就显示 Tab #N)
        val titles = sessions.mapIndexed { index, session -> 
            val title = if (session.currentUri?.isNotEmpty() == true) session.currentUri else "New Tab"
            "${index + 1}. $title"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("切换窗口 (${sessions.size})")
            .setItems(titles) { _, which ->
                switchToTab(which)
            }
            .setPositiveButton("新建窗口") { _, _ ->
                createNewTab("https://www.baidu.com")
            }
            .setNegativeButton("关闭当前") { _, _ ->
                closeTab(currentTabIndex)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun updateTabsButton() {
        tabsButton.text = "${sessions.size}"
    }

    private fun loadUrlFromInput() {
        val session = getCurrentSession() ?: return
        var url = urlInput.text.toString().trim()
        if (url.isNotEmpty()) {
            val hasProtocol = url.contains("://")
            val isSpecial = url.startsWith("about:") || url.startsWith("file:")
            
            if (!hasProtocol && !isSpecial) {
                url = "https://$url"
            }
            urlInput.setText(url)
            session.loadUri(url)
            geckoView.clearFocus()
            geckoView.requestFocus()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val session = getCurrentSession()
        if (session != null && session.canGoBack()) { // 需要 GeckoView 121+ API 支持 canGoBack 检查，这里先盲调
            session.goBack()
        } else {
             // 如果不能后退了，且有多个标签，提示一下是否退出？
             // 这里简化：直接后台运行
             moveTaskToBack(true)
        }
    }
    
    // 简单的 maxOf 辅助函数 (防止低版本 Kotlin 报错)
    private fun maxOf(a: Int, b: Int): Int {
        return if (a > b) a else b
    }
}
