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
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AutoTranslateAction : AnAction() {

    private val TARGET_LANGUAGES = listOf("de", "es", "fr", "hi", "in", "it", "ja", "ko", "pt")

    @Volatile
    private var isProcessing = false

    override fun actionPerformed(e: AnActionEvent) {
        if (isProcessing) return

        var virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (virtualFile == null && psiFile != null) {
            virtualFile = psiFile.virtualFile
        }

        val project = e.project ?: return

        // 📍 FIX 1: Hỗ trợ cả strings.xml và string.xml
        val fileName = virtualFile?.name
        if (fileName != "strings.xml" && fileName != "string.xml") {
            Messages.showWarningDialog("Vui lòng click chuột phải chính xác vào file strings.xml hoặc string.xml", "Sai Vị Trí")
            return
        }

        val valuesDir = virtualFile.parent
        val resDir = valuesDir?.parent ?: return

        isProcessing = true

        // Tham số thứ 3 là "true" để cho phép Hủy task (Nút X)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Đang Dịch Auto Translate...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Đang đọc file gốc..."

                    val baseStrings = parseXmlToMap(virtualFile)
                    if (baseStrings.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog("File $fileName gốc không có dữ liệu!", "Cảnh Báo")
                        }
                        return
                    }

                    var totalAdded = 0

                    for ((index, lang) in TARGET_LANGUAGES.withIndex()) {
                        if (indicator.isCanceled) break

                        indicator.text = "Đang dịch sang ngôn ngữ: $lang (${index + 1}/${TARGET_LANGUAGES.size})"
                        indicator.fraction = index.toDouble() / TARGET_LANGUAGES.size

                        val targetDirName = "values-$lang"
                        val targetMap = mutableMapOf<String, String>()

                        val targetDir = resDir.findChild(targetDirName)
                        val targetFile = targetDir?.findChild(fileName) // 📍 Dùng tên file động

                        var existingFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"

                        if (targetFile != null && targetFile.exists()) {
                            targetMap.putAll(parseXmlToMap(targetFile))
                            existingFileContent = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
                        }

                        val missingKeys = baseStrings.keys.filter { !targetMap.containsKey(it) }
                        if (missingKeys.isEmpty()) continue

                        val newStringsBuilder = StringBuilder()

                        for (key in missingKeys) {
                            if (indicator.isCanceled) break

                            val rawOriginalText = baseStrings[key] ?: continue
                            try {
                                indicator.text2 = "Đang dịch key: $key"

                                // 📍 FIX 2: Xử lý chuỗi trước và sau khi dịch để bảo toàn cấu trúc
                                val cleanTextToTranslate = prepareTextForTranslation(rawOriginalText)
                                val translatedText = callGoogleTranslate(cleanTextToTranslate, lang)
                                val escapedText = escapeAndroidText(translatedText)

                                newStringsBuilder.append("    <string name=\"$key\">$escapedText</string>\n")
                                totalAdded++

                                Thread.sleep(500)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }

                        if (indicator.isCanceled) break

                        if (newStringsBuilder.isNotEmpty()) {
                            ApplicationManager.getApplication().invokeAndWait {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    try {
                                        val dir = resDir.findChild(targetDirName) ?: resDir.createChildDirectory(this, targetDirName)
                                        val file = dir.findChild(fileName) ?: dir.createChildData(this, fileName)

                                        val endTagIndex = existingFileContent.lastIndexOf("</resources>")
                                        val updatedContent = if (endTagIndex != -1) {
                                            existingFileContent.substring(0, endTagIndex) + newStringsBuilder.toString() + existingFileContent.substring(endTagIndex)
                                        } else {
                                            existingFileContent + "\n" + newStringsBuilder.toString() + "\n</resources>"
                                        }

                                        file.setBinaryContent(updatedContent.toByteArray(Charsets.UTF_8))
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                }
                            }
                        }
                    }

                    if (!indicator.isCanceled) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage("Đã thêm $totalAdded chuỗi mới!", "Dịch Thành Công")
                        }
                    }

                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Lỗi trong quá trình dịch: ${e.message}", "Lỗi")
                    }
                }
            }

            override fun onFinished() {
                isProcessing = false
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = !isProcessing
    }

    // ==========================================
    // CÁC HÀM TIỆN ÍCH HỖ TRỢ (ĐÃ FIX CHUẨN)
    // ==========================================

    // 📍 FIX 3: Chuyển sang dùng Regex để lấy TEXT THỰC TẾ, không bị mất thẻ HTML hay CDATA
    private fun parseXmlToMap(file: VirtualFile): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val content = String(file.contentsToByteArray(), Charsets.UTF_8)

            // Tìm tất cả khối <string name="...">...</string>
            val regex = Regex("""<string\s+name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(content)

            for (match in matches) {
                val name = match.groupValues[1]
                val attributes = match.groupValues[2]
                val text = match.groupValues[3] // Lấy đúng đoạn text thô bên trong (Bao gồm cả &amp;, CDATA...)

                if (attributes.contains("translatable=\"false\"")) continue

                map[name] = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    // 📍 Loại bỏ các ký tự bọc (CDATA, escape code) để Google dịch tự nhiên nhất
    private fun prepareTextForTranslation(rawText: String): String {
        return rawText
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
    }

    // 📍 FIX 4: Đóng gói lại chuẩn XML + Chuẩn Android + Sửa lỗi placeholder của Google
    private fun escapeAndroidText(text: String): String {
        var escaped = text
            // Chống lỗi nếu Google tự trả về dạng XML (&amp;)
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

            // Escape chuẩn XML (Bắt buộc phải có để XML không bị báo lỗi)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

            // Escape chuẩn Android (Dấu nháy, ngoặc kép, xuống dòng)
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        // 📍 Fix lỗi chí mạng: Google Translate hay tự thêm dấu cách vào Format String của Android (VD: % s -> %s, % 1 $ s -> %1$s)
        escaped = escaped.replace(Regex("%\\s+s"), "%s")
            .replace(Regex("%\\s+d"), "%d")
            .replace(Regex("%\\s+([0-9]+)\\s+\\$\\s+s"), "%\$1\\\$s")
            .replace(Regex("%\\s+([0-9]+)\\s+\\$\\s+d"), "%\$1\\\$d")

        return escaped
    }

    private fun callGoogleTranslate(text: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=$targetLang&dt=t&q=$encodedText"

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            throw RuntimeException("Google Error: $responseCode")
        }

        val response = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { it.readText() }

        val jsonArray = JsonParser.parseString(response).asJsonArray
        val sentencesArray = jsonArray.get(0).asJsonArray

        val resultBuilder = StringBuilder()
        for (i in 0 until sentencesArray.size()) {
            resultBuilder.append(sentencesArray.get(i).asJsonArray.get(0).asString)
        }

        return resultBuilder.toString()
    }
}