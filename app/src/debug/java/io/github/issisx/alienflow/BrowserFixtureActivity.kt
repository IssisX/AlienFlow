package io.github.issisx.alienflow

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

/** Local fixture only: excluded from every release variant. */
class BrowserFixtureActivity : Activity() {
    lateinit var web: WebView; private set
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.blockNetworkLoads = true
        setContentView(web)
        web.loadDataWithBaseURL("https://editor.fixture.invalid", """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            body{background:#222;color:white;font:20px sans-serif;padding:20px}
            #editor{border:1px solid gray;border-radius:20px;min-height:200px;padding:20px}
            p{margin:0}
            p.empty:before{content:attr(data-placeholder);color:#999;float:left;height:0;pointer-events:none}
            </style>
            <div id="editor" contenteditable="true" role="textbox" aria-multiline="true">
            <p class="empty" data-placeholder="Ask ChatGPT"><br></p></div>
            <script>editor.addEventListener('input',()=>editor.querySelectorAll('p').forEach(p=>p.classList.toggle('empty',!p.textContent)));</script>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
}

