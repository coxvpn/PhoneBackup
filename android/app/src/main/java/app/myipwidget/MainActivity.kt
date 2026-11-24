package app.myipwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private companion object {
        const val SOURCE_URL = "https://github.com/coxvpn/Mostafa-MultiWidget"
    }

    private lateinit var flagView: TextView
    private lateinit var countryView: TextView
    private lateinit var ipView: TextView
    private lateinit var detailView: TextView
    private lateinit var refreshButton: Button
    private lateinit var weatherNow: TextView
    private lateinit var usageNow: TextView
    private lateinit var cityName: TextView
    private lateinit var checkWeather: CheckBox
    private lateinit var checkUsage: CheckBox
    private lateinit var grantUsage: Button
    private lateinit var checkSpeedNotice: CheckBox
    private lateinit var checkDate: CheckBox
    private lateinit var dateNow: TextView
    private lateinit var checkFlagNotice: CheckBox
    private var currentIp: String = ""

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Prefs.localized(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        flagView = findViewById(R.id.flag)
        countryView = findViewById(R.id.country)
        ipView = findViewById(R.id.ip)
        detailView = findViewById(R.id.detail)
        weatherNow = findViewById(R.id.weather_now)
        usageNow = findViewById(R.id.usage_now)
        cityName = findViewById(R.id.city_name)
        checkWeather = findViewById(R.id.check_weather)
        checkUsage = findViewById(R.id.check_usage)
        grantUsage = findViewById(R.id.grant_usage)

        refreshButton = findViewById(R.id.refresh)
        refreshButton.setOnClickListener { refresh() }
        ipView.setOnClickListener { copyIp() }

        checkWeather.isChecked = Prefs.showWeather(this)
        checkWeather.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowWeather(this, checked)
            updateExtras()
        }
        checkUsage.isChecked = Prefs.showUsage(this)
        checkUsage.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowUsage(this, checked)
            updateExtras()
        }
        findViewById<CheckBox>(R.id.check_rate).apply {
            isChecked = Prefs.showRate(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setShowRate(this@MainActivity, checked)
                updateExtras(0L)
            }
        }
        dateNow = findViewById(R.id.date_now)
        checkDate = findViewById(R.id.check_date)
        checkDate.isChecked = Prefs.showDate(this)
        checkDate.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowDate(this, checked)
            updateExtras()
        }
        findViewById<CheckBox>(R.id.check_aqi).apply {
            isChecked = Prefs.showAqi(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setShowAqi(this@MainActivity, checked)
                updateExtras(0L)
            }
        }
        findViewById<CheckBox>(R.id.check_prayer).apply {
            isChecked = Prefs.showPrayer(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setShowPrayer(this@MainActivity, checked)
                if (checked && !Prefs.hasCity(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, R.string.no_city_for_azan, Toast.LENGTH_LONG).show()
                }
                updateExtras()
            }
        }
        // what the notification text holds
        findViewById<CheckBox>(R.id.check_notice_temp).apply {
            isChecked = Prefs.noticeTemp(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.touchNoticeOrder(this@MainActivity, "temp", checked)
                Prefs.setNoticeTemp(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<CheckBox>(R.id.check_notice_date).apply {
            isChecked = Prefs.noticeDate(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.touchNoticeOrder(this@MainActivity, "date", checked)
                Prefs.setNoticeDate(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<CheckBox>(R.id.check_notice_rate).apply {
            isChecked = Prefs.noticeRate(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.touchNoticeOrder(this@MainActivity, "rate", checked)
                Prefs.setNoticeRate(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<CheckBox>(R.id.check_notice_usage).apply {
            isChecked = Prefs.noticeUsage(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.touchNoticeOrder(this@MainActivity, "usage", checked)
                Prefs.setNoticeUsage(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<CheckBox>(R.id.check_notice_prayer).apply {
            isChecked = Prefs.noticePrayer(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.touchNoticeOrder(this@MainActivity, "prayer", checked)
                Prefs.setNoticePrayer(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<Button>(R.id.choose_theme).apply {
            text = getString(R.string.theme_button, themeName(Prefs.theme(this@MainActivity)))
            setOnClickListener { askTheme(this) }
        }
        findViewById<Button>(R.id.choose_layout).apply {
            text = getString(R.string.layout_button, layoutName(Prefs.layout(this@MainActivity)))
            setOnClickListener { askLayout(this) }
        }
        findViewById<Button>(R.id.choose_language).apply {
            text = getString(R.string.language_button, languageName(Prefs.language(this@MainActivity)))
            setOnClickListener { askLanguage() }
        }
        // the choices for the notification bar are under a drop-down
        val noticeBody = findViewById<View>(R.id.notice_body)
        val noticeToggle = findViewById<TextView>(R.id.notice_toggle)
        // the choices stand open by default
        noticeBody.visibility = View.VISIBLE
        noticeToggle.text = getString(R.string.notice_toggle_open)
        noticeToggle.setOnClickListener {
            val open = noticeBody.visibility != View.VISIBLE
            noticeBody.visibility = if (open) View.VISIBLE else View.GONE
            noticeToggle.text = getString(if (open) R.string.notice_toggle_open else R.string.notice_toggle_closed)
        }
        findViewById<Button>(R.id.check_update).setOnClickListener { checkUpdate() }
        // once a week, when the app is opened: a quiet look for a new version (no pop-up, only the banner)
        if (Prefs.firstRunDone(this) && !firstRun) quietUpdateCheck()
        buildBeepSlots()
        setupAlertsAndTools()
        colorizeCards()
        // the flag and IP, or the clock, in the widget and on the first page: exactly one is ticked
        val tickIp = findViewById<CheckBox>(R.id.check_show_ip)
        val tickClock = findViewById<CheckBox>(R.id.check_show_clock)
        fun chooseClock(clock: Boolean) {
            Prefs.setShowClock(this, clock)
            tickIp.setOnCheckedChangeListener(null); tickClock.setOnCheckedChangeListener(null)
            tickIp.isChecked = !clock; tickClock.isChecked = clock
            tickIp.setOnCheckedChangeListener { _, _ -> chooseClock(false) }
            tickClock.setOnCheckedChangeListener { _, _ -> chooseClock(true) }
            showClockMode()
            IpWidget.redrawFromCache(applicationContext)
        }
        chooseClock(Prefs.showClock(this))
        val beepTimes = findViewById<View>(R.id.beep_container)
        val beepToggle = findViewById<TextView>(R.id.beep_times_toggle)
        beepToggle.text = getString(R.string.beep_times_closed)
        beepToggle.setOnClickListener {
            val open = beepTimes.visibility != View.VISIBLE
            beepTimes.visibility = if (open) View.VISIBLE else View.GONE
            beepToggle.text = getString(if (open) R.string.beep_times_open else R.string.beep_times_closed)
        }
        // the main page shows the data; the three lines at the top open and close the settings page
        val dataPage = findViewById<View>(R.id.data_page)
        val settingsPage = findViewById<View>(R.id.settings_page)
        val menu = findViewById<TextView>(R.id.menu_button)
        val title = findViewById<TextView>(R.id.page_title)
        fun showSettings(on: Boolean) {
            settingsPage.visibility = if (on) View.VISIBLE else View.GONE
            dataPage.visibility = if (on) View.GONE else View.VISIBLE
            menu.text = if (on) "\u2715" else "\u2630"
            title.text = getString(if (on) R.string.settings_title else R.string.app_name)
        }
        menu.setOnClickListener { showSettings(settingsPage.visibility != View.VISIBLE) }
        backToData = { if (settingsPage.visibility == View.VISIBLE) { showSettings(false); true } else false }
        val beepBody = findViewById<View>(R.id.beep_body)
        findViewById<CheckBox>(R.id.check_beep_enabled).apply {
            isChecked = Prefs.beepEnabled(this@MainActivity)
            beepBody.visibility = if (isChecked) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, checked ->
                Prefs.setBeepEnabled(this@MainActivity, checked)
                beepBody.visibility = if (checked) View.VISIBLE else View.GONE
                Beeper.schedule(this@MainActivity)
            }
        }
        for ((id, key) in listOf(R.id.check_azan_fajr to "azan_fajr", R.id.check_azan_dhuhr to "azan_dhuhr", R.id.check_azan_maghrib to "azan_maghrib")) {
            findViewById<CheckBox>(id).apply {
                isChecked = Prefs.azanSound(this@MainActivity, key)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setAzanSound(this@MainActivity, key, checked)
                    Beeper.scheduleAzan(this@MainActivity)
                }
            }
        }
        Beeper.schedule(this)
        Beeper.scheduleAzan(this)
        // the first time: the notification is on, and the phone asks for the notification permission
        if (!Prefs.firstRunDone(this)) {
            Prefs.setFirstRunDone(this)
            Prefs.setNoticeEnabled(this, true)
            Prefs.setShowSpeedNotice(this, true)
            firstRun = true
            noticeChanged(Prefs.noticeEnabled(this))
            // no permission question was needed: go on at once; otherwise after the answer
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Handler(Looper.getMainLooper()).post { firstRunUsage() }
            }
        } else {
            SpeedService.sync(this)
        }
        findViewById<Button>(R.id.choose_beep_sound).apply {
            text = getString(R.string.beep_sound_button, soundName(Prefs.beepSound(this@MainActivity)))
            setOnClickListener { askSound(this) }
        }
        findViewById<Button>(R.id.about).setOnClickListener { showAbout() }
        findViewById<Button>(R.id.add_widget).setOnClickListener { addWidget() }
        findViewById<CheckBox>(R.id.check_compact).apply {
            isChecked = Prefs.compactNotice(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setCompactNotice(this@MainActivity, checked)
                SpeedService.sync(this@MainActivity)
            }
        }
        findViewById<Button>(R.id.notice_settings).setOnClickListener { openNoticeSettings() }
        // master switch: when off, the notification bar shows nothing and the other choices are greyed out
        findViewById<CheckBox>(R.id.check_notice_enabled).apply {
            isChecked = Prefs.noticeEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setNoticeEnabled(this@MainActivity, checked)
                noticeDependants(checked)
                noticeChanged(checked)
            }
        }
        noticeDependants(Prefs.noticeEnabled(this))
        checkSpeedNotice = findViewById(R.id.check_speed_notice)
        checkFlagNotice = findViewById(R.id.check_flag_notice)
        checkSpeedNotice.isChecked = Prefs.showSpeedNotice(this)
        checkSpeedNotice.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowSpeedNotice(this, checked)
            noticeChanged(checked)
        }
        checkFlagNotice.isChecked = Prefs.showFlagNotice(this)
        checkFlagNotice.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowFlagNotice(this, checked)
            noticeChanged(checked)
        }
        findViewById<Button>(R.id.choose_city).setOnClickListener { askCity() }
        grantUsage.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                // no such settings page on this phone
            }
        }

        val cached = IpLookup.load(this)
        show(if (cached != null) IpLookup.describe(this, cached) else IpLookup.checking(this))
        showExtras()
        refresh()
        findViewById<Button>(R.id.battery).setOnClickListener { requestUnrestricted() }
        askUnrestrictedOnce()
    }

    private fun soundName(index: Int): String =
        if (index >= 20) getString(R.string.sound_custom, Prefs.beepCustomName(this).ifEmpty { "-" })
        else resources.getStringArray(R.array.beep_sound_names)[index]

    /** A list of short melodies; each one is played once when it is touched. */
    private var soundButton: Button? = null

    private fun askSound(button: Button) {
        soundButton = button
        var chosen = Prefs.beepSound(this)
        val items = resources.getStringArray(R.array.beep_sound_names) + getString(R.string.sound_from_phone)
        AlertDialog.Builder(this)
            .setTitle(R.string.beep_sound_title)
            .setSingleChoiceItems(items, chosen) { dialog, which ->
                if (which >= 20) {
                    // a sound file from the phone
                    dialog.dismiss()
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    try { startActivityForResult(pick, 7) } catch (e: Exception) { }
                } else {
                    chosen = which
                    Thread { Beeper.play(applicationContext, which) }.start()
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (chosen < 20) {
                    Prefs.setBeepSound(this, chosen)
                    button.text = getString(R.string.beep_sound_button, soundName(chosen))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == 31 || requestCode == 32) && resultCode == RESULT_OK) { backupResult(requestCode, data?.data); return }
        if (requestCode != 7 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        var name = uri.lastPathSegment ?: ""
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (e: Exception) { }
        Prefs.setBeepCustom(this, uri.toString(), name)
        Prefs.setBeepSound(this, 20)
        soundButton?.text = getString(R.string.beep_sound_button, soundName(20))
        Thread { Beeper.play(applicationContext, 20) }.start()
    }

    private fun languageName(code: String): String = if (code == "en") "English" else "فارسی"

    private fun askLanguage() {
        val codes = arrayOf("fa", "en")
        val current = codes.indexOf(Prefs.language(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(arrayOf("فارسی", "English"), current) { dialog, which ->
                dialog.dismiss()
                if (codes[which] != Prefs.language(this)) {
                    Prefs.setLanguage(this, codes[which])
                    recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Five beep times, each with an on switch, a time button and seven weekday chips (Saturday first). */
    private fun buildBeepSlots() {
        val box = findViewById<LinearLayout>(R.id.beep_container)
        box.removeAllViews()
        val dayNames = resources.getStringArray(R.array.day_short)
        // Calendar-style bit (0 = Sunday ... 6 = Saturday) for the chips in the order Saturday .. Friday
        val bits = intArrayOf(6, 0, 1, 2, 3, 4, 5)
        for (slot in 0 until Beeper.SLOTS) {
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val on = CheckBox(this).apply {
                text = getString(R.string.beep_slot, slot + 1)
                isChecked = Prefs.beepOn(this@MainActivity, slot)
            }
            val time = Button(this)
            fun showTime() {
                time.text = "%02d:%02d".format(java.util.Locale.US, Prefs.beepHour(this, slot), Prefs.beepMinute(this, slot))
            }
            showTime()
            on.setOnCheckedChangeListener { _, checked ->
                Prefs.setBeepOn(this, slot, checked)
                Beeper.schedule(this)
            }
            time.setOnClickListener {
                TimePickerDialog(this, { _, h, m ->
                    Prefs.setBeepTime(this, slot, h, m)
                    showTime()
                    Beeper.schedule(this)
                }, Prefs.beepHour(this, slot), Prefs.beepMinute(this, slot), true).show()
            }
            top.addView(on, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(time)
            box.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

            val days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 7) {
                val bit = 1 shl bits[i]
                val chip = TextView(this).apply {
                    text = dayNames[i]
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setPadding(0, dp(8), 0, dp(8))
                }
                fun paint() {
                    val selected = Prefs.beepDays(this@MainActivity, slot) and bit != 0
                    chip.background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(if (selected) Color.parseColor("#3D6FE0") else Color.parseColor("#33888888"))
                    }
                    chip.setTextColor(if (selected) Color.WHITE else Color.GRAY)
                }
                paint()
                chip.setOnClickListener {
                    Prefs.setBeepDays(this, slot, Prefs.beepDays(this, slot) xor bit)
                    paint()
                    Beeper.schedule(this)
                }
                days.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                })
            }
            box.addView(days, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        }
    }

    private fun layoutName(index: Int): String = resources.getStringArray(R.array.layout_names)[index]

    /** The five widget layouts: the original and the four coloured ones. The widget changes at once. */
    private fun askLayout(button: Button) {
        AlertDialog.Builder(this)
            .setTitle(R.string.layout_title)
            .setSingleChoiceItems(resources.getStringArray(R.array.layout_names), Prefs.layout(this)) { dialog, which ->
                Prefs.setLayout(this, which)
                button.text = getString(R.string.layout_button, layoutName(which))
                IpWidget.redrawFromCache(applicationContext)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun themeName(index: Int): String = resources.getStringArray(R.array.theme_names)[index]

    /** A list of looks for the widget background; the widget changes at once. */
    private fun askTheme(button: Button) {
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(resources.getStringArray(R.array.theme_names), Prefs.theme(this)) { dialog, which ->
                Prefs.setTheme(this, which)
                button.text = getString(R.string.theme_button, themeName(which))
                IpWidget.redrawFromCache(applicationContext)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true

    /** The phone's own question "let this app run without battery limits?" (otherwise it may be put to sleep). */
    private fun requestUnrestricted() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName")),
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                // no such settings page on this phone
            }
        }
    }

    /** Right after the first start: explain, then show the phone's question. Asked only once. */
    private fun askUnrestrictedOnce() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (isUnrestricted() || prefs.getBoolean("battery_asked", false)) return
        prefs.edit().putBoolean("battery_asked", true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_title)
            .setMessage(R.string.battery_message)
            .setPositiveButton(R.string.battery_allow) { _, _ -> requestUnrestricted() }
            .setNegativeButton(R.string.battery_later, null)
            .show()
    }

    private var backToData: () -> Boolean = { false }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!backToData()) super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // coming back from the usage-access settings page: use the new permission
        if (waitingUsage) {
            waitingUsage = false
            Handler(Looper.getMainLooper()).post { firstRunLocation() }
        }
        showExtras()
        updateExtras()
        showClockMode()
        loadChart()
        loadTopApps()
        chargeHandler.removeCallbacks(chargeTick)
        chargeHandler.post(chargeTick)
    }

    override fun onPause() {
        super.onPause()
        chargeHandler.removeCallbacks(chargeTick)
    }

    // ---------------------------------------------------------------- the charging box: refreshed every 2 seconds while the app is open
    private val chargeHandler = Handler(Looper.getMainLooper())
    private val chargeTick = object : Runnable {
        override fun run() {
            showCharge()
            chargeHandler.postDelayed(this, 2000L)
        }
    }

    private fun showCharge() {
        val box = findViewById<TextView>(R.id.charge_now)
        val info = ChargeInfo.read(this)
        if (info == null) { box.visibility = View.GONE; return }
        box.visibility = View.VISIBLE
        val lines = ArrayList<String>()
        val temp = String.format(java.util.Locale.US, "%.1f", info.tempTenths / 10.0)
        if (info.charging) {
            val source = getString(when (info.source) { 3 -> R.string.charge_wireless; 2 -> R.string.charge_usb; else -> R.string.charge_cable })
            lines.add(getString(R.string.charge_on, source, info.level))
            if (info.milliAmps > 0) {
                lines.add(getString(R.string.charge_power, String.format(java.util.Locale.US, "%.1f", info.watts)))
                lines.add(getString(R.string.charge_current, info.milliAmps))
            } else {
                lines.add(getString(R.string.charge_unknown))
            }
            if (info.millivolts > 0) lines.add(getString(R.string.charge_voltage, String.format(java.util.Locale.US, "%.2f", info.millivolts / 1000.0)))
            if (info.minutesLeft > 0) {
                lines.add(getString(R.string.charge_left, info.minutesLeft / 60, info.minutesLeft % 60))
            }
        } else if (info.full && info.source != 0) {
            lines.add(getString(R.string.charge_full, info.level))
        } else {
            lines.add(getString(R.string.charge_off, info.level))
        }
        lines.add(getString(R.string.charge_temp, temp))
        box.text = lines.joinToString("\n")
    }

    /** The seven-day chart: the system's numbers are read off the main thread. */
    // ---------------------------------------------------------------- alerts, tools, backup, top apps
    private fun setupAlertsAndTools() {
        val charge = findViewById<CheckBox>(R.id.check_charge_alert)
        val rain = findViewById<CheckBox>(R.id.check_rain_alert)
        charge.isChecked = Prefs.chargeAlert(this)
        rain.isChecked = Prefs.rainAlert(this)
        charge.setOnCheckedChangeListener { _, on -> Prefs.setChargeAlert(this, on) }
        rain.setOnCheckedChangeListener { _, on -> Prefs.setRainAlert(this, on) }
        val volume = findViewById<android.widget.SeekBar>(R.id.azan_volume)
        volume.progress = Prefs.azanVolume(this)
        volume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) Prefs.setAzanVolume(this@MainActivity, value)
            }
            override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
            // let go of the slider: the sound plays once with the new loudness
            override fun onStopTrackingTouch(bar: android.widget.SeekBar) {
                Thread { Beeper.playAzanRecording(applicationContext) }.start()
            }
        })
        findViewById<Button>(R.id.azan_test).setOnClickListener {
            Thread {
                val ok = Beeper.playAzanRecording(applicationContext) || Beeper.sayAllahuAkbar(applicationContext)
                if (!ok) runOnUiThread { Toast.makeText(this, R.string.azan_test_failed, Toast.LENGTH_LONG).show() }
            }.start()
        }
        findViewById<Button>(R.id.backup_save).setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "Mostafa-MultiWidget-backup.json")
            }
            try { startActivityForResult(intent, 31) } catch (e: Exception) { }
        }
        findViewById<Button>(R.id.backup_load).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try { startActivityForResult(intent, 32) } catch (e: Exception) { }
        }
    }

    private fun backupResult(requestCode: Int, uri: android.net.Uri?) {
        uri ?: return
        try {
            if (requestCode == 31) {
                contentResolver.openOutputStream(uri)?.use { it.write(Prefs.exportJson(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_LONG).show()
            } else {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                if (Prefs.importJson(this, text)) {
                    Toast.makeText(this, R.string.backup_loaded, Toast.LENGTH_LONG).show()
                    SpeedService.sync(this)
                    Beeper.schedule(this)
                    Beeper.scheduleAzan(this)
                    IpWidget.redrawFromCache(applicationContext)
                    recreate()
                } else {
                    Toast.makeText(this, R.string.backup_bad, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.backup_bad, Toast.LENGTH_LONG).show()
        }
    }

    /** The five apps that used the most data today (needs the usage access). */
    private fun loadTopApps() {
        val box = findViewById<TextView>(R.id.top_apps)
        Thread {
            val list = Usage.topApps(applicationContext, 5)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                box.text = if (list.isEmpty()) getString(R.string.top_apps_none)
                else list.joinToString("\n") { "\u2068" + it.first + "\u2069  \u00B7  " + Formatter.formatShortFileSize(applicationContext, it.second) }
            }
        }.start()
    }

    private fun loadChart() {
        val chart = findViewById<UsageChart>(R.id.usage_chart)
        val legend = findViewById<TextView>(R.id.chart_legend)
        chart.textColor = legend.currentTextColor
        val days = resources.getStringArray(R.array.day_short)
        Thread {
            val data = Usage.history(applicationContext, 7)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (data.isEmpty()) {
                    chart.data = emptyList()
                    legend.text = getString(R.string.usage_no_access)
                } else {
                    // Calendar: Saturday = 7 -> index 0 of the day names (Saturday first)
                    val labels = (data.size - 1 downTo 0).map { back ->
                        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -back) }
                        days[c.get(java.util.Calendar.DAY_OF_WEEK) % 7]
                    }
                    chart.labels = labels
                    chart.data = data
                    val sum = data.sumOf { maxOf(0L, it.wifi) + maxOf(0L, it.mobile) }
                    legend.textDirection = View.TEXT_DIRECTION_LTR
                    legend.text = "Wi-Fi (blue)   SIM (pink)\n7 days total: " + Usage.englishSize(sum)
                }
            }
        }.start()
    }

    /** The choices under the master switch can only be changed while it is on. */
    private fun noticeDependants(enabled: Boolean) {
        for (id in intArrayOf(
            R.id.check_speed_notice, R.id.check_flag_notice, R.id.check_compact, R.id.check_notice_temp,
            R.id.check_notice_date, R.id.check_notice_rate, R.id.check_notice_usage, R.id.check_notice_prayer,
        )) {
            findViewById<View>(id).isEnabled = enabled
        }
    }

    /** Starts / stops the notifications; from Android 13 the notification permission is needed first. */
    private fun noticeChanged(turnedOn: Boolean) {
        if (turnedOn && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }
        SpeedService.sync(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 6) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) useGps()
            else {
                Toast.makeText(this, R.string.gps_denied, Toast.LENGTH_LONG).show()
                firstRunPin()
            }
            return
        }
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.notice_denied, Toast.LENGTH_LONG).show()
        }
        SpeedService.sync(this)
        if (requestCode == 5) firstRunUsage()
    }

    /** Asks the home screen to add the widget (the phone shows its own "add" question). */
    private fun addWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, IpWidget::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    /** The phone's own page for the speed notification: there it can be set to "silent" or collapsed. */
    private fun openNoticeSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, SpeedService.CHANNEL),
            )
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            } catch (e2: Exception) {
                // no such settings page on this phone
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_text, version, SOURCE_URL))
            .setPositiveButton(R.string.source_code) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_URL)))
                } catch (e: Exception) {
                    // no browser installed
                }
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun show(display: Display) {
        currentIp = display.ip
        flagView.text = display.flag
        countryView.text = display.title
        ipView.text = display.ip
        detailView.text = display.detail
    }

    /** Fills in the weather, today's volume, the city name and the permission button from what is saved. */
    private fun showExtras() {
        val rateView = findViewById<TextView>(R.id.rate_now)
        rateView.text = getString(R.string.rate_line, Rate.format(Rate.cached(this)))
        rateView.visibility = if (Prefs.showRate(this)) View.VISIBLE else View.GONE
        val now = java.util.Calendar.getInstance()
        dateNow.text = PersianDate.full(now).substringBefore("\n")
        findViewById<TextView>(R.id.greg_now).text = PersianDate.gregorian(now)
        val occasion = Occasions.today()
        findViewById<TextView>(R.id.occasion_bar).apply {
            if (occasion == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = Occasions.label(occasion)
                setBackgroundResource(if (occasion.holiday) R.drawable.chip_red else R.drawable.chip_purple)
            }
        }
        findViewById<View>(R.id.tile_date).visibility = if (Prefs.showDate(this)) View.VISIBLE else View.GONE

        // the prayer call that is next
        val azan = if (Prefs.showPrayer(this)) Prayer.next(this) else null
        findViewById<TextView>(R.id.azan_now).apply {
            if (azan == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = getString(R.string.azan_word) + " " + getString(azan.nameRes) + " " + Prayer.clockText(azan.time) + "\n" +
                    Prayer.remainingText(azan.time - System.currentTimeMillis()) + " " + getString(R.string.azan_left)
            }
        }

        // the weather tile: big temperature, smaller high / low, air quality and the city
        val weather = Weather.cached(this)
        val tile = findViewById<View>(R.id.tile_weather)
        if (Prefs.hasCity(this) && weather != null) {
            tile.visibility = View.VISIBLE
            val night = now.get(java.util.Calendar.HOUR_OF_DAY).let { it < 6 || it >= 18 }
            findViewById<TextView>(R.id.sun_now).text = if (night && weather.code == 0) "\uD83C\uDF19" else Weather.symbol(weather.code)
            weatherNow.text = Math.round(weather.temp).toString() + "\u00B0"
            findViewById<TextView>(R.id.range_now).text = Weather.range(weather)
            findViewById<TextView>(R.id.aqi_now).apply {
                if (weather.aqi >= 0) {
                    val level = Weather.aqiLevel(weather.aqi)
                    visibility = View.VISIBLE
                    text = "AQI " + weather.aqi
                    setBackgroundResource(intArrayOf(R.drawable.aqi_bg_0, R.drawable.aqi_bg_1, R.drawable.aqi_bg_2, R.drawable.aqi_bg_3, R.drawable.aqi_bg_4, R.drawable.aqi_bg_5)[level.coerceIn(0, 5)])
                    setTextColor(if (level == 1 || level == 2) 0xFF222222.toInt() else 0xFFFFFFFF.toInt())
                } else {
                    visibility = View.GONE
                }
            }
            findViewById<TextView>(R.id.city_now).text = "\u2068" + Prefs.cityLabel(this) + "\u2069"
        } else {
            tile.visibility = View.GONE
        }

        cityName.text = if (Prefs.hasCity(this)) Prefs.cityName(this) else getString(R.string.no_city)

        val hasAccess = Usage.hasAccess(this)
        grantUsage.visibility = if (hasAccess) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.battery).visibility = if (isUnrestricted()) View.GONE else View.VISIBLE
        val used = Usage.cached(this)
        usageNow.text = when {
            !hasAccess -> getString(R.string.usage_no_access)
            used != null -> Usage.englishLine(used)
            else -> ""
        }
        usageNow.visibility = if (usageNow.text.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Looks up the weather / volume in the background, then redraws this screen and the widget. */
    private fun updateExtras(weatherMaxAgeMs: Long = 2 * 60 * 1000L) {
        val app = applicationContext
        Thread {
            try {
                Weather.refreshIfStale(app, weatherMaxAgeMs)
                Rate.refreshIfStale(app, weatherMaxAgeMs)
                Usage.update(app)
            } catch (e: Exception) {
                // optional extras
            }
            IpWidget.redrawFromCache(app)
            runOnUiThread { if (!isFinishing) showExtras() }
        }.start()
    }

    private fun refresh() {
        val app = applicationContext
        refreshButton.isEnabled = false
        Thread {
            val info = if (NetWatch.isOnline(app)) IpLookup.fetch() else null
            if (info != null) IpLookup.save(app, info)
            val display = if (info != null) IpLookup.describe(app, info) else IpLookup.offline(app)
            runOnUiThread {
                if (!isFinishing) {
                    show(display)
                    refreshButton.isEnabled = true
                }
            }
            IpWidget.render(app, display)
            updateExtras()
        }.start()
    }

    // ---------------------------------------------------------------- the city
    // ---------------------------------------------------------------- the first run, one question after the other:
    // usage access, then the position (the city comes from GPS), then the widget on the home screen
    private var firstRun = false
    private var waitingUsage = false

    private fun firstRunUsage() {
        if (!firstRun) return
        if (Usage.hasAccess(this)) { firstRunLocation(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.first_usage_title)
            .setMessage(R.string.first_usage_message)
            .setCancelable(false)
            .setPositiveButton(R.string.first_usage_allow) { _, _ ->
                waitingUsage = true
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { waitingUsage = false; firstRunLocation() }
            }
            .setNegativeButton(R.string.battery_later) { _, _ -> firstRunLocation() }
            .show()
    }

    private fun firstRunLocation() {
        if (!firstRun) return
        useGps()
    }

    /** The end of the first-run questions (also when the position could not be found): the widget is offered. */
    private fun firstRunPin() {
        if (!firstRun) return
        // without a city there is no weather and no prayer time: Tehran until the person chooses another
        if (!Prefs.hasCity(this)) {
            Prefs.setCity(this, "\u062A\u0647\u0631\u0627\u0646", 35.6892, 51.3890)
            Prefs.setShowWeather(this, true)
            checkWeather.isChecked = true
            showExtras()
            updateExtras(0L)
            Toast.makeText(this, R.string.city_default_tehran, Toast.LENGTH_LONG).show()
        }
        firstRun = false
        addWidget()
    }

    private fun askCity() {
        val input = EditText(this)
        input.hint = getString(R.string.city_hint)
        input.setSingleLine()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_city)
            .setView(input)
            .setPositiveButton(R.string.search) { _, _ -> searchCity(input.text.toString()) }
            .setNeutralButton(R.string.use_gps) { _, _ -> useGps() }
            .show()
    }

    /** The city from the phone's own position (GPS or network); asks for the location permission first. */
    @Suppress("DEPRECATION")
    private fun useGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 6)
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
            try { manager.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.gps_off, Toast.LENGTH_LONG).show()
            firstRunPin()
            return
        }
        Toast.makeText(this, R.string.gps_waiting, Toast.LENGTH_LONG).show()
        val handler = Handler(Looper.getMainLooper())
        var done = false
        lateinit var listener: LocationListener
        fun finish(location: Location?) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            try { manager.removeUpdates(listener) } catch (e: Exception) { }
            if (location == null) { Toast.makeText(this, R.string.gps_failed, Toast.LENGTH_LONG).show(); firstRunPin() }
            else gotLocation(location)
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location)
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            for (p in providers) manager.requestSingleUpdate(p, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            finish(null)
            return
        }
        // after 25 seconds, take the last position the phone knew
        handler.postDelayed({
            val last = try {
                providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            } catch (e: Exception) { null }
            finish(last)
        }, 25000L)
    }

    /** Looks for the city's name (needs the phone's map service); without it the city is just called "my location". */
    private fun gotLocation(location: Location) {
        Thread {
            var name = ""
            try {
                if (Geocoder.isPresent()) {
                    val found = Geocoder(this, java.util.Locale("fa")).getFromLocation(location.latitude, location.longitude, 1)
                    val a = found?.firstOrNull()
                    name = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
                }
            } catch (e: Exception) {
                // no map service: use the fixed name
            }
            if (name.isEmpty()) {
                try {
                    val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=fa&lat=" +
                        location.latitude + "&lon=" + location.longitude
                    val body = IpLookup.httpGet(url, 20000)
                    if (body != null) {
                        val a = org.json.JSONObject(body).optJSONObject("address")
                        if (a != null) {
                            for (key in arrayOf("city", "town", "village", "county", "state")) {
                                val v = a.optString(key, "")
                                if (v.isNotEmpty()) { name = v; break }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // offline: the fixed name below
                }
            }
            if (name.isEmpty()) {
                // a second map service
                try {
                    val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?localityLanguage=fa&latitude=" +
                        location.latitude + "&longitude=" + location.longitude
                    val body = IpLookup.httpGet(url, 20000)
                    if (body != null) {
                        val o = org.json.JSONObject(body)
                        for (key in arrayOf("city", "locality", "principalSubdivision")) {
                            val v = o.optString(key, "")
                            if (v.isNotEmpty()) { name = v; break }
                        }
                    }
                } catch (e: Exception) {
                    // offline
                }
            }
            // no internet service answered: the closest known city, from the list inside the app
            if (name.isEmpty()) name = NearCity.name(location.latitude, location.longitude)
            if (name.isEmpty()) name = getString(R.string.my_location)
            runOnUiThread {
                if (!isFinishing) chooseCity(City(name, "", "", location.latitude, location.longitude))
            }
        }.start()
    }

    private fun searchCity(query: String) {
        Toast.makeText(this, R.string.searching, Toast.LENGTH_SHORT).show()
        Thread {
            val results = Weather.search(query)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when {
                    results == null -> Toast.makeText(this, R.string.search_failed, Toast.LENGTH_LONG).show()
                    results.isEmpty() -> Toast.makeText(this, R.string.no_city_found, Toast.LENGTH_LONG).show()
                    else -> showCityList(results)
                }
            }
        }.start()
    }

    private fun showCityList(cities: List<City>) {
        val labels = cities.map { c ->
            listOf(c.name, c.region, c.country).filter { it.isNotEmpty() }.joinToString(" - ")
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_city)
            .setItems(labels) { _, index -> chooseCity(cities[index]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseCity(city: City) {
        Prefs.setCity(this, city.name, city.lat, city.lon)
        Prefs.setShowWeather(this, true)
        checkWeather.isChecked = true
        showExtras()
        updateExtras(0L)
        firstRunPin()
    }

    private fun copyIp() {
        if (currentIp.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("IP", currentIp))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** Every card of the two pages has its own colour: tinted frame (set in the layout), title, check boxes and buttons. */
    private fun colorizeCards() {
        val colours = intArrayOf(
            0xFF3D6FE0.toInt(), 0xFF2E9E6B.toInt(), 0xFFF08A1C.toInt(), 0xFF8E54E9.toInt(),
            0xFFE0508F.toInt(), 0xFF1BA3B8.toInt(), 0xFFC99A00.toInt(), 0xFFE05A3A.toInt(),
        )
        val cards = ArrayList<View>()
        for (pageId in intArrayOf(R.id.data_page, R.id.settings_page)) {
            val column = (findViewById<android.view.ViewGroup>(pageId)).getChildAt(0) as? android.view.ViewGroup ?: continue
            for (i in 0 until column.childCount) cards.add(column.getChildAt(i))
        }
        cards.forEachIndexed { index, card ->
            val colour = colours[index % colours.size]
            val tint = android.content.res.ColorStateList.valueOf(colour)
            fun paint(view: View) {
                when (view) {
                    is CheckBox -> view.buttonTintList = tint
                    is Button -> { view.backgroundTintList = tint; view.setTextColor(0xFFFFFFFF.toInt()) }
                    is android.view.ViewGroup -> for (i in 0 until view.childCount) paint(view.getChildAt(i))
                }
            }
            paint(card)
        }
    }

    // ---------------------------------------------------------------- update check
    /** Asks GitHub for the latest release and says whether a newer version exists; the download page opens on request. */
    private fun checkUpdate() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        lookForUpdate(manual = true)
    }

    private class Latest(val tag: String, val page: String, val apkUrl: String)

    private fun mineVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    /**
     * Asks GitHub for the latest release. [manual]: the button was tapped, so every answer is shown;
     * otherwise (the quiet check when the app opens) only a new version shows a banner, nothing else.
     */
    private fun lookForUpdate(manual: Boolean) {
        Thread {
            var latest: Latest? = null
            try {
                val body = IpLookup.httpGet("https://api.github.com/repos/coxvpn/Mostafa-MultiWidget/releases/latest", 400000)
                if (body != null) {
                    val o = org.json.JSONObject(body)
                    var apk = ""
                    val assets = o.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name", "").endsWith(".apk")) { apk = a.optString("browser_download_url", ""); break }
                        }
                    }
                    latest = Latest(o.optString("tag_name", ""), o.optString("html_url", ""), apk)
                }
            } catch (e: Exception) {
                // no answer
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val mine = mineVersion()
                val found = latest
                if (found == null || found.tag.isEmpty()) {
                    if (manual) Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
                } else if (versionNewer(found.tag, mine)) {
                    showUpdateBanner(found, mine)
                    if (manual) showUpdateDialog(found, mine)
                } else {
                    findViewById<TextView>(R.id.update_banner).visibility = View.GONE
                    if (manual) Toast.makeText(this, getString(R.string.update_none, mine), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** A thin line at the top: "a new version is out". It never asks for anything; a tap opens the choices. */
    private fun showUpdateBanner(latest: Latest, mine: String) {
        findViewById<TextView>(R.id.update_banner).apply {
            visibility = View.VISIBLE
            text = getString(R.string.update_banner, latest.tag.trimStart('v', 'V'))
            setOnClickListener { showUpdateDialog(latest, mine) }
        }
    }

    private fun showUpdateDialog(latest: Latest, mine: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(getString(R.string.update_found, latest.tag.trimStart('v', 'V'), mine) + "\n\n" + getString(R.string.update_safe))
            .setPositiveButton(if (latest.apkUrl.isNotEmpty()) R.string.update_install else R.string.update_open) { _, _ ->
                if (latest.apkUrl.isNotEmpty()) downloadAndInstall(latest.apkUrl) else openPage(latest.page)
            }
            .setNeutralButton(R.string.update_open) { _, _ -> openPage(latest.page) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    /** The quiet check when the app opens: at most once a week. */
    private fun quietUpdateCheck() {
        val prefs = getSharedPreferences("update_check", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last", 0L) < 7 * 24 * 3600_000L) return
        prefs.edit().putLong("last", now).apply()
        lookForUpdate(manual = false)
    }

    /** True when version [a] (like "v1.19.0") is higher than [b] (like "1.18.18"). */
    private fun versionNewer(a: String, b: String): Boolean {
        val x = a.trimStart('v', 'V').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.trimStart('v', 'V').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** The first page shows the clock instead of the flag and IP when the clock is chosen. */
    private fun showClockMode() {
        val clock = Prefs.showClock(this)
        for (id in intArrayOf(R.id.ip_row)) {
            findViewById<View>(id).visibility = if (clock) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.clock_main).visibility = if (clock) View.VISIBLE else View.GONE
    }

    private fun openPage(page: String) {
        val url = if (page.isNotEmpty()) page else SOURCE_URL + "/releases/latest"
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (e: Exception) { }
    }

    /** Downloads the new app file (with a progress bar) and gives it to the installer. */
    private fun downloadAndInstall(apkUrl: String) {
        // the phone must allow this app to install apps: a switch on its own settings page
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true }
        val label = TextView(this).apply { text = getString(R.string.update_downloading, 0); setPadding(0, 0, 0, 16) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 8)
            addView(label); addView(bar)
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.update_title).setView(box).setCancelable(false).show()
        Thread {
            val file = Updater.download(applicationContext, apkUrl) { percent ->
                runOnUiThread {
                    if (percent >= 0) {
                        bar.isIndeterminate = false
                        bar.progress = percent
                        label.text = getString(R.string.update_downloading, percent)
                    }
                }
            }
            val started = file != null && Updater.install(applicationContext, file)
            runOnUiThread {
                try { dialog.dismiss() } catch (e: Exception) { }
                if (!started) Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
