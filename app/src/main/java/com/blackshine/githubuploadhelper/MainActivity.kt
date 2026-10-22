package com.blackshine.githubuploadhelper

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.graphics.Typeface
import android.text.method.ScrollingMovementMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var token: EditText
    private lateinit var owner: EditText
    private lateinit var repo: EditText
    private lateinit var branch: EditText
    private lateinit var dest: EditText
    private lateinit var message: EditText
    private lateinit var log: TextView
    private lateinit var console: TextView
    private val logLines = StringBuilder()
    private var workflowFound = false
    private var addApkToReleaseWorkflow = false
    private var changedEntries: List<ZipEntryData> = emptyList()
    private lateinit var projectPathLabel: TextView
    private var consoleExpanded = false
    private lateinit var selectedLabel: TextView
    private lateinit var settingsPanel: LinearLayout
    private lateinit var uploadPanel: LinearLayout
    private lateinit var linksPanel: LinearLayout
    private lateinit var overwrite: CheckBox
    private lateinit var deleteMode: CheckBox
    private var selected: Uri? = null
    private var zipEntries: List<ZipEntryData> = emptyList()
    private var repoPermissions: JSONObject? = null
    data class ZipEntryData(val path: String, val bytes: ByteArray)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("repo_settings", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(0xFFF3F5F9.toInt()) }
        val header = TextView(this).apply { text = "GitHub Upload Helper 3.2"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(0xFF172554.toInt()); setPadding(0, 8, 0, 8) }
        root.addView(header)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabs)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val consoleTitle = Button(this).apply { text = "▶ CONSOLE / LOG (แตะเพื่อขยาย)"; textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(0xFF9BE7A5.toInt()); setBackgroundColor(0xFF20242B.toInt()); setOnClickListener { consoleExpanded = !consoleExpanded; log.visibility = View.VISIBLE; log.maxHeight = if (consoleExpanded) 260 else 76; text = if (consoleExpanded) "▼ CONSOLE / LOG (แตะเพื่อย่อ)" else "▶ CONSOLE / LOG (แตะเพื่อขยาย)" } }
        root.addView(consoleTitle, LinearLayout.LayoutParams(-1, 42))
        log = TextView(this).apply { text = "[READY] พร้อมใช้งาน"; textSize = 11f; typeface = Typeface.MONOSPACE; movementMethod = ScrollingMovementMethod(); setTextColor(0xFFE7F3E8.toInt()); setPadding(8, 5, 8, 5); setBackgroundColor(0xFF20242B.toInt()); maxHeight = 76; visibility = View.VISIBLE }
        root.addView(log, LinearLayout.LayoutParams(-1, -2))
        val clearLog = Button(this).apply { text = "ล้าง Log"; textSize = 11f; minHeight = 36; setOnClickListener { logLines.clear(); setLog("[READY] ล้าง Log แล้ว") } }
        root.addView(clearLog, LinearLayout.LayoutParams(-1, 38))
        setContentView(root)

        settingsPanel = panel(content)
        uploadPanel = panel(content)
        linksPanel = panel(content)

        token = field(settingsPanel, "GitHub Personal Access Token", "").apply { inputType = 129 }
        owner = field(settingsPanel, "Owner / Username", "").apply { setText(prefs.getString("owner", "")) }
        repo = field(settingsPanel, "Repository", "").apply { setText(prefs.getString("repo", "")) }
        branch = field(settingsPanel, "Branch", "").apply { setText(prefs.getString("branch", "main")) }
        button(settingsPanel, "บันทึกการตั้งค่า Repository") {
            prefs.edit().putString("owner", owner.text.toString().trim()).putString("repo", repo.text.toString().trim()).putString("branch", branch.text.toString().trim()).apply()
            setLog("บันทึก Owner / Repository / Branch แล้ว (ไม่ได้บันทึก Token เพื่อความปลอดภัย)")
        }
        button(settingsPanel, "ตรวจสอบ Token / Repository / Branch และสิทธิ์") { runTask { checkAll() } }
        button(settingsPanel, "เปิดหน้า Repository") { openGitHub(repoUrl()) }
        button(settingsPanel, "เปิดหน้า Code") { openGitHub(repoUrl() + "/tree/" + enc(branch.text.toString().trim().ifEmpty { "main" })) }
        button(settingsPanel, "เปิดหน้า Commits") { openGitHub(repoUrl() + "/commits/" + enc(branch.text.toString().trim().ifEmpty { "main" })) }
        button(settingsPanel, "เปิดหน้าสร้าง Fine-grained Token") { openGitHub("https://github.com/settings/personal-access-tokens/new") }
        button(settingsPanel, "เปิดหน้า Token ที่มีอยู่") { openGitHub("https://github.com/settings/personal-access-tokens") }
        button(settingsPanel, "เปิดหน้า Settings ของ Repository") { openGitHub(repoUrl() + "/settings") }
        button(settingsPanel, "เปิดหน้า Releases") { openGitHub(repoUrl() + "/releases") }

        uploadPanel.addView(TextView(this).apply { text = "อัปโหลด / เขียนทับ / ลบไฟล์"; textSize = 19f; setTextColor(0xFF172554.toInt()); setPadding(2, 8, 2, 4) })
        projectPathLabel = TextView(this).apply { text = "โฟลเดอร์โปรเจกต์ในเครื่อง: จะสร้างเมื่อแตก ZIP ครั้งแรก"; textSize = 12f; setPadding(6, 6, 6, 6); setBackgroundColor(0xFFE4EAF3.toInt()) }
        uploadPanel.addView(projectPathLabel)
        dest = field(uploadPanel, "โฟลเดอร์ปลายทางใน GitHub (ไม่บังคับ)", "")
        message = field(uploadPanel, "ข้อความ Commit", "").apply { setText("Upload project from Android") }
        overwrite = CheckBox(this).apply { text = "อนุญาตเขียนทับไฟล์ที่มีอยู่ใน GitHub"; isChecked = false }
        uploadPanel.addView(overwrite)
        deleteMode = CheckBox(this).apply { text = "โหมดลบไฟล์: ลบไฟล์ใน GitHub ที่มี path ตรงกับไฟล์ใน ZIP (ไม่อัปโหลด)"; isChecked = false }
        uploadPanel.addView(deleteMode)
        uploadPanel.addView(TextView(this).apply { text = "โหมดลบจะใช้รายชื่อ path จาก ZIP เป็นรายการเป้าหมาย ต้องยืนยันอีกครั้งก่อนลบ และลบไม่ได้หาก Token ไม่มีสิทธิ์หรือไฟล์เปลี่ยนไปแล้ว"; textSize = 12f; setPadding(4, 4, 4, 8) })
        button(uploadPanel, "1. เลือกไฟล์ ZIP") { pickZip() }
        selectedLabel = TextView(this).apply { text = "ยังไม่ได้เลือก ZIP"; textSize = 14f; setPadding(8, 10, 8, 10); setBackgroundColor(0xFFFFFFFF.toInt()) }
        uploadPanel.addView(selectedLabel)
        button(uploadPanel, "2. แตก ZIP ลงโฟลเดอร์โปรเจกต์ในเครื่อง และเทียบความเปลี่ยนแปลง") { runTask { extractZip() } }
        button(uploadPanel, "เปิดดูรายงานความเปลี่ยนแปลง") { showDiffReport() }
        button(uploadPanel, "3. ตรวจสิทธิ์ก่อนดำเนินการ") { runTask { checkAll() } }
        button(uploadPanel, "4. เริ่มทำรายการตามโหมดที่เลือก") {
            if (deleteMode.isChecked) AlertDialog.Builder(this).setTitle("ยืนยันลบไฟล์ใน GitHub")
                .setMessage("จะลบไฟล์ที่มี path ตรงกับไฟล์ใน ZIP จาก Repository/Branch ที่ตั้งไว้ ไฟล์ที่ลบแล้วจะเกิด commit จริง ต้องการดำเนินการหรือไม่?")
                .setNegativeButton("ยกเลิก", null).setPositiveButton("ยืนยันลบ") { _, _ -> runTask { deleteMatchingFiles() } }.show()
            else runTask { uploadAll() }
        }
        button(uploadPanel, "คู่มือแก้ปัญหา") { showHelp() }

        linksPanel.addView(TextView(this).apply { text = "ทางลัด GitHub"; textSize = 19f; setTextColor(0xFF172554.toInt()); setPadding(2, 8, 2, 4) })
        button(linksPanel, "สร้าง Token (Fine-grained)") { openGitHub("https://github.com/settings/personal-access-tokens/new") }
        button(linksPanel, "รายการ Token") { openGitHub("https://github.com/settings/personal-access-tokens") }
        button(linksPanel, "Repository / Code / Commits") { openGitHub(repoUrl()) }
        button(linksPanel, "เอกสารสิทธิ์ GitHub API") { openGitHub("https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens") }
        button(linksPanel, "เอกสารจัดการไฟล์ (สร้าง/แก้ไข/ลบ)") { openGitHub("https://docs.github.com/en/rest/repos/contents") }

        fun tab(label: String, target: LinearLayout) { tabs.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { settingsPanel.visibility = View.GONE; uploadPanel.visibility = View.GONE; linksPanel.visibility = View.GONE; target.visibility = View.VISIBLE } }, LinearLayout.LayoutParams(0, -2, 1f)) }
        tab("ตั้งค่า Repository", settingsPanel); tab("อัปโหลดไฟล์", uploadPanel); tab("ทางลัด GitHub", linksPanel)
        settingsPanel.visibility = View.VISIBLE; uploadPanel.visibility = View.GONE; linksPanel.visibility = View.GONE
        updateProjectPathLabel()
    }

    private fun panel(parent: LinearLayout) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 4, 4, 8); parent.addView(this) }
    private fun field(parent: LinearLayout, label: String, hint: String): EditText {
        parent.addView(TextView(this).apply { text = label; textSize = 14f; setPadding(2, 9, 2, 3) })
        val e = EditText(this).apply { this.hint = ""; textSize = 15f; setSingleLine(true); setPadding(12, 8, 12, 8); setBackgroundColor(0xFFFFFFFF.toInt()) }
        parent.addView(e, LinearLayout.LayoutParams(-1, -2)); return e
    }
    private fun button(parent: LinearLayout, label: String, action: () -> Unit) { parent.addView(Button(this).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 }) }
    private fun pickZip() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }, 41) }
    @Deprecated("Deprecated Android callback retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == 41 && resultCode == RESULT_OK) { selected = data?.data; zipEntries = emptyList(); selectedLabel.text = "เลือก ZIP แล้ว: ${selected?.lastPathSegment ?: "ไฟล์ ZIP"}"; setLog("เลือกไฟล์ ZIP แล้ว กดแตก ZIP เพื่อดูรายการ") } }
    private fun runTask(task: () -> String) { setLog("[RUN] เริ่มงาน"); executor.execute { val result = try { task() } catch (e: Exception) { val msg = e.message ?: e.javaClass.simpleName; "[ERROR] $msg\n[HELP] ${diagnose(msg)}" }; runOnUiThread { setLog(result); if (result.contains("[ERROR]") || result.contains("HTTP ")) showErrorActions(result) } } }
    private fun showErrorActions(result: String) { AlertDialog.Builder(this).setTitle("ตรวจพบปัญหา").setMessage(result.take(1800)).setPositiveButton("ลองตรวจสิทธิ์ใหม่") { _, _ -> runTask { checkAll() } }.setNeutralButton("เปิด Repository") { _, _ -> openGitHub(repoUrl()) }.setNegativeButton("ปิด", null).show() }
    private fun values(): Triple<String, String, String> { val t = token.text.toString().trim(); val o = owner.text.toString().trim(); val r = repo.text.toString().trim(); if (t.isEmpty() || o.isEmpty() || r.isEmpty()) throw Exception("กรอก Token, Owner และ Repository ให้ครบในแท็บตั้งค่า"); return Triple(t, o, r) }
    private fun readZip(): List<ZipEntryData> {
        val uri = selected ?: throw Exception("กรุณาเลือกไฟล์ ZIP ก่อน")
        val out = mutableListOf<ZipEntryData>(); contentResolver.openInputStream(uri).use { raw ->
            if (raw == null) throw Exception("เปิด ZIP ไม่ได้")
            ZipInputStream(raw).use { zis -> var entry = zis.nextEntry; var total = 0L
                while (entry != null) { val name = entry.name.replace("\\", "/"); val safe = name.split("/").filter { it.isNotEmpty() }
                    if (name.startsWith("/") || safe.any { it == ".." } || name.contains(":")) throw Exception("พบ path ที่ไม่ปลอดภัยใน ZIP: $name")
                    if (!entry.isDirectory && !name.endsWith("/")) { val buffer = ByteArrayOutputStream(); val chunk = ByteArray(8192); while (true) { val n = zis.read(chunk); if (n <= 0) break; total += n; if (total > 150L * 1024 * 1024) throw Exception("ZIP แตกแล้วมีขนาดเกิน 150 MB"); buffer.write(chunk, 0, n) }; out.add(ZipEntryData(name, buffer.toByteArray())); if (out.size > 500) throw Exception("ZIP มีไฟล์เกิน 500 ไฟล์") }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
        }
        if (out.isEmpty()) throw Exception("ไม่พบไฟล์ใน ZIP"); return out
    }
    private fun projectDir(): File {
        val (_, o, r) = values()
        val safeOwner = o.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val safeRepo = r.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(File(filesDir, "projects/$safeOwner"), safeRepo)
    }
    private fun updateProjectPathLabel() { if (::projectPathLabel.isInitialized) projectPathLabel.text = "โฟลเดอร์โปรเจกต์ในเครื่อง: ${projectDirForLabel()}\nระบบจะเก็บไว้ใช้ครั้งต่อไป" }
    private fun projectDirForLabel(): String { val o = owner.text.toString().trim().ifEmpty { "owner" }.replace(Regex("[^A-Za-z0-9_.-]"), "_"); val r = repo.text.toString().trim().ifEmpty { "repository" }.replace(Regex("[^A-Za-z0-9_.-]"), "_"); return File(File(filesDir, "projects/$o"), r).absolutePath }
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun extractZip(): String {
        val rawEntries = readZip()
        // Remove a single common wrapper folder, e.g. GitHubUploadHelper-main/.
        val firstParts = rawEntries.map { it.path.trim('/').substringBefore('/') }.distinct()
        val entries = if (firstParts.size == 1 && rawEntries.all { it.path.contains('/') } && !rawEntries.any { it.path.trim('/') == firstParts[0] }) rawEntries.map { it.copy(path = it.path.substringAfter('/')) } else rawEntries
        val dir = projectDir(); if (!dir.exists() && !dir.mkdirs()) throw Exception("สร้างโฟลเดอร์โปรเจกต์ในเครื่องไม่ได้: ${dir.absolutePath}")
        val before = dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }
        val incoming = entries.associate { it.path.trimStart('/') to it.bytes }
        val added = incoming.keys.filter { it !in before }
        val changed = incoming.keys.filter { it in before && sha(before.getValue(it)) != sha(incoming.getValue(it)) }
        val same = incoming.keys.filter { it in before && sha(before.getValue(it)) == sha(incoming.getValue(it)) }
        val removed = before.keys.filter { it !in incoming }
        for ((path, bytes) in incoming) { val out = File(dir, path); if (!out.canonicalPath.startsWith(dir.canonicalPath + File.separator)) throw Exception("path ไม่ปลอดภัย: $path"); out.parentFile?.mkdirs(); out.writeBytes(bytes) }
        zipEntries = entries.map { ZipEntryData(it.path.trimStart('/'), it.bytes) }
        changedEntries = zipEntries.filter { it.path in added || it.path in changed }
        getSharedPreferences("repo_settings", MODE_PRIVATE).edit().putString("project_dir_${owner.text.toString().trim()}_${repo.text.toString().trim()}", dir.absolutePath).apply()
        workflowFound = zipEntries.any { it.path.replace("\\", "/").contains(".github/workflows/") && (it.path.endsWith(".yml", true) || it.path.endsWith(".yaml", true)) }
        if (workflowFound) runOnUiThread { askRelease() }
        updateProjectPathLabel()
        val report = "แตก ZIP ลงโฟลเดอร์: ${dir.absolutePath}\nไฟล์ใหม่: ${added.size}\nไฟล์เปลี่ยน: ${changed.size}\nไฟล์ไม่เปลี่ยน: ${same.size}\nไฟล์เดิมที่ไม่มีใน ZIP: ${removed.size} (ไม่ลบอัตโนมัติ)\n\nไฟล์ที่จะอัปโหลดเมื่อเลือกอัปโหลดเฉพาะที่เปลี่ยน: ${changedEntries.size}"
        getSharedPreferences("repo_settings", MODE_PRIVATE).edit().putString("last_diff_report", report + "\n\nไฟล์ใหม่/เปลี่ยน:\n" + (added + changed).take(150).joinToString("\n")).apply()
        return "[OK] ตรวจ ZIP และซิงก์โฟลเดอร์โปรเจกต์แล้ว\n$report\n\nกด ‘เปิดดูรายงานความเปลี่ยนแปลง’ เพื่อดูรายชื่อไฟล์"
    }
    private fun showDiffReport() { val report = getSharedPreferences("repo_settings", MODE_PRIVATE).getString("last_diff_report", "ยังไม่มีรายงาน กรุณาเลือก ZIP แล้วแตก ZIP ก่อน") ?: "ยังไม่มีรายงาน"; AlertDialog.Builder(this).setTitle("เปรียบเทียบไฟล์โปรเจกต์").setMessage(report).setPositiveButton("ตกลง", null).show() }
    private fun askRelease() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
        val tag = EditText(this).apply { hint = "เว้นว่างให้สร้างรุ่นอัตโนมัติ เช่น v3.0.0"; setSingleLine(true) }
        val name = EditText(this).apply { hint = "ชื่อ Release (ไม่บังคับ)"; setSingleLine(true) }
        val notes = EditText(this).apply { hint = "รายละเอียด Release (ไม่บังคับ)"; minLines = 2; gravity = Gravity.TOP }
        val addApk = CheckBox(this).apply {
            text = "เพิ่มขั้นตอนแนบ APK เข้า GitHub Release ใน Workflow"
            isChecked = false
        }
        val apkInfo = TextView(this).apply {
            text = "เลือกช่องนี้แล้วระบบจะปรับ Workflow ในไฟล์ ZIP ให้ทำงานเมื่อมี tag v* และแนบ APK ที่ Build ได้เข้า Release อัตโนมัติ การเปลี่ยนแปลง Workflow จะถูกส่งขึ้นเมื่อกดอัปโหลดไฟล์ ส่วนการสร้าง Release ควรทำหลังอัปโหลด Workflow สำเร็จแล้ว"
            textSize = 12f
            setPadding(4, 4, 4, 8)
        }
        box.addView(tag); box.addView(name); box.addView(notes); box.addView(addApk); box.addView(apkInfo)
        AlertDialog.Builder(this).setTitle("พบ GitHub Actions Workflow")
            .setMessage("พบไฟล์ Workflow สำหรับทำงานอัตโนมัติ ต้องการสร้าง GitHub Release หรือเพิ่มขั้นตอนแนบ APK หรือไม่?")
            .setView(box).setNegativeButton("ปิด", null)
            .setNeutralButton("บันทึกตัวเลือก APK", null)
            .setPositiveButton("สร้าง Release", null)
            .setOnShowListener {
                val dlg = (it as AlertDialog)
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (addApk.isChecked) {
                        try {
                            patchWorkflowForReleaseApk()
                            addApkToReleaseWorkflow = true
                            setLog("[OK] เพิ่มขั้นตอนแนบ APK ใน Workflow แล้วในรายการไฟล์ที่รออัปโหลด\nกรุณาอัปโหลดไฟล์/Commit นี้ก่อน แล้วจึงสร้าง Release ด้วย tag v* เพื่อให้ Workflow ทำงาน")
                            dlg.dismiss()
                        } catch (e: Exception) { setLog("[ERROR] เพิ่มขั้นตอน APK ไม่สำเร็จ: ${e.message}") }
                    } else {
                        setLog("[INFO] ไม่ได้เลือกเพิ่มขั้นตอนแนบ APK")
                        dlg.dismiss()
                    }
                }
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (addApk.isChecked) {
                        try { patchWorkflowForReleaseApk(); addApkToReleaseWorkflow = true }
                        catch (e: Exception) { setLog("[ERROR] ปรับ Workflow ไม่สำเร็จ: ${e.message}"); return@setOnClickListener }
                        dlg.dismiss()
                        AlertDialog.Builder(this).setTitle("อัปเดต Workflow ก่อนสร้าง Release")
                            .setMessage("ระบบเพิ่มขั้นตอนแนบ APK ใน Workflow แล้ว ต้องอัปโหลดการเปลี่ยนแปลงนี้ให้สำเร็จก่อน จึงค่อยสร้าง Release/tag เพื่อให้ GitHub Actions ใช้ Workflow รุ่นใหม่\n\nต้องการปิดหน้าต่างนี้และอัปโหลดไฟล์ก่อนหรือไม่?")
                            .setPositiveButton("ตกลง") { _, _ -> setLog("[INFO] กรุณากดเริ่มอัปโหลดไฟล์ก่อนสร้าง Release") }
                            .setNegativeButton("ยกเลิก", null).show()
                    } else {
                        dlg.dismiss()
                        runTask { createRelease(tag.text.toString(), name.text.toString(), notes.text.toString()) }
                    }
                }
            }.show()
    }
    private fun patchWorkflowForReleaseApk() {
        val idx = zipEntries.indexOfFirst { it.path.replace("\\", "/").contains(".github/workflows/") && (it.path.endsWith(".yml", true) || it.path.endsWith(".yaml", true)) }
        if (idx < 0) throw Exception("ไม่พบไฟล์ .github/workflows/*.yml หรือ *.yaml ใน ZIP")
        val entry = zipEntries[idx]
        var yaml = entry.bytes.toString(Charsets.UTF_8)
        if (yaml.contains("softprops/action-gh-release") && yaml.contains("app/build/outputs/apk")) {
            setLog("[INFO] Workflow มีขั้นตอนแนบ APK เข้า Release อยู่แล้ว")
            return
        }
        // Ensure tag pushes trigger the workflow and grant release creation permission.
        if (!Regex("(?m)^\\s*tags\\s*:").containsMatchIn(yaml)) {
            val pushPattern = Regex("(?m)^(\\s*)push\\s*:\s*$")
            yaml = if (pushPattern.containsMatchIn(yaml)) {
                pushPattern.replaceFirst(yaml) { m -> m.value + "\n" + m.groupValues[1] + "  tags: [ 'v*' ]" }
            } else {
                yaml.replaceFirst(Regex("(?m)^on\\s*:\s*$"), "on:\n  push:\n    tags: [ 'v*' ]")
            }
        }
        if (Regex("(?m)^permissions\\s*:").containsMatchIn(yaml)) {
            yaml = yaml.replaceFirst(Regex("(?ms)^permissions\\s*:\s*\\n(?:[ \t]+[^\\n]*\\n)*"), "permissions:\n  contents: write\n")
        } else {
            yaml = yaml.replaceFirst(Regex("(?m)^jobs\\s*:"), "permissions:\n  contents: write\n\njobs:")
        }
        val step = """      - name: Publish APK to GitHub Release
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v2
        with:
          name: GitHub Upload Helper ${'$'}{{ github.ref_name }}
          generate_release_notes: true
          files: app/build/outputs/apk/debug/app-debug.apk
"""
        if (!yaml.contains("softprops/action-gh-release")) {
            yaml = yaml.trimEnd() + "\n" + step
        }
        val patched = ZipEntryData(entry.path, yaml.toByteArray(Charsets.UTF_8))
        zipEntries = zipEntries.toMutableList().also { it[idx] = patched }
        changedEntries = (changedEntries.filterNot { it.path == entry.path } + patched)
        getSharedPreferences("repo_settings", MODE_PRIVATE).edit().putString("last_diff_report", "เพิ่มขั้นตอนแนบ APK ใน Workflow: ${entry.path}\nไฟล์นี้ถูกทำเครื่องหมายเป็นไฟล์เปลี่ยนแปลงและจะถูกส่งขึ้นเมื่อเริ่มอัปโหลดไฟล์\n\nหมายเหตุ: สร้าง Release/tag v* หลัง Workflow นี้ถูกอัปโหลดสำเร็จแล้ว").apply()
    }

    private fun createRelease(tagInput: String, nameInput: String, notesInput: String): String {
        val (_, o, r) = values(); val b = branch.text.toString().trim().ifEmpty { "main" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val tag = tagInput.trim().ifEmpty { "v3.0.0-$stamp" }
        val title = nameInput.trim().ifEmpty { "GitHub Upload Helper $tag" }
        val notes = notesInput.trim().ifEmpty { "Release $tag created from Android using GitHub Upload Helper." }
        val body = JSONObject().put("tag_name", tag).put("target_commitish", b).put("name", title).put("body", notes).put("draft", false).put("prerelease", false)
        val (code, response) = request("POST", "/repos/${enc(o)}/${enc(r)}/releases", body)
        if (code !in 200..299) throw Exception("สร้าง Release ไม่สำเร็จ HTTP $code: $response\nต้องมีสิทธิ์ Contents/Metadata ที่เหมาะสม และ Tag ต้องไม่ซ้ำ")
        val j = JSONObject(response); return "[OK] สร้าง Release สำเร็จ\nTag: $tag\nชื่อ: $title\nURL: ${j.optString("html_url", repoUrl()+"/releases") }"
    }
    private fun request(method: String, endpoint: String, body: JSONObject? = null): Pair<Int, String> {
        val (t, _, _) = values(); val c = URL("https://api.github.com$endpoint").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 30000; c.setRequestProperty("Authorization", "Bearer $t"); c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
        val code = c.responseCode; val stream = if (code in 200..399) c.inputStream else c.errorStream; val text = stream?.bufferedReader()?.use { it.readText() } ?: ""; c.disconnect(); return code to text
    }
    private fun checkAll(): String {
        val (_, o, r) = values(); val (uc, ub) = request("GET", "/user"); if (uc !in 200..299) throw Exception("Token ตรวจไม่ผ่าน HTTP $uc\n$ub")
        val username = JSONObject(ub).optString("login", "unknown"); val (rc, rb) = request("GET", "/repos/${enc(o)}/${enc(r)}"); if (rc !in 200..299) throw Exception("อ่าน Repository ไม่สำเร็จ HTTP $rc\n$rb")
        val rj = JSONObject(rb); repoPermissions = rj.optJSONObject("permissions"); val canPush = repoPermissions?.optBoolean("push", false) ?: false
        val defaultBranch = rj.optString("default_branch", "main"); val b = branch.text.toString().trim().ifEmpty { defaultBranch }; val (bc, bb) = request("GET", "/repos/${enc(o)}/${enc(r)}/branches/${enc(b)}"); if (bc !in 200..299) throw Exception("ไม่พบ Branch '$b' HTTP $bc\n$bb")
        return "ตรวจสอบการเข้าถึงผ่าน\nToken account: $username\nRepository: $o/$r\nBranch: $b\nสิทธิ์ push ที่ GitHub รายงาน: ${if (canPush) "มี" else "ไม่มี/ไม่สามารถยืนยันได้"}\n\nหมายเหตุ: การอ่าน Repository สำเร็จไม่ได้ยืนยันสิทธิ์เขียน/ลบทุกกรณี การเขียนทับ/ลบจะตรวจ SHA และตรวจผลจาก API อีกครั้งก่อนรายงานผล"
    }
    private fun uploadAll(): String {
        val (_, o, r) = values()
        if (zipEntries.isEmpty()) zipEntries = readZip()
        val b = branch.text.toString().trim().ifEmpty { "main" }
        val prefix = dest.text.toString().trim().trim('/')
        val entries = if (changedEntries.isNotEmpty()) changedEntries else zipEntries
        if (changedEntries.isEmpty() && zipEntries.isNotEmpty()) throw Exception("ไม่พบไฟล์ใหม่หรือเปลี่ยนแปลงใน ZIP เมื่อเทียบกับโฟลเดอร์โปรเจกต์ในเครื่อง\nหากเป็นการอัปโหลดครั้งแรก ให้แตก ZIP ก่อน แล้วตรวจอีกครั้ง")
        if (entries.size > 500) throw Exception("มีไฟล์เกิน 500 ไฟล์")

        // Use Git Data API: upload blobs, create one tree, one commit, then update the branch ref once.
        // This creates ONE commit/push event for the whole batch, so Actions will not run once per file.
        val baseRef = "/repos/${enc(o)}/${enc(r)}/git/ref/heads/${enc(b)}"
        val (refCode, refBody) = request("GET", baseRef)
        if (refCode !in 200..299) throw Exception("อ่าน Branch reference ไม่สำเร็จ HTTP $refCode: $refBody")
        val baseCommitSha = JSONObject(refBody).optJSONObject("object")?.optString("sha").orEmpty()
        if (baseCommitSha.isBlank()) throw Exception("GitHub ไม่ส่ง SHA ของ commit ปัจจุบันกลับมา")
        val (commitCode, commitBody) = request("GET", "/repos/${enc(o)}/${enc(r)}/git/commits/${enc(baseCommitSha)}")
        if (commitCode !in 200..299) throw Exception("อ่าน commit ปัจจุบันไม่สำเร็จ HTTP $commitCode: $commitBody")
        val baseTreeSha = JSONObject(commitBody).optJSONObject("tree")?.optString("sha").orEmpty()
        if (baseTreeSha.isBlank()) throw Exception("GitHub ไม่ส่ง SHA ของ tree กลับมา")

        val treeEntries = org.json.JSONArray()
        val results = StringBuilder("เริ่มเตรียมไฟล์ ${entries.size} รายการเพื่อสร้าง commit เดียว\n")
        for ((i, item) in entries.withIndex()) {
            val path = listOf(prefix, item.path).filter { it.isNotBlank() }.joinToString("/")
            if (path.split('/').any { it == ".." || it.isBlank() }) throw Exception("path ไม่ปลอดภัย: $path")
            val contentEndpoint = "/repos/${enc(o)}/${enc(r)}/contents/" + path.split("/").joinToString("/") { enc(it) }
            val (existingCode, existingBody) = request("GET", "$contentEndpoint?ref=${enc(b)}")
            if (existingCode !in 200..299 && existingCode != 404) throw Exception("ตรวจไฟล์เดิม $path ไม่สำเร็จ HTTP $existingCode: $existingBody")
            if (existingCode in 200..299 && !overwrite.isChecked) throw Exception("พบไฟล์เดิมใน GitHub: $path\nเปิดตัวเลือกอนุญาตเขียนทับ แล้วตรวจสอบ/เริ่มใหม่อีกครั้ง")
            val blobBody = JSONObject().put("content", Base64.encodeToString(item.bytes, Base64.NO_WRAP)).put("encoding", "base64")
            val (blobCode, blobResponse) = request("POST", "/repos/${enc(o)}/${enc(r)}/git/blobs", blobBody)
            if (blobCode !in 200..299) throw Exception("อัปโหลดข้อมูลไฟล์ $path ไม่สำเร็จ HTTP $blobCode: $blobResponse")
            val blobSha = JSONObject(blobResponse).optString("sha")
            if (blobSha.isBlank()) throw Exception("ไม่ได้ SHA ของไฟล์ $path")
            treeEntries.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", blobSha))
            results.append("✓ เตรียม ${i + 1}/${entries.size}: $path\n")
            runOnUiThread { setLog("[RUN] เตรียมไฟล์ ${i + 1}/${entries.size}\nจะสร้าง commit เดียวเมื่อเตรียมครบ") }
        }
        val treeBody = JSONObject().put("base_tree", baseTreeSha).put("tree", treeEntries)
        val (treeCode, treeResponse) = request("POST", "/repos/${enc(o)}/${enc(r)}/git/trees", treeBody)
        if (treeCode !in 200..299) throw Exception("สร้าง Git tree ไม่สำเร็จ HTTP $treeCode: $treeResponse")
        val newTreeSha = JSONObject(treeResponse).optString("sha")
        if (newTreeSha.isBlank()) throw Exception("GitHub ไม่ส่ง SHA ของ tree ใหม่กลับมา")

        val commitMessage = message.text.toString().ifBlank { "Upload project from Android" }
        val commitPayload = JSONObject().put("message", commitMessage).put("tree", newTreeSha).put("parents", org.json.JSONArray().put(baseCommitSha))
        val (newCommitCode, newCommitResponse) = request("POST", "/repos/${enc(o)}/${enc(r)}/git/commits", commitPayload)
        if (newCommitCode !in 200..299) throw Exception("สร้าง commit ไม่สำเร็จ HTTP $newCommitCode: $newCommitResponse")
        val newCommitSha = JSONObject(newCommitResponse).optString("sha")
        if (newCommitSha.isBlank()) throw Exception("GitHub ไม่ส่ง SHA ของ commit ใหม่กลับมา")

        // Force=false; update only if the branch still points at the base commit to avoid overwriting concurrent changes.
        val updatePayload = JSONObject().put("sha", newCommitSha).put("force", false)
        val (updateCode, updateResponse) = request("PATCH", baseRef, updatePayload)
        if (updateCode !in 200..299) throw Exception("สร้าง commit แล้ว แต่เลื่อน Branch ไม่สำเร็จ HTTP $updateCode: $updateResponse\nตรวจหน้า Commits ก่อนลองใหม่ เพื่อป้องกัน commit ซ้ำ")
        return "[OK] อัปโหลดเสร็จแล้ว\nไฟล์ใน commit เดียว: ${entries.size}\nBranch: $b\nCommit: $newCommitSha\n\nGitHub Actions จะถูกกระตุ้นจากการอัปเดต Branch ครั้งเดียว ไม่ใช่แยกทุกไฟล์\n\nรายละเอียดไฟล์:\n$results"
    }
    private fun deleteMatchingFiles(): String {
        val (_, o, r) = values(); if (zipEntries.isEmpty()) zipEntries = readZip(); val b = branch.text.toString().trim().ifEmpty { "main" }; val prefix = dest.text.toString().trim().trim('/'); val entries = zipEntries
        val results = StringBuilder("เริ่มลบเป้าหมาย ${entries.size} ไฟล์\n"); var ok = 0
        for ((i, item) in entries.withIndex()) { val path = listOf(prefix, item.path).filter { it.isNotBlank() }.joinToString("/"); val endpoint = "/repos/${enc(o)}/${enc(r)}/contents/" + path.split("/").joinToString("/") { enc(it) }
            val (gc, gb) = request("GET", "$endpoint?ref=${enc(b)}"); if (gc == 404) { results.append("↷ ข้าม ").append(path).append(" — ไม่พบไฟล์\n"); continue }; if (gc !in 200..299) { results.append("✗ อ่าน ").append(path).append(" — HTTP ").append(gc).append("\n"); continue }
            val sha = JSONObject(gb).optString("sha"); if (sha.isBlank()) { results.append("✗ ").append(path).append(" — ไม่พบ SHA\n"); continue }
            val payload = JSONObject().put("message", message.text.toString().ifBlank { "Delete files from Android" } + " (${i + 1}/${entries.size})").put("sha", sha).put("branch", b); val (code, body) = request("DELETE", endpoint, payload)
            if (code in 200..299) { ok++; results.append("✓ ลบ ").append(path).append("\n") } else results.append("✗ ").append(path).append(" — HTTP ").append(code).append(": ").append(body.take(180)).append("\n")
            runOnUiThread { setLog("กำลังลบ ${i + 1}/${entries.size}\nลบสำเร็จ $ok ไฟล์") }
        }
        return "เสร็จสิ้น: ลบสำเร็จ $ok/${entries.size} ไฟล์\n\n$results"
    }
    private fun repoUrl(): String { val o = owner.text.toString().trim(); val r = repo.text.toString().trim(); return "https://github.com/${o.ifEmpty { "l3lackshine" }}/${r.ifEmpty { "GitHubUploadHelper" }}" }
    private fun openGitHub(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun diagnose(msg: String): String { val m = msg.lowercase(); return when { "401" in m || "bad credentials" in m -> "401: Token ไม่ถูกต้อง หมดอายุ หรือถูกเพิกถอน"; "403" in m || "resource not accessible" in m -> "403: ตรวจ Contents: Read and write, Workflows: Read and write หากแก้ไฟล์ใน .github/workflows และสิทธิ์ Branch protection"; "404" in m || "not found" in m -> "404: ตรวจ Owner, Repository, Branch, path และสิทธิ์ Token"; "409" in m || "sha" in m -> "409: ไฟล์เปลี่ยนระหว่างตรวจหรือ SHA ไม่ตรง ให้ตรวจใหม่แล้วลองอีกครั้ง"; "timeout" in m || "failed to connect" in m -> "เครือข่าย: ตรวจอินเทอร์เน็ตและลองใหม่"; else -> "ตรวจ Token, สิทธิ์ Contents: Read and write, Branch และชื่อ path" } }
    private fun showHelp() { setLog("""คู่มือสิทธิ์และการใช้งาน
• Fine-grained Token: เลือก Repository ที่ต้องการ และตั้ง Contents = Read and write
• หากอัปโหลด/แก้ไฟล์ .github/workflows ให้ตั้ง Workflows = Read and write ด้วย
• เขียนทับ: เปิดตัวเลือก แล้วแอปจะอ่าน SHA ของไฟล์เดิมก่อนส่ง PUT
• ลบ: เลือกโหมดลบ แล้วเลือก ZIP ที่มี path ของไฟล์เป้าหมาย ยืนยันก่อนลบ
• สิทธิ์ push ที่ Repository API รายงานเป็นเพียงสัญญาณเบื้องต้น ไม่รับประกันว่า Token/Branch rules อนุญาตทุกคำสั่ง
• จำกัด 500 ไฟล์ และขนาดรวมหลังแตก ZIP 150 MB
• ไม่บันทึก Token ลง SharedPreferences; อย่าแชร์ Token และเพิกถอนทันทีหากหลุด
• การเขียนทับและลบจะสร้าง commit จริงใน Branch ที่ตั้งไว้""") }
    private fun setLog(s: String) { if (::log.isInitialized) { val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()); logLines.append("[$time] ").append(s).append("\n\n"); if (logLines.length > 18000) logLines.delete(0, logLines.length - 14000); log.text = logLines.toString(); log.post { val layout = log.layout; if (layout != null) log.scrollTo(0, layout.getLineTop(log.lineCount) - log.height) } } }
}
