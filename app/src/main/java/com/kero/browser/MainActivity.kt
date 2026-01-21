package com.kero.browser

import android.os.Bundle
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

        val view = findViewById<GeckoView>(R.id.geckoview)
        val session = GeckoSession()

        session.contentDelegate = object : GeckoSession.ContentDelegate {}

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
        }

        session.open(sRuntime!!)
        view.setSession(session)
        session.loadUri("https://www.bilibili.com")
    }
}