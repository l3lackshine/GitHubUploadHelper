package com.blackshine.githubuploadhelper

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    private lateinit var selectedLabel: TextView
    private var selected: Uri? = null
    private var zipEntries: List<ZipEntryData> = emptyList()
    private var pickedIsZip = false

    data class ZipEntryData(val path: String, val bytes: ByteArray)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(0xFFF3F5F9.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        content.addView(TextView(this).apply {
            text = "GitHub Upload Helper"
            textSize = 25f; setTextColor(0xFF172554.toInt()); gravity = Gravity.CENTER
        })
        content.addView(TextView(this).apply {
            text = "จัดการไฟล์ ZIP และอัปโหลด GitHub แบบไม่ต้องพิมพ์คำสั่ง"
            textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 14)
        })
        token = field(content, "GitHub Personal Access Token", "วาง Token ที่นี่")
        token.inputType = 129
        owner = field(content, "Owner", "เช่น l3lackshine")
        repo = field(content, "Repository", "เช่น Dummy-Online")
        branch = field(content, "Branch", "main")
        dest = field(content, "โฟลเดอร์ปลายทางใน GitHub (ไม่บังคับ)", "เว้นว่างเพื่อใช้โครงสร้างใน ZIP")
        message = field(content, "ข้อความ Commit", "Upload project from Android")
        button(content, "1. เลือกไฟล์ ZIP") { pickZip() }
        selectedLabel = TextView(this).apply {
            text = "ยังไม่ได้เลือก ZIP"; textSize = 14f; setPadding(8, 10, 8, 10)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(selectedLabel)
        button(content, "2. แตก ZIP และแสดงรายการไฟล์") { runTask { extractZip() } }
        button(content, "3. ตรวจสอบ Token / Repository / Branch") { runTask { checkAll() } }
        button(content, "4. อัปโหลดไฟล์ทั้งหมดใน ZIP") { runTask { uploadAll() } }
        button(content, "คู่มือแก้ปัญหา") { showHelp() }
        log = TextView(this).apply {
            text = "สถานะ: พร้อมใช้งาน"; textSize = 14f
            setTextColor(0xFF111827.toInt()); setPadding(12, 16, 12, 16)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(log, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        content.addView(TextView(this).apply {
            text = "Token ใช้เชื่อมต่อ GitHub โดยตรงผ่าน HTTPS อย่าแชร์ Token ให้ผู้อื่น รุ่นนี้ยังไม่รองรับเขียนทับไฟล์เดิมอัตโนมัติ"
            textSize = 12f; setPadding(4, 12, 4, 8)
        })
    }

    private fun field(parent: LinearLayout, label: String, hint: String): EditText {
        parent.addView(TextView(this).apply { text = label; textSize = 14f; setPadding(2, 9, 2, 3) })
        val e = EditText(this).apply {
            this.hint = hint; textSize = 15f; singleLine = true
            setPadding(12, 8, 12, 8); setBackgroundColor(0xFFFFFFFF.toInt())
        }
        parent.addView(e, LinearLayout.LayoutParams(-1, -2))
        return e
    }
    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = 5 })
    }
    private fun pickZip() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
        }
        startActivityForResult(i, 41)
    }
    @Deprecated("Deprecated Android callback retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            selected = data?.data
            zipEntries = emptyList()
            pickedIsZip = true
            selectedLabel.text = "เลือก ZIP แล้ว: ${selected?.lastPathSegment ?: "ไฟล์ ZIP"}"
            setLog("เลือกไฟล์ ZIP แล้ว กด “แตก ZIP และแสดงรายการไฟล์”")
        }
    }
    private fun runTask(task: () -> String) {
        setLog("กำลังทำงาน…")
        executor.execute {
            val result = try { task() } catch (e: Exception) {
                "ผิดพลาด: ${e.message ?: e.javaClass.simpleName}\n\n${diagnose(e.message ?: "")}"
            }
            runOnUiThread { setLog(result) }
        }
    }
    private fun values(): Triple<String, String, String> {
        val t = token.text.toString().trim()
        val o = owner.text.toString().trim()
        val r = repo.text.toString().trim()
        if (t.isEmpty() || o.isEmpty() || r.isEmpty()) throw Exception("กรอก Token, Owner และ Repository ให้ครบ")
        return Triple(t, o, r)
    }
    private fun readZip(): List<ZipEntryData> {
        val uri = selected ?: throw Exception("กรุณาเลือกไฟล์ ZIP ก่อน")
        val out = mutableListOf<ZipEntryData>()
        contentResolver.openInputStream(uri).use { raw ->
            if (raw == null) throw Exception("เปิด ZIP ไม่ได้")
            ZipInputStream(raw).use { zis ->
                var entry = zis.nextEntry
                var total = 0L
                while (entry != null) {
                    val name = entry.name.replace("\\\\", "/")
                    val safe = name.split("/").filter { it.isNotEmpty() }
                    if (name.startsWith("/") || safe.any { it == ".." } || name.contains(":")) {
                        throw Exception("พบ path ที่ไม่ปลอดภัยใน ZIP: $name")
                    }
                    if (!entry.isDirectory && !name.endsWith("/")) {
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = zis.read(chunk)
                            if (n <= 0) break
                            total += n
                            if (total > 150L * 1024 * 1024) throw Exception("ZIP แตกแล้วมีขนาดเกิน 150 MB")
                            buffer.write(chunk, 0, n)
                        }
                        out.add(ZipEntryData(name, buffer.toByteArray()))
                        if (out.size > 500) throw Exception("ZIP มีไฟล์เกิน 500 ไฟล์ในรุ่นนี้")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        if (out.isEmpty()) throw Exception("ไม่พบไฟล์ภายใน ZIP")
        return out
    }
    private fun extractZip(): String {
        zipEntries = readZip()
        pickedIsZip = true
        val listing = zipEntries.take(80).joinToString("\n") {
            "• ${it.path} (${it.bytes.size} bytes)"
        }
        return "แตก ZIP ในหน่วยความจำสำเร็จ\nพบ ${zipEntries.size} ไฟล์\n\n$listing" +
            if (zipEntries.size > 80) "\n…และอีก ${zipEntries.size - 80} ไฟล์" else ""
    }
    private fun request(method: String, endpoint: String, body: JSONObject? = null): Pair<Int, String> {
        val (t, _, _) = values()
        val c = URL("https://api.github.com$endpoint").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000; c.readTimeout = 30000
        c.setRequestProperty("Authorization", "Bearer $t")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code in 200..399) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
    private fun checkAll(): String {
        val (_, o, r) = values()
        val (uc, ub) = request("GET", "/user")
        if (uc !in 200..299) throw Exception("Token ตรวจไม่ผ่าน HTTP $uc\n$ub")
        val username = JSONObject(ub).optString("login", "unknown")
        val (rc, rb) = request("GET", "/repos/${enc(o)}/${enc(r)}")
        if (rc !in 200..299) throw Exception("อ่าน Repository ไม่สำเร็จ HTTP $rc\n$rb")
        val defaultBranch = JSONObject(rb).optString("default_branch", "main")
        val b = branch.text.toString().trim().ifEmpty { defaultBranch }
        val (bc, bb) = request("GET", "/repos/${enc(o)}/${enc(r)}/branches/${enc(b)}")
        if (bc !in 200..299) throw Exception("ไม่พบ Branch '$b' HTTP $bc\n$bb")
        return "ตรวจสอบผ่าน\nToken account: $username\nRepository: $o/$r\nBranch: $b"
    }
    private fun uploadAll(): String {
        val (_, o, r) = values()
        if (zipEntries.isEmpty()) zipEntries = readZip()
        val b = branch.text.toString().trim().ifEmpty { "main" }
        val prefix = dest.text.toString().trim().trim('/')
        val entries = zipEntries
        val results = StringBuilder("เริ่มอัปโหลด ${entries.size} ไฟล์\n")
        var ok = 0
        for ((i, item) in entries.withIndex()) {
            val path = listOf(prefix, item.path).filter { it.isNotBlank() }.joinToString("/")
            val endpoint = "/repos/${enc(o)}/${enc(r)}/contents/" +
                path.split("/").joinToString("/") { enc(it) }
            val payload = JSONObject()
                .put("message", message.text.toString().ifBlank { "Upload project from Android" } + " (${i + 1}/${entries.size})")
                .put("content", Base64.encodeToString(item.bytes, Base64.NO_WRAP))
                .put("branch", b)
            val (code, body) = request("PUT", endpoint, payload)
            if (code in 200..299) {
                ok++
                results.append("✓ ").append(path).append("\n")
            } else {
                results.append("✗ ").append(path).append(" — HTTP ").append(code)
                    .append(": ").append(body.take(220)).append("\n")
            }
            runOnUiThread { setLog("กำลังอัปโหลด ${i + 1}/${entries.size}\nสำเร็จ $ok ไฟล์") }
        }
        return "เสร็จสิ้น: สำเร็จ $ok/${entries.size} ไฟล์\n\n$results\nถ้าไฟล์มีอยู่แล้ว อาจต้องอัปโหลดไม่สำเร็จเพราะรุ่นนี้ยังไม่จัดการ SHA เพื่อเขียนทับ"
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun diagnose(msg: String): String {
        val m = msg.lowercase()
        return when {
            "401" in m || "bad credentials" in m -> "401: Token ไม่ถูกต้อง หมดอายุ หรือถูกเพิกถอน"
            "403" in m || "resource not accessible" in m -> "403: ให้ Token มีสิทธิ์ Contents: Read and write และเลือก Repository ที่ถูกต้อง"
            "404" in m || "not found" in m -> "404: ตรวจ Owner, Repository, Branch และสิทธิ์ Token"
            "409" in m || "sha" in m -> "409: ไฟล์อาจมีอยู่แล้ว รุ่นนี้ยังไม่รองรับเขียนทับไฟล์เดิมอัตโนมัติ"
            "timeout" in m || "failed to connect" in m -> "เครือข่าย: ตรวจอินเทอร์เน็ตและลองใหม่"
            else -> "ตรวจ Token, สิทธิ์ Contents: Read and write, Branch, ชื่อ path และอินเทอร์เน็ต"
        }
    }
    private fun showHelp() {
        setLog("""วิธีแก้ปัญหา
401 — สร้าง Token ใหม่หากหมดอายุหรือไม่ถูกต้อง
403 — ตั้ง Contents เป็น Read and write และเลือก Repository ที่ถูกต้อง
404 — ตรวจ Owner / Repository / Branch / path
409 หรือไฟล์มีอยู่แล้ว — รุ่นนี้ยังไม่รองรับเขียนทับไฟล์เดิม
อัปโหลดบางไฟล์ไม่ผ่าน — ตรวจรายงานแต่ละไฟล์และแก้ตาม HTTP code
ZIP — จำกัด 500 ไฟล์และขนาดหลังแตก 150 MB; ZIP ที่มี path ไม่ปลอดภัยจะถูกปฏิเสธ
เพื่อความปลอดภัยอย่าแชร์ Token และใช้สิทธิ์เท่าที่จำเป็น""")
    }
    private fun setLog(s: String) { if (::log.isInitialized) log.text = s }
}
