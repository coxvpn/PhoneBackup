package com.phonebackup.helper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppsActivity : Activity() {

    private var apps: List<AppEntry> = emptyList()
    private val selected = HashSet<String>()
    private lateinit var adapter: Adapter
    private lateinit var folderText: TextView
    private lateinit var countText: TextView
    private lateinit var resultText: TextView
    private var destTree: Uri? = null

    companion object {
        const val REQ_PICK_FOLDER = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "بکاپ برنامه‌ها"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(32, 32, 32, 16)
        }

        val folderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderText = TextView(this).apply {
            text = "پوشه‌ی مقصد: انتخاب نشده"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pickFolderBtn = Button(this).apply {
            text = "انتخاب پوشه"
            setOnClickListener { pickFolder() }
        }
        folderRow.addView(folderText)
        folderRow.addView(pickFolderBtn)
        root.addView(folderRow)

        val toolbarRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val allBtn = Button(this).apply { text = "انتخاب همه"; setOnClickListener { setAll(true) } }
        val noneBtn = Button(this).apply { text = "هیچکدام"; setOnClickListener { setAll(false) } }
        toolbarRow.addView(allBtn)
        toolbarRow.addView(noneBtn)
        root.addView(toolbarRow, margin(0, 12, 0, 0))

        countText = TextView(this).apply { textSize = 14f }
        root.addView(countText, margin(0, 8, 0, 0))

        val listView = ListView(this)
        root.addView(
            listView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 8 }
        )

        val backupBtn = Button(this).apply {
            text = "شروع بکاپ‌گیری"
            setOnClickListener { startBackup() }
        }
        root.addView(backupBtn, margin(0, 12, 0, 0))

        resultText = TextView(this).apply { textSize = 14f }
        root.addView(resultText, margin(0, 8, 0, 0))

        setContentView(root)

        apps = AppsIO.collect(this)
        adapter = Adapter()
        listView.adapter = adapter
        updateCount()
    }

    private fun margin(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(l, t, r, b) }

    private fun setAll(v: Boolean) {
        selected.clear()
        if (v) apps.forEach { selected.add(it.pkg) }
        adapter.notifyDataSetChanged()
        updateCount()
    }

    private fun updateCount() {
        countText.text = "انتخاب‌شده: ${selected.size} از ${apps.size}"
    }

    private fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(i, REQ_PICK_FOLDER)
        } catch (_: Exception) {
            Toast.makeText(this, "انتخاب‌گر پوشه روی این گوشی پیدا نشد", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_FOLDER) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        destTree = uri
        folderText.text = "پوشه‌ی مقصد: " + (uri.lastPathSegment ?: uri.toString())
    }

    private fun safeName(name: String, fallback: String): String {
        val cleaned = name.map { c -> if (c.code < 32 || c in "\\/:*?\"<>|") ' ' else c }.joinToString("")
        val trimmed = cleaned.trim().replace(Regex("\\s+"), " ")
        val cut = if (trimmed.length > 50) trimmed.substring(0, 50).trim() else trimmed
        return cut.ifEmpty { fallback }
    }

    private fun startBackup() {
        val tree = destTree
        if (tree == null) {
            Toast.makeText(this, "اول پوشه‌ی مقصد را انتخاب کن", Toast.LENGTH_LONG).show()
            return
        }
        val chosen = apps.filter { selected.contains(it.pkg) }
        if (chosen.isEmpty()) {
            Toast.makeText(this, "هیچ برنامه‌ای تیک نخورده", Toast.LENGTH_LONG).show()
            return
        }
        resultText.text = "در حال بکاپ‌گیری از ${chosen.size} برنامه..."

        Thread {
            var ok = 0
            var bad = 0
            val used = HashSet<String>()
            val parentDocId = DocumentsContract.getTreeDocumentId(tree)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)

            for (app in chosen) {
                try {
                    var base = safeName(app.name, app.pkg)
                    if (!used.add(base)) {
                        var i = 2
                        while (!used.add("$base ($i)")) i++
                        base = "$base ($i)"
                    }
                    if (app.paths.size == 1) {
                        val newUri = DocumentsContract.createDocument(
                            contentResolver, parentUri, "application/vnd.android.package-archive", "$base.apk"
                        ) ?: throw Exception("ساخت فایل ناموفق")
                        contentResolver.openOutputStream(newUri)?.use { out ->
                            File(app.paths[0]).inputStream().use { it.copyTo(out) }
                        }
                    } else {
                        val newUri = DocumentsContract.createDocument(
                            contentResolver, parentUri, "application/zip", "$base.apks"
                        ) ?: throw Exception("ساخت فایل ناموفق")
                        contentResolver.openOutputStream(newUri)?.use { out ->
                            ZipOutputStream(out).use { zip ->
                                for (p in app.paths) {
                                    zip.putNextEntry(ZipEntry(File(p).name))
                                    File(p).inputStream().use { input -> input.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                    ok++
                } catch (_: Exception) {
                    bad++
                }
                val okFinal = ok
                val badFinal = bad
                runOnUiThread { resultText.text = "در حال بکاپ‌گیری... موفق: $okFinal، ناموفق: $badFinal" }
            }

            val okFinal = ok
            val badFinal = bad
            runOnUiThread {
                resultText.text = "بکاپ تمام شد: $okFinal موفق، $badFinal ناموفق."
                Toast.makeText(this, resultText.text, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun sizeText(size: Long): String =
        if (size >= 1024 * 1024) String.format("%.1f MB", size / 1024.0 / 1024.0)
        else String.format("%.0f KB", size / 1024.0)

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val app = apps[position]
            val row = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(8, 16, 8, 16)
            }
            val cb = CheckBox(this@AppsActivity).apply {
                isChecked = selected.contains(app.pkg)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selected.add(app.pkg) else selected.remove(app.pkg)
                    updateCount()
                }
            }
            val texts = LinearLayout(this@AppsActivity).apply { orientation = LinearLayout.VERTICAL }
            val nameLine = TextView(this@AppsActivity).apply { text = app.name; textSize = 15f }
            val kindLabel = when (app.kind) { 1 -> "سیستمی"; 2 -> "سیستمی (به‌روزشده)"; else -> "کاربر" }
            val subLine = TextView(this@AppsActivity).apply {
                text = "${app.pkg} • $kindLabel • ${sizeText(app.size)}"
                textSize = 12f
                setTextColor(0xFF777777.toInt())
            }
            texts.addView(nameLine)
            texts.addView(subLine)
            row.addView(cb)
            row.addView(texts)
            return row
        }
    }
}
