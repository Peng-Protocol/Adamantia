package com.peng.adamantia

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val mContext: Context) {

    @JavascriptInterface
    fun startBackgroundTask() {
        val intent = Intent(mContext, MyBackgroundService::class.java)
        mContext.startForegroundService(intent)
        Toast.makeText(mContext, "Background Service Started", Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun stopBackgroundTask() {
        val intent = Intent(mContext, MyBackgroundService::class.java)
        mContext.stopService(intent)
        Toast.makeText(mContext, "Background Service Stopped", Toast.LENGTH_SHORT).show()
    }
}