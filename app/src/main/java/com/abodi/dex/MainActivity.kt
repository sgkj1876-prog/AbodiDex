package com.abodi.dex

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(10, 11, 15)
    private val panel = Color.rgb(22, 23, 29)
    private val panel2 = Color.rgb(29, 30, 38)
    private val line = Color.rgb(55, 57, 68)
    private val text = Color.rgb(245, 245, 248)
    private val muted = Color.rgb(165, 168, 180)
    private val cyan = Color.rgb(40, 214, 207)
    private val purple = Color.rgb(170, 120, 235)
    private val yellow = Color.rgb(239, 202, 63)
    private val red = Color.rgb(239, 83, 92)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var stats: TextView
    private var summary: DexAnalyzer.Summary? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun tv(value: String, size: Float, color: Int = text, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(10)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, 1)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(8, 4, 8, 4) }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(14)
        setBackgroundColor(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(8, 7, 8, 7) }
    }

    private fun button(label: String, color: Int = panel2, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(this@MainActivity.text)
        setBackgroundColor(color)
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14)
            setBackgroundColor(panel)
        }
        header.addView(tv("ABODI DEX", 25f, Color.WHITE, true))
        header.addView(tv("APK / DEX ANALYZER • XREF • BYTECODE", 11f, muted))
        root.addView(header)

        val pickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        pickRow.addView(button("📦  اختيار APK", panel2) { pickApk() }, LinearLayout.LayoutParams(0, 52, 1f))
        pickRow.addView(button("⟳  إعادة تعيين", panel2) { resetUi() }, LinearLayout.LayoutParams(0, 52, 1f))
        root.addView(pickRow)

        val searchCard = card()
        search = EditText(this).apply {
            hint = "🔎  Search classes, methods, strings…"
            setHintTextColor(muted)
            setTextColor(this@MainActivity.text)
            textSize = 14f
            setSingleLine(true)
            setPadding(14)
            setBackgroundColor(panel2)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            addTextChangedListener(SimpleTextWatcher { filterCurrentView() })
        }
        searchCard.addView(search, LinearLayout.LayoutParams(-1, 52))
        searchCard.addView(tv("البحث يعمل على Strings وMethods وClasses وXREFs", 11f, muted))
        root.addView(searchCard)

        stats = tv("DEX: —    Classes: —    Methods: —    Strings: —", 12f, muted)
        root.addView(stats)

        status = tv("جاهز لتحليل APK", 13f, cyan, true)
        root.addView(status)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        renderHome()
    }

    private fun renderHome() {
        content.removeAllViews()
        val nav = card()
        nav.addView(tv("مستكشف التحليل", 17f, Color.WHITE, true))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row1.addView(button("Strings") { showStrings() }, LinearLayout.LayoutParams(0, 50, 1f))
        row1.addView(button("Classes") { showClasses() }, LinearLayout.LayoutParams(0, 50, 1f))
        row1.addView(button("Methods") { showMethods() }, LinearLayout.LayoutParams(0, 50, 1f))
        nav.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row2.addView(button("🔗 XREF") { showXrefs() }, LinearLayout.LayoutParams(0, 50, 1f))
        row2.addView(button("<> Bytecode") { showMethods() }, LinearLayout.LayoutParams(0, 50, 1f))
        nav.addView(row2)
        content.addView(nav)

        val targets = card()
        targets.addView(tv("⚡ String Pool Targets", 17f, cyan, true))
        targets.addView(tv("يتم ترتيب الكلمات المهمة وإظهار الدالة التي استخدمتها مع إمكانية فتح XREF والـBytecode.", 12f, muted))
        content.addView(targets)
        renderHits(summary?.hits ?: emptyList())
    }

    private fun renderHits(hits: List<DexAnalyzer.Hit>) {
        val title = tv("نتائج التحليل", 16f, Color.WHITE, true)
        content.addView(title)
        if (hits.isEmpty()) {
            content.addView(tv("لم يتم العثور على نتائج. اختر APK للبدء.", 13f, muted))
            return
        }
        hits.take(100).forEachIndexed { index, hit ->
            val c = card()
            val severityColor = when { hit.severity >= 85 -> red; hit.severity >= 75 -> yellow; else -> cyan }
            c.addView(tv("${hit.severity}%   ${severityLabel(hit.severity)}", 11f, severityColor, true))
            c.addView(tv(hit.text, 15f, Color.WHITE, true))
            c.addView(tv("${hit.method}\n${hit.dex}", 10f, muted))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            row.addView(button("🔗 XREF") { showXrefFor(hit.text) }, LinearLayout.LayoutParams(0, 48, 1f))
            row.addView(button("<> Bytecode") { openMethod(hit.method) }, LinearLayout.LayoutParams(0, 48, 1f))
            c.addView(row)
            content.addView(c)
        }
    }

    private fun severityLabel(value: Int) = when {
        value >= 85 -> "CRITICAL"
        value >= 75 -> "HIGH"
        else -> "MEDIUM"
    }

    private fun filterCurrentView() {
        val q = search.text.toString().trim().lowercase(Locale.ROOT)
        if (summary == null) return
        if (q.isEmpty()) { renderHome(); return }
        content.removeAllViews()
        content.addView(tv("نتائج البحث: $q", 17f, cyan, true))
        val s = summary!!
        val hitMatches = s.hits.filter { it.text.lowercase().contains(q) || it.method.lowercase().contains(q) }
        val methodMatches = s.methodsList.filter { it.key.lowercase().contains(q) || it.className.lowercase().contains(q) }.take(100)
        val xrefMatches = s.xrefs.filter { it.target.lowercase().contains(q) || it.fromMethod.lowercase().contains(q) }.take(100)
        content.addView(tv("Strings / Hits (${hitMatches.size})", 14f, yellow, true))
        hitMatches.take(50).forEach { hit -> addHitCard(hit) }
        content.addView(tv("Methods (${methodMatches.size})", 14f, purple, true))
        methodMatches.forEach { addMethodCard(it) }
        content.addView(tv("XREF (${xrefMatches.size})", 14f, cyan, true))
        xrefMatches.forEach { addXrefCard(it) }
    }

    private fun addHitCard(hit: DexAnalyzer.Hit) {
        val c = card()
        c.addView(tv("${hit.severity}% ${severityLabel(hit.severity)}", 11f, if (hit.severity >= 85) red else cyan, true))
        c.addView(tv(hit.text, 14f, Color.WHITE, true))
        c.addView(tv(hit.method, 10f, muted))
        c.addView(button("🔗 XREF") { showXrefFor(hit.text) })
        content.addView(c)
    }

    private fun addMethodCard(info: DexAnalyzer.MethodInfo) {
        val c = card()
        c.addView(tv(info.name, 14f, Color.WHITE, true))
        c.addView(tv("${info.className}\n${info.signature}\n${info.dexName}", 10f, muted))
        c.addView(button("<> فتح Bytecode") { openMethod(info.key) })
        content.addView(c)
    }

    private fun addXrefCard(x: DexAnalyzer.Xref) {
        val c = card()
        c.addView(tv(x.kind.name, 10f, cyan, true))
        c.addView(tv(x.target, 13f, Color.WHITE, true))
        c.addView(tv("FROM: ${x.fromMethod}\n${x.dexName}", 10f, muted))
        c.addView(button("فتح Method") { openMethod(x.fromMethod) })
        content.addView(c)
    }

    private fun showStrings() {
        val s = summary ?: return needApk()
        content.removeAllViews()
        content.addView(tv("String Pool", 19f, cyan, true))
        s.xrefs.filter { it.kind == DexAnalyzer.Xref.Kind.STRING }.distinctBy { it.target }.take(300).forEach { addXrefCard(it) }
    }

    private fun showClasses() {
        val s = summary ?: return needApk()
        content.removeAllViews()
        content.addView(tv("Classes", 19f, purple, true))
        s.methodsList.groupBy { it.className }.keys.take(500).forEach { clazz ->
            val c = card()
            c.addView(tv(clazz, 13f, Color.WHITE, true))
            c.addView(tv("${s.methodsList.count { it.className == clazz }} methods", 10f, muted))
            c.addView(button("عرض Methods") {
                content.removeAllViews()
                content.addView(tv(clazz, 17f, purple, true))
                s.methodsList.filter { it.className == clazz }.forEach { addMethodCard(it) }
            })
            content.addView(c)
        }
    }

    private fun showMethods() {
        val s = summary ?: return needApk()
        content.removeAllViews()
        content.addView(tv("Methods / Bytecode", 19f, yellow, true))
        s.methodsList.take(500).forEach { addMethodCard(it) }
    }

    private fun showXrefs() {
        val s = summary ?: return needApk()
        content.removeAllViews()
        content.addView(tv("XREF Explorer", 19f, cyan, true))
        val counts = s.xrefs.groupingBy { it.kind }.eachCount()
        content.addView(tv("Strings: ${counts[DexAnalyzer.Xref.Kind.STRING] ?: 0}   Calls: ${counts[DexAnalyzer.Xref.Kind.METHOD_CALL] ?: 0}   Fields: ${counts[DexAnalyzer.Xref.Kind.FIELD] ?: 0}", 11f, muted))
        s.xrefs.take(500).forEach { addXrefCard(it) }
    }

    private fun showXrefFor(target: String) {
        val s = summary ?: return needApk()
        content.removeAllViews()
        content.addView(tv("XREF Explorer", 19f, cyan, true))
        content.addView(tv(target, 15f, Color.WHITE, true))
        val refs = s.xrefs.filter { it.target == target || it.target.contains(target) }.take(500)
        if (refs.isEmpty()) content.addView(tv("لا توجد مراجع مسجلة لهذا الهدف.", 12f, muted))
        refs.forEach { addXrefCard(it) }
    }

    private fun openMethod(key: String) {
        val info = summary?.methodsList?.firstOrNull { it.key == key }
            ?: summary?.methodsList?.firstOrNull { it.key.contains(key) }
        if (info == null) return Toast.makeText(this, "Method غير موجود في الفهرس الحالي", Toast.LENGTH_SHORT).show()
        content.removeAllViews()
        content.addView(tv("<> Bytecode Explorer", 19f, yellow, true))
        content.addView(tv(info.name, 16f, Color.WHITE, true))
        content.addView(tv("${info.className}\n${info.signature}\n${info.dexName}", 10f, muted))
        val code = card()
        info.bytecode.forEach { lineText ->
            val lineView = tv(lineText, 10f, Color.rgb(215, 216, 224))
            lineView.typeface = android.graphics.Typeface.MONOSPACE
            lineView.setTextIsSelectable(true)
            lineView.gravity = Gravity.CENTER_VERTICAL
            code.addView(lineView)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row.addView(button("🔗 XREF") { showXrefFor(key) }, LinearLayout.LayoutParams(0, 48, 1f))
        row.addView(button("نسخ") { copyText(info.bytecode.joinToString("\n")) }, LinearLayout.LayoutParams(0, 48, 1f))
        code.addView(row)
        content.addView(code)
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ABODI DEX", value))
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
    }

    private fun pickApk() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/vnd.android.package-archive"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 42)
    }

    @Deprecated("Deprecated in Android API; retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) data?.data?.let { analyze(it) }
    }

    private fun analyze(uri: Uri) {
        search.setText("")
        status.text = "جارٍ استخراج وتحليل DEX…"
        executor.execute {
            try {
                val result = DexAnalyzer(this).analyze(uri) { _, msg -> runOnUiThread { status.text = msg } }
                runOnUiThread {
                    summary = result
                    stats.text = "DEX: ${result.dexCount}    Classes: ${result.classes}    Methods: ${result.methods}    Strings: ${result.strings}"
                    status.text = "✓ اكتمل التحليل • ${result.hits.size} نتائج مهمة • ${result.xrefs.size} XREF"
                    renderHome()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطأ: ${e.message ?: "فشل التحليل"}" }
            }
        }
    }

    private fun resetUi() {
        summary = null
        search.setText("")
        stats.text = "DEX: —    Classes: —    Methods: —    Strings: —"
        status.text = "جاهز لتحليل APK"
        renderHome()
    }

    private fun needApk() {
        Toast.makeText(this, "اختر ملف APK أولًا", Toast.LENGTH_SHORT).show()
    }

    private class SimpleTextWatcher(private val callback: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { callback() }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
