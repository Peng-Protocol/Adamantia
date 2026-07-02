package com.peng.adamantia

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PREFS_NAME = "AdamantiaPrefs"
    private val TABS_KEY = "SavedTabs"

    // File Picker to load local HTML
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadLocalHtml(uri.toString())
                saveTab(uri.toString())
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                databaseEnabled = true
            }
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidBackground")
            
            // Allow cross-origin for local files if needed
            settings.allowUniversalAccessFromFileURLs = true
        }

        setContentView(webView)

        // Restore tabs or show initial picker
        val savedTabs = getSavedTabs()
        if (savedTabs.isNotEmpty()) {
            loadLocalHtml(savedTabs.last())
        } else {
            openFilePicker()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/html"
        }
        filePickerLauncher.launch(intent)
    }

    private fun loadLocalHtml(uriString: String) {
        webView.loadUrl(uriString)
    }

    // --- Tab Persistence Logic ---

    private fun saveTab(uri: String) {
        val tabs = getSavedTabs().toMutableList()
        if (!tabs.contains(uri)) {
            tabs.add(uri)
            val json = Gson().toJson(tabs)
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(TABS_KEY, json).apply()
        }
    }

    private fun getSavedTabs(): List<String> {
        val json = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(TABS_KEY, null)
        return if (json == null) emptyList() 
        else Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}