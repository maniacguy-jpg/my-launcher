package com.example.mylauncher

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "My Launcher"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(48, 200, 48, 48)
        })
    }
}
