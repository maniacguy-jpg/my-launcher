package com.example.mylauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class AppItem(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {
    private val allApps = ArrayList<AppItem>()
    private val shown = ArrayList<AppItem>()
    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun launch(item: AppItem) {
        packageManager.getLaunchIntentForPackage(item.packageName)?.let { startActivity(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(dp(16), dp(40), dp(16), 0)
        }

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 56f
            setTextColor(Color.WHITE)
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 16f
            setTextColor(Color.parseColor("#CCFFFFFF"))
        }
        root.addView(clock)
        root.addView(date)

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.parseColor("#99FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(Color.parseColor("#33FFFFFF"), 14)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
                bottomMargin = dp(12)
            }
        )

        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            setPadding(0, dp(4), 0, dp(16))
            clipToPadding = false
        }
        adapter = AppAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ -> launch(shown[position]) }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }
        })
        search.setOnEditorActionListener { _, _, _ ->
            shown.firstOrNull()?.let { launch(it) }
            true
        }

        setContentView(root)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        search.setText("")
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        shown.clear()
        if (q.isEmpty()) shown.addAll(allApps)
        else shown.addAll(allApps.filter { it.label.lowercase().contains(q) })
        adapter.notifyDataSetChanged()
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
                allApps.clear()
                allApps.addAll(list)
                applyFilter(search.text.toString())
            }
        }.start()
    }

    override fun onBackPressed() {
        // A launcher stays on screen when Back is pressed
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
                    background = rounded(Color.parseColor("#22FFFFFF"), 14)
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                })
                addView(
                    TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 11f
                        maxLines = 1
                        gravity = Gravity.CENTER
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
                )
            }
            val item = shown[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(item.icon)
            (row.getChildAt(1) as TextView).text = item.label
            return row
        }
    }
}
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
