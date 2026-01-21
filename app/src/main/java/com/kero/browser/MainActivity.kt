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

// 定义一个简单的类，用来保存每个标签页的信息
data class Tab(
    val session: GeckoSession,
    var title: String = "New Tab",
    var url: String = ""
)

class MainActivity : AppCompatActivity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
        private const val HOME_URL = "https://bing.com"
    }

    // 这里改成存储 Tab 对象，而不是原始的 Session
    private val tabs = ArrayList<Tab>()
    private var currentTabIndex = -1

    private lateinit var geckoView: GeckoView
    private lateinit var urlInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tabsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoview)
        urlInput = findViewById(R.id.address_bar)
        progressBar = findViewById(R.id.progress_bar)
        tabsButton = findViewById(R.id.btn_tabs)
        val goButton = findViewById<Button>(R.id.go_button)
        val backButton = findViewById<Button>(R.id.btn_back)
        val forwardButton = findViewById<Button>(R.id.btn_forward)

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        createNewTab(HOME_URL)

        goButton.setOnClickListener { loadUrlFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> 
            loadUrlFromInput()
            true 
        }

        // 修复：直接调用 goBack/goForward，不检查 canGoBack (API 不支持)
        backButton.setOnClickListener { getCurrentSession()?.goBack() }
        forwardButton.setOnClickListener { getCurrentSession()?.goForward() }

        tabsButton.setOnClickListener { showTabSwitcher() }
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

    private fun createNewTab(url: String) {
        val session = GeckoSession()
        session.open(sRuntime!!)
        
        val newTab = Tab(session)
        
        // 绑定监听器
        initSessionListeners(newTab)
        
        tabs.add(newTab)
        switchToTab(tabs.size - 1)
        
        session.loadUri(url)
        updateTabsButton()
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        
        currentTabIndex = index
        val tab = tabs[index]
        
        geckoView.releaseSession()
        geckoView.setSession(tab.session)
        
        // 修复：从我们自己记录的 tab.url 获取网址
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
    }

    // 这里传入的是 Tab 对象，方便我们更新 Tab 里的数据
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
                // 修复：把网址记在 Tab 对象里
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
            
            // 可选：监听标题变化（如果有需要）
        }
    }

    private fun showTabSwitcher() {
        // 修复：使用 tab.url 或 tab.title 作为列表标题
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
             // 修复：直接调用 goBack，不检查 canGoBack
             session.goBack()
        } else {
             super.onBackPressed()
        }
    }
    
    private fun maxOf(a: Int, b: Int): Int {
        return if (a > b) a else b
    }
}
