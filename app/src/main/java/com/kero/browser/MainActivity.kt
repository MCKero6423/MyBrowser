package com.kero.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
    var isLoading: Boolean = false
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
    private lateinit var refreshButton: ImageButton // 改为 ImageButton
    private lateinit var menuButton: ImageButton    // 改为 ImageButton
    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DBHelper(this)
        
        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        
        // 注意类型转换
        tabsButton = findViewById(R.id.btn_tabs)
        refreshButton = findViewById(R.id.btn_refresh)
        menuButton = findViewById(R.id.btn_menu)
        
        val backButton = findViewById<ImageButton>(R.id.btn_back)
        val forwardButton = findViewById<ImageButton>(R.id.btn_forward)
        // goButton 实际上被我们隐藏了，但代码里留着防止报错
        val goButton = findViewById<View>(R.id.go_button)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        if (!restoreTabs()) {
            createNewTab(HOME_URL)
        }

        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        backButton.setOnClickListener { getCurrentSession()?.goBack() }
        forwardButton.setOnClickListener { getCurrentSession()?.goForward() }
        tabsButton.setOnClickListener { showTabSwitcher() }
        menuButton.setOnClickListener { showMainMenu() }
        
        // 刷新/停止逻辑更新：换图标
        refreshButton.setOnClickListener {
            val tab = getCurrentTab() ?: return@setOnClickListener
            if (tab.isLoading) {
                tab.session.stop()
                refreshButton.setImageResource(R.drawable.ic_refresh)
            } else {
                tab.session.reload()
                refreshButton.setImageResource(R.drawable.ic_close)
            }
        }
    }

    // --- 菜单与历史记录 ---
    private fun showMainMenu() {
        val options = arrayOf("历史记录", "清除历史", "关于")
        AlertDialog.Builder(this)
            .setTitle("菜单")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showHistoryDialog()
                    1 -> {
                        dbHelper.clearHistory()
                        // Toast.makeText(this, "历史已清除", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("KeroBrowser")
                            .setMessage("v0.9.3\nDesigned by QIU SHENGMING")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showHistoryDialog() {
        val historyList = dbHelper.getAllHistory()
        if (historyList.isEmpty()) {
            AlertDialog.Builder(this).setTitle("历史记录").setMessage("暂无记录").show()
            return
        }

        val items = historyList.map { 
            val display = if (it.title.isNotEmpty()) it.title else it.url
            if (display.length > 50) display.substring(0, 50) + "..." else display
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("历史记录")
            .setItems(items) { _, which ->
                val targetUrl = historyList[which].url
                getCurrentSession()?.loadUri(targetUrl)
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    // --- 基础逻辑 ---

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
        updateRefreshButtonState(tab.isLoading)
        updateTabsButton()
    }

    // 逻辑更新：根据状态换图标
    private fun updateRefreshButtonState(isLoading: Boolean) {
        if (isLoading) {
            refreshButton.setImageResource(R.drawable.ic_close)
        } else {
            refreshButton.setImageResource(R.drawable.ic_refresh)
        }
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
                tab.isLoading = (progress < 100)
                if (session == getCurrentSession()) {
                    runOnUiThread {
                        progressBar.progress = progress
                        progressBar.visibility = if (progress < 100) View.VISIBLE else View.INVISIBLE
                        updateRefreshButtonState(tab.isLoading)
                    }
                }
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>) {
                if (url != null) {
                    tab.url = url
                    if (!url.startsWith("about:")) {
                        Thread { dbHelper.addHistory("Page: $url", url) }.start()
                    }
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
            .setItems(titles) { _, which -> switchToTab(which) }
            .setPositiveButton("新建窗口") { _, _ -> createNewTab(HOME_URL) }
            .setNegativeButton("关闭当前") { _, _ -> closeTab(currentTabIndex) }
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
