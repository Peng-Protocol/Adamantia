package com.peng.adamantia.spike

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Throwaway spike for Phase 0 of the multi-tab redesign (not part of the shipping app).
 *
 * Answers two questions on a real device, empirically:
 *  1. Does a WebView that is attached but View.GONE keep firing JS timers while
 *     the Activity itself stays foregrounded?
 *  2. Do two WebView instances loading the same file:// URI share localStorage?
 *
 * Launch directly (no launcher icon):
 *   adb shell am start -n com.peng.adamantia/.spike.SpikeActivity
 * Watch native-side logs:
 *   adb logcat -s AdamantiaSpike
 */
class SpikeActivity : Activity() {

    private lateinit var webViewA: WebView
    private lateinit var webViewB: WebView
    private lateinit var reportView: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class SpikeBridge {
        @JavascriptInterface
        fun log(label: String, message: String) {
            Log.d("AdamantiaSpike", "[$label] $message")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val htmlFile = File(cacheDir, "spike.html")
        htmlFile.writeText(SPIKE_HTML)
        val baseUri = htmlFile.toURI().toString()

        webViewA = buildWebView()
        webViewB = buildWebView()

        webViewA.loadUrl("$baseUri?label=A")
        webViewB.loadUrl("$baseUri?label=B")
        webViewB.visibility = View.GONE

        val webWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(webViewA, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(webViewB, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val showAButton = Button(this).apply {
            text = "Show A (hide B)"
            setOnClickListener {
                webViewA.visibility = View.VISIBLE
                webViewB.visibility = View.GONE
                appendReport("Switched: A visible, B hidden (still attached, still running)")
            }
        }
        val showBButton = Button(this).apply {
            text = "Show B (hide A)"
            setOnClickListener {
                webViewA.visibility = View.GONE
                webViewB.visibility = View.VISIBLE
                appendReport("Switched: B visible, A hidden (still attached, still running)")
            }
        }
        val checkStorageButton = Button(this).apply {
            text = "Check storage sharing"
            setOnClickListener { checkStorageSharing() }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(showAButton)
            addView(showBButton)
            addView(checkStorageButton)
        }

        reportView = TextView(this).apply {
            text = "Watch `adb logcat -s AdamantiaSpike` for per-tick logs from both tabs.\n" +
                "Expected: if background JS keeps running, you'll see \"[A] tick=...\" and \"[B] tick=...\" " +
                "lines interleaved every ~2s regardless of which tab is visible, as long as this app " +
                "stays in the foreground. Press Home to background the whole app and see whether ticks " +
                "stop entirely (expected) — that's a separate throttling layer from the per-tab " +
                "visibility question and is exactly why real background work should use the foreground " +
                "Service, not rely on WebView JS.\n\nStorage check results appear here:\n"
            setPadding(24, 24, 24, 24)
            gravity = Gravity.START
        }
        val reportScroll = ScrollView(this).apply {
            addView(reportView)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(controls)
            addView(reportScroll)
            addView(webWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowUniversalAccessFromFileURLs = true
            addJavascriptInterface(SpikeBridge(), "SpikeBridge")
        }
    }

    private fun checkStorageSharing() {
        webViewA.evaluateJavascript(
            "JSON.stringify({a: localStorage.getItem('spike_counter_A'), b: localStorage.getItem('spike_counter_B')})"
        ) { resultFromA ->
            webViewB.evaluateJavascript(
                "JSON.stringify({a: localStorage.getItem('spike_counter_A'), b: localStorage.getItem('spike_counter_B')})"
            ) { resultFromB ->
                mainHandler.post {
                    appendReport("As seen from A's WebView instance: $resultFromA")
                    appendReport("As seen from B's WebView instance: $resultFromB")
                    appendReport(
                        "If both instances report non-null values for BOTH counters, " +
                            "storage is shared across tabs for this file:// origin. If each " +
                            "instance only sees its own counter, storage is isolated per WebView instance."
                    )
                }
            }
        }
    }

    private fun appendReport(line: String) {
        reportView.append("\n$line")
    }

    companion object {
        private val SPIKE_HTML = """
            <!DOCTYPE html>
            <html>
            <body>
              <h2 id="label">TAB</h2>
              <div id="counter">0</div>
              <script>
                function getLabel() {
                  return new URLSearchParams(location.search).get('label') || '?';
                }
                var label = getLabel();
                document.getElementById('label').innerText = 'Tab ' + label;
                var counter = 0;
                function tick() {
                  counter++;
                  document.getElementById('counter').innerText = counter;
                  try {
                    localStorage.setItem('spike_counter_' + label, String(counter));
                  } catch (e) {}
                  if (window.SpikeBridge) {
                    SpikeBridge.log(label, 'tick=' + counter + ' at ' + Date.now());
                  }
                }
                setInterval(tick, 2000);
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
