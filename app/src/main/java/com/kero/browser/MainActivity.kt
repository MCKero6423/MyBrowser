package com.kero.browser

import android.content.Context
import android.content.SharedPreferences
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

data class Tab(
    val session: GeckoSession,
    var title: String = "New Tab",
    var url: String = ""
)

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
        private const val HOME_URL = "https://bing.com"
        private const val PREFS_NAME = "KeroBrowserPrefs"
        private const val KEY_TAB_URLS = "tab_urls"
        private const val KEY_CURRENT_INDEX = "current_tab_index"
    }

    private val tabs = ArrayList<Tab>()
    private var currentTabIndex = -1

    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tabsButton: Button
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        tabsButton = findViewById(R.id.btn_tabs)
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (sRuntime == null) {
            // 使用默认配置创建 Runtime，它会自动把 Cookie/Cache 存在 /data/data/com.kero.browser/ 下
            // 这就是为什么 Cookie 其实是生效的，只是之前 Tab 丢了
            sRuntime = GeckoRuntime.create(this)
        }

        // 2. 尝试恢复上次的标签页
        val restored = restoreTabs()
        if (!restored) {
            // 如果没存档，就新建一个主页
            createNewTab(HOME_URL)
        }

        // 3. 事件绑定
        goButton.setOnClickListener { loadUrlFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        backButton.setOnClickListener { getCurrentSession()?.goBack() }
        forwardButton.setOnClickListener { getCurrentSession()?.goForward() }
        tabsButton.setOnClickListener { showTabSwitcher() }
    }

    // --- 生命周期管理 (自动存档) ---

    override fun onPause() {
        super.onPause()
        saveTabs() // 切到后台或退出时，自动保存
    }

    private fun saveTabs() {
        // 简单的序列化：把所有 URL 用 | 符号拼起来
        // 例如: "https://bing.com|https://bilibili.com"
        val urls = tabs.joinToString("|") { it.url }
        
        prefs.edit()
            .putString(KEY_TAB_URLS, urls)
            .putInt(KEY_CURRENT_INDEX, currentTabIndex)
            .apply()
    }

    private fun restoreTabs(): Boolean {
        val urlString = prefs.getString(KEY_TAB_URLS, "") ?: ""
        if (urlString.isEmpty()) return false

        val savedUrls = urlString.split("|")
        if (savedUrls.isEmpty()) return false

        // 恢复所有 Tab
        for (url in savedUrls) {
            if (url.isNotEmpty()) {
                createNewTab(url, switchToIt = false)
            }
        }

        // 恢复选中的 Tab
        val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        if (savedIndex >= 0 && savedIndex < tabs.size) {
            switchToTab(savedIndex)
        } else if (tabs.isNotEmpty()) {
            switchToTab(tabs.size - 1)
        }
        
        return true
    }

    // --- 核心逻辑区 ---

    private fun getCurrentTab(): Tab? {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
            return tabs[currentTabIndex]
        }
        return null
    }

    private fun getCurrentSession(): GeckoSession? {
        return getCurrentTab()?.session
    }

    // 增加 switchToIt 参数，批量恢复时不需要每次都切屏
    private fun createNewTab(url: String, switchToIt: Boolean = true) {
        val session = GeckoSession()
        session.open(sRuntime!!)
        
        val newTab = Tab(session, url = url) // 初始 URL 记下来
        initSessionListeners(newTab)
        
        tabs.add(newTab)
        
        session.loadUri(url)
        
        if (switchToIt) {
            switchToTab(tabs.size - 1)
        }
        updateTabsButton()
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        
        currentTabIndex = index
        val tab = tabs[index]
        
        geckoView.releaseSession()
        geckoView.setSession(tab.session)
        
        urlInput.setText(tab.url)
        updateTabsButton()
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        
        val tab = tabs[index]
        tab.session.close()
        tabs.removeAt(index)
        
        if (tabs.isEmpty()) {
            createNewTab(HOME_URL)
        } else {
            if (index <= currentTabIndex) {
                currentTabIndex = maxOf(0, currentTabIndex - 1)
            }
            switchToTab(currentTabIndex)
        }
        updateTabsButton()
        saveTabs() // 关闭标签时也顺手存一下
    }

    private fun initSessionListeners(tab: Tab) {
        val session = tab.session
        
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
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
                if (url != null) {
                    tab.url = url // 实时更新内存中的 URL
                }
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

    private fun showTabSwitcher() {
        val titles = tabs.mapIndexed { index, tab -> 
            val displayTitle = if (tab.url.isNotEmpty()) tab.url else "New Tab"
            "${index + 1}. $displayTitle"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("切换窗口 (${tabs.size})")
            .setItems(titles) { _, which ->
                switchToTab(which)
            }
            .setPositiveButton("新建窗口") { _, _ ->
                createNewTab(HOME_URL)
            }
            .setNegativeButton("关闭当前") { _, _ ->
                closeTab(currentTabIndex)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun updateTabsButton() {
        tabsButton.text = "${tabs.size}"
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
        if (session != null) {
             session.goBack()
        } else {
             super.onBackPressed()
        }
    }
    
    private fun maxOf(a: Int, b: Int): Int {
        return if (a > b) a else b
    }
}
