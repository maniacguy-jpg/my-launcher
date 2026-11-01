package com.example.mylauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppItem(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private lateinit var adapter: AppAdapter

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            setPadding(dp(8), dp(48), dp(8), dp(16))
            clipToPadding = false
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        adapter = AppAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            packageManager.getLaunchIntentForPackage(apps[position].packageName)
                ?.let { startActivity(it) }
        }
        setContentView(grid)
        loadApps()
    }

    private fun loadApps() {
        Thread {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppItem(
                        it.loadLabel(packageManager).toString(),
                        it.activityInfo.packageName,
                        it.loadIcon(packageManager)
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps.clear()
                apps.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    override fun onBackPressed() {
        // A launcher should stay on screen when Back is pressed
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                })
                addView(TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    maxLines = 1
                    gravity = Gravity.CENTER
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            val item = apps[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(item.icon)
            (row.getChildAt(1) as TextView).text = item.label
            return row
        }
    }
}
