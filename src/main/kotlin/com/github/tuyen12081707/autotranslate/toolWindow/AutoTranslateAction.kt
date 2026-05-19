package com.github.tuyen12081707.autotranslate.toolWindow

import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.Element
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.collections.iterator

class AutoTranslateAction : AnAction() {

    // Danh sách ngôn ngữ giống hệt file Python của bạn
    private val TARGET_LANGUAGES = listOf("de", "es", "fr", "hi", "in", "it", "ja", "ko", "pt")

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project ?: return

        if (virtualFile == null || virtualFile.name != "strings.xml") {
            Messages.showErrorDialog("Vui lòng click chuột phải vào file strings.xml", "Lỗi File")
            return
        }

        // Thư mục 'values' chứa file strings.xml gốc
        val valuesDir = virtualFile.parent
        // Thư mục 'res'
        val resDir = valuesDir.parent

        // 📍 BẮT ĐẦU CHẠY NGẦM (BACKGROUND TASK) ĐỂ KHÔNG ĐƠ ANDROID STUDIO
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Đang Dịch Auto Translate...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Đang đọc file gốc..."

                    // 1. Đọc strings gốc
                    val baseStrings = parseXmlToMap(virtualFile)
                    if (baseStrings.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog("File strings.xml gốc không có dữ liệu!", "Cảnh Báo")
                        }
                        return
                    }

                    var totalAdded = 0

                    // Lặp qua từng ngôn ngữ
                    for ((index, lang) in TARGET_LANGUAGES.withIndex()) {
                        indicator.text = "Đang dịch sang ngôn ngữ: $lang (${index + 1}/${TARGET_LANGUAGES.size})"
                        indicator.fraction = index.toDouble() / TARGET_LANGUAGES.size

                        val targetDirName = "values-$lang"
                        // Tạo hoặc lấy thư mục values-xx và strings.xml tương ứng (Phải bọc trong Read/Write Action)
                        val targetMap = mutableMapOf<String, String>()

                        // Lấy thư mục và file XML mục tiêu nếu có
                        val targetDir = resDir.findChild(targetDirName)
                        val targetFile = targetDir?.findChild("strings.xml")

                        if (targetFile != null && targetFile.exists()) {
                            targetMap.putAll(parseXmlToMap(targetFile))
                        }

                        // Kiểm tra từ nào chưa dịch
                        val missingKeys = baseStrings.keys.filter { !targetMap.containsKey(it) }
                        if (missingKeys.isEmpty()) continue

                        // Bắt đầu gọi API dịch
                        for (key in missingKeys) {
                            val originalText = baseStrings[key] ?: continue
                            try {
                                indicator.text2 = "Đang dịch key: $key"
                                val translatedText = callGoogleTranslate(originalText, lang)
                                val escapedText = escapeAndroidText(translatedText)
                                targetMap[key] = escapedText
                                totalAdded++

                                // Ngủ 1 chút để tránh Google block IP vì spam (Rate limit)
                                Thread.sleep(200)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }

                        // 📍 LƯU FILE MỚI: Phải gọi qua WriteCommandAction trên Main Thread
                        ApplicationManager.getApplication().invokeAndWait {
                            WriteCommandAction.runWriteCommandAction(project) {
                                try {
                                    // Tạo thư mục nếu chưa có
                                    val dir = resDir.findChild(targetDirName) ?: resDir.createChildDirectory(this, targetDirName)
                                    // Tạo file nếu chưa có
                                    val file = dir.findChild("strings.xml") ?: dir.createChildData(this, "strings.xml")

                                    // Build nội dung XML
                                    val xmlContent = buildXmlString(targetMap)
                                    file.setBinaryContent(xmlContent.toByteArray(Charsets.UTF_8))
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    }

                    // Thông báo hoàn tất
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage("Đã thêm $totalAdded chuỗi mới!", "Dịch Thành Công")
                    }

                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Lỗi trong quá trình dịch: ${e.message}", "Lỗi")
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        // Tạm thời tắt hết mọi điều kiện check để ÉP NÓ HIỂN THỊ ra đã!
        e.presentation.isEnabledAndVisible = true
    }

    // ==========================================
    // CÁC HÀM TIỆN ÍCH HỖ TRỢ
    // ==========================================

    // Hàm đọc file XML thành Map<Key, Text>
    private fun parseXmlToMap(file: VirtualFile): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file.inputStream)
            val nodes = doc.getElementsByTagName("string")

            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                // Bỏ qua các string có cờ translatable="false"
                if (element.getAttribute("translatable") == "false") continue

                val name = element.getAttribute("name")
                val text = element.textContent
                if (name.isNotEmpty()) {
                    map[name] = text
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // Hàm gọi API Google Translate (Miễn phí)
    private fun callGoogleTranslate(text: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encodedText"

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        val response = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }

        // Response trả về là 1 array JSON phức tạp, ví dụ: [[["Chào","Hello",...], [" bạn"," you",...]]]
        // Ta dùng JsonParser có sẵn của IntelliJ để bóc tách
        val jsonArray = JsonParser.parseString(response).asJsonArray
        val sentencesArray = jsonArray.get(0).asJsonArray

        val resultBuilder = StringBuilder()
        for (i in 0 until sentencesArray.size()) {
            resultBuilder.append(sentencesArray.get(i).asJsonArray.get(0).asString)
        }

        return resultBuilder.toString()
    }

    // Hàm xử lý ký tự y hệt bên Python
    private fun escapeAndroidText(text: String): String {
        return text.replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") // Fix luôn lỗi xuống dòng XML
    }

    // Build cấu trúc file strings.xml từ Map
    private fun buildXmlString(map: Map<String, String>): String {
        val sb = java.lang.StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<resources>\n")

        // Sort lại cho đẹp (tuỳ chọn)
        for ((key, value) in map.toSortedMap()) {
            sb.append("    <string name=\"$key\">$value</string>\n")
        }

        sb.append("</resources>")
        return sb.toString()
    }
}