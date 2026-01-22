package com.kero.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

data class Tab(
    val session: GeckoSession,
    var title: String = "New Tab",
    var url: String = "",
    var isLoading: Boolean = false // 新增：记录该页面是否正在加载
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
    private lateinit var refreshButton: Button // 新按钮
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化控件
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        tabsButton = findViewById(R.id.btn_tabs)
        refreshButton = findViewById(R.id.btn_refresh) // 获取实例
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        if (!restoreTabs()) {
            createNewTab(HOME_URL)
        }

        // 2. 事件绑定
        goButton.setOnClickListener { loadUrlFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        backButton.setOnClickListener { getCurrentSession()?.goBack() }
        forwardButton.setOnClickListener { getCurrentSession()?.goForward() }
        tabsButton.setOnClickListener { showTabSwitcher() }
        
        // 3. 刷新/停止按钮逻辑
        refreshButton.setOnClickListener {
            val tab = getCurrentTab() ?: return@setOnClickListener
            if (tab.isLoading) {
                tab.session.stop() // 正在加载 -> 停止
                // 视觉上立即变回刷新，虽然 progressDelegate 也会回调，但这样响应更快
                refreshButton.text = "↻"
            } else {
                tab.session.reload() // 没在加载 -> 刷新
                refreshButton.text = "✕"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveTabs()
    }

    private fun saveTabs() {
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

        for (url in savedUrls) {
            if (url.isNotEmpty()) {
                createNewTab(url, switchToIt = false)
            }
        }
        val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        if (savedIndex >= 0 && savedIndex < tabs.size) {
            switchToTab(savedIndex)
        } else if (tabs.isNotEmpty()) {
            switchToTab(tabs.size - 1)
        }
        return true
    }

    private fun getCurrentTab(): Tab? {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size) {
            return tabs[currentTabIndex]
        }
        return null
    }

    private fun getCurrentSession(): GeckoSession? {
        return getCurrentTab()?.session
    }

    private fun createNewTab(url: String, switchToIt: Boolean = true) {
        val session = GeckoSession()
        session.open(sRuntime!!)
        
        val newTab = Tab(session, url = url)
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
        
        // 切换标签时，更新刷新按钮的状态
        updateRefreshButtonState(tab.isLoading)
        updateTabsButton()
    }

    // 封装更新按钮图标的逻辑
    private fun updateRefreshButtonState(isLoading: Boolean) {
        refreshButton.text = if (isLoading) "✕" else "↻"
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
        saveTabs()
    }

    private fun initSessionListeners(tab: Tab) {
        val session = tab.session
        
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                // 更新 Tab 状态
                tab.isLoading = (progress < 100)

                if (session == getCurrentSession()) {
                    runOnUiThread {
                        progressBar.progress = progress
                        progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
                        // 实时更新按钮状态
                        updateRefreshButtonState(tab.isLoading)
                    }
                }
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>) {
                if (url != null) {
                    tab.url = url
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
