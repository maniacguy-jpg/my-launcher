package com.example.mylauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class AppItem(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {
    private val allApps = ArrayList<AppItem>()
    private val shown = ArrayList<AppItem>()
    private val dockPkgs = ArrayList<String>()
    private val hidden = HashSet<String>()

    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText
    private lateinit var drawer: LinearLayout
    private lateinit var dock: LinearLayout
    private var drawerOpen = false

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun launch(item: AppItem) {
        packageManager.getLaunchIntentForPackage(item.packageName)?.let { startActivity(it) }
    }

    private fun loadSettings() {
        dockPkgs.clear()
        dockPkgs.addAll(prefs.getString("dock", "")!!.split(",").filter { it.isNotEmpty() })
        hidden.clear()
        hidden.addAll(prefs.getStringSet("hidden", emptySet())!!)
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("dock", dockPkgs.joinToString(","))
            .putStringSet("hidden", HashSet(hidden))
            .apply()
    }

    private fun focusIn(v: View?) {
        v?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(110)?.start()
    }

    private fun focusOut(v: View?) {
        v?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(160)?.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.setBackgroundBlurRadius(60)
            } catch (e: Throwable) {
                // blur not supported on this phone: ignore
            }
        }
        loadSettings()

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#40000000"), Color.parseColor("#B3000000"))
        )

        // ---------- HOME LAYER ----------
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }
        home.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 76f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setShadowLayer(12f, 0f, 3f, Color.parseColor("#66000000"))
        })
        home.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 17f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#DDFFFFFF"))
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#66000000"))
        })
        home.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        home.addView(
            TextView(this).apply {
                text = "Swipe up for all apps"
                textSize = 12f
                setTextColor(Color.parseColor("#88FFFFFF"))
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        )
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            background = rounded(Color.parseColor("#38FFFFFF"), 30).apply {
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        home.addView(
            dock,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val homeGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) < dp(70) || abs(dy) < abs(dx)) return false
                if (dy < 0) openDrawer() else expandNotifications()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showHomeMenu()
            }
        })
        home.setOnTouchListener { _, ev -> homeGestures.onTouchEvent(ev) }

        // ---------- DRAWER LAYER ----------
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2101114"))
            setPadding(dp(16), dp(24), dp(16), 0)
            visibility = View.GONE
        }

        val handle = FrameLayout(this)
        handle.addView(
            View(this).apply { background = rounded(Color.parseColor("#66FFFFFF"), 3) },
            FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER)
        )
        val drawerGestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                closeDrawer(true)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && e2.y - e1.y > dp(30)) {
                    closeDrawer(true)
                    return true
                }
                return false
            }
        })
        handle.setOnTouchListener { _, ev -> drawerGestures.onTouchEvent(ev) }
        drawer.addView(
            handle,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
        )

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.parseColor("#99FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(Color.parseColor("#33FFFFFF"), 22)
            setPadding(dp(20), dp(13), dp(20), dp(13))
        }
        drawer.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(14)
            }
        )

        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(20)
            setPadding(0, dp(8), 0, dp(16))
            clipToPadding = false
            clipChildren = false
        }
        adapter = AppAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ -> launch(shown[position]) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            showAppMenu(shown[position])
            true
        }

        var pressedView: View? = null
        grid.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val pos = grid.pointToPosition(ev.x.toInt(), ev.y.toInt())
                    if (pos >= 0) {
                        pressedView = grid.getChildAt(pos - grid.firstVisiblePosition)
                        focusIn(pressedView)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    focusOut(pressedView)
                    pressedView = null
                }
            }
            false
        }
        drawer.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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

        root.addView(home, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(drawer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        registerReceiver(
            pkgReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        )
        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        closeDrawer(false)
        search.setText("")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer(true)
    }

    override fun onBackPressed() {
        if (drawerOpen) closeDrawer(true)
        // otherwise a launcher stays on screen when Back is pressed
    }

    // ---------- DRAWER OPEN / CLOSE ----------
    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawer.animate().cancel()
        drawer.visibility = View.VISIBLE
        drawer.translationY = resources.displayMetrics.heightPixels.toFloat()
        drawer.animate().translationY(0f).setDuration(220).start()
    }

    private fun closeDrawer(animate: Boolean) {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        search.setText("")
        if (animate) {
            drawer.animate()
                .translationY(resources.displayMetrics.heightPixels.toFloat())
                .setDuration(180)
                .withEndAction { if (!drawerOpen) drawer.visibility = View.GONE }
                .start()
        } else {
            drawer.animate().cancel()
            drawer.visibility = View.GONE
            drawer.translationY = 0f
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun expandNotifications() {
        try {
            val sbs = getSystemService("statusbar")
            sbs?.let { it.javaClass.getMethod("expandNotificationsPanel").invoke(it) }
        } catch (e: Exception) {
            // not supported on this phone: ignore
        }
    }

    // ---------- DATA ----------
    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        shown.clear()
        for (a in allApps) {
            if (a.packageName in hidden) continue
            if (q.isEmpty() || a.label.lowercase().contains(q)) shown.add(a)
        }
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
                val installed = list.map { it.packageName }.toSet()
                dockPkgs.retainAll(installed)
                allApps.clear()
                allApps.addAll(list)
                saveSettings()
                refreshDock()
                applyFilter(search.text.toString())
            }
        }.start()
    }

    // ---------- DOCK ----------
    private fun refreshDock() {
        dock.removeAllViews()
        if (allApps.isEmpty()) return
        val items = dockPkgs.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }
        if (items.isEmpty()) {
            dock.addView(
                TextView(this).apply {
                    text = "Long-press an app in the list to add it here"
                    setTextColor(Color.parseColor("#AAFFFFFF"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
            )
            return
        }
        for (item in items) {
            val iv = ImageView(this).apply {
                setImageDrawable(item.icon.constantState?.newDrawable(resources) ?: item.icon)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { launch(item) }
                setOnLongClickListener {
                    showDockMenu(item)
                    true
                }
                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> focusIn(v)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> focusOut(v)
                    }
                    false
                }
            }
            dock.addView(iv, LinearLayout.LayoutParams(0, dp(60), 1f))
        }
    }

    private fun addToDock(item: AppItem) {
        if (dockPkgs.size >= 5) {
            toast("Dock is full (5 apps max)")
            return
        }
        dockPkgs.add(item.packageName)
        saveSettings()
        refreshDock()
        toast("Added to dock")
    }

    private fun removeFromDock(item: AppItem) {
        dockPkgs.remove(item.packageName)
        saveSettings()
        refreshDock()
    }

    private fun hideApp(item: AppItem) {
        hidden.add(item.packageName)
        dockPkgs.remove(item.packageName)
        saveSettings()
        refreshDock()
        applyFilter(search.text.toString())
        toast("Hidden. Long-press the home screen to unhide")
    }

    private fun openAppInfo(item: AppItem) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.packageName))
            )
        } catch (e: Exception) {
            toast("Can't open app info")
        }
    }

    // ---------- MENUS ----------
    private fun dialog(title: String, options: Array<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(options) { _, which -> onPick(which) }
            .show()
    }

    private fun showAppMenu(item: AppItem) {
        val docked = dockPkgs.contains(item.packageName)
        val options = arrayOf(if (docked) "Remove from dock" else "Add to dock", "Hide app", "App info")
        dialog(item.label, options) { which ->
            when (which) {
                0 -> if (docked) removeFromDock(item) else addToDock(item)
                1 -> hideApp(item)
                2 -> openAppInfo(item)
                else -> {}
            }
        }
    }

    private fun showDockMenu(item: AppItem) {
        dialog(item.label, arrayOf("Remove from dock", "App info")) { which ->
            when (which) {
                0 -> removeFromDock(item)
                1 -> openAppInfo(item)
                else -> {}
            }
        }
    }

    private fun showHomeMenu() {
        dialog("Launcher", arrayOf("Hidden apps", "Change wallpaper")) { which ->
            when (which) {
                0 -> showHiddenApps()
                1 -> try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                } catch (e: Exception) {
                    toast("Can't open wallpaper picker")
                }
                else -> {}
            }
        }
    }

    private fun showHiddenApps() {
        val list = allApps.filter { it.packageName in hidden }
        if (list.isEmpty()) {
            toast("No hidden apps")
            return
        }
        dialog("Tap an app to unhide", list.map { it.label }.toTypedArray()) { which ->
            hidden.remove(list[which].packageName)
            saveSettings()
            applyFilter(search.text.toString())
        }
    }

    // ---------- GRID ADAPTER ----------
    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(66), dp(66))
                    background = rounded(Color.parseColor("#2B2E36"), 17)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                    elevation = dp(6).toFloat()
                })
                addView(
                    TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        maxLines = 1
                        gravity = Gravity.CENTER
                        ellipsize = TextUtils.TruncateAt.END
                        setShadowLayer(6f, 0f, 1f, Color.parseColor("#80000000"))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                )
            }
            row.scaleX = 1f
            row.scaleY = 1f
            val item = shown[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(item.icon)
            (row.getChildAt(1) as TextView).text = item.label
            return row
        }
    }
}
