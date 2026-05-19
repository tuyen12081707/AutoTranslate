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

        if (virtualFile == null || !virtualFile.name.contains("strings")) {
            Messages.showWarningDialog("Vui lòng click chuột phải chính xác vào file strings.xml (hoặc nội dung bên trong file)", "Sai Vị Trí")
            return
        }


        val valuesDir = virtualFile.parent
        val resDir = valuesDir?.parent ?: return

        isProcessing = true

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Đang Dịch Auto Translate...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Đang đọc file gốc..."

                    val baseStrings = parseXmlToMap(virtualFile)
                    if (baseStrings.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog("File strings.xml gốc không có dữ liệu!", "Cảnh Báo")
                        }
                        return
                    }

                    var totalAdded = 0

                    for ((index, lang) in TARGET_LANGUAGES.withIndex()) {
                        indicator.text = "Đang dịch sang ngôn ngữ: $lang (${index + 1}/${TARGET_LANGUAGES.size})"
                        indicator.fraction = index.toDouble() / TARGET_LANGUAGES.size

                        val targetDirName = "values-$lang"
                        val targetMap = mutableMapOf<String, String>()

                        val targetDir = resDir.findChild(targetDirName)
                        val targetFile = targetDir?.findChild("strings.xml")

                        // 📍 1. ĐỌC NỘI DUNG GỐC CỦA FILE (DẠNG TEXT) ĐỂ GIỮ NGUYÊN FORMAT/COMMENT
                        var existingFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"

                        if (targetFile != null && targetFile.exists()) {
                            targetMap.putAll(parseXmlToMap(targetFile))
                            existingFileContent = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
                        }

                        val missingKeys = baseStrings.keys.filter { !targetMap.containsKey(it) }
                        if (missingKeys.isEmpty()) continue

                        // 📍 2. CHỈ TẠO CHUỖI XML MỚI CHO NHỮNG TỪ BỊ THIẾU
                        val newStringsBuilder = StringBuilder()

                        for (key in missingKeys) {
                            val originalText = baseStrings[key] ?: continue
                            try {
                                indicator.text2 = "Đang dịch key: $key"
                                val translatedText = callGoogleTranslate(originalText, lang)
                                val escapedText = escapeAndroidText(translatedText)

                                newStringsBuilder.append("    <string name=\"$key\">$escapedText</string>\n")
                                totalAdded++

                                // 📍 Nghỉ 500ms để Google không block IP
                                Thread.sleep(500)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }

                        // 📍 3. NHÉT CÁC CHUỖI MỚI VÀO TRƯỚC THẺ </resources> CỦA FILE CŨ
                        if (newStringsBuilder.isNotEmpty()) {
                            ApplicationManager.getApplication().invokeAndWait {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    try {
                                        val dir = resDir.findChild(targetDirName) ?: resDir.createChildDirectory(this, targetDirName)
                                        val file = dir.findChild("strings.xml") ?: dir.createChildData(this, "strings.xml")

                                        val endTagIndex = existingFileContent.lastIndexOf("</resources>")
                                        val updatedContent = if (endTagIndex != -1) {
                                            // Cắt file ra nhét chuỗi mới vào giữa
                                            existingFileContent.substring(0, endTagIndex) + newStringsBuilder.toString() + existingFileContent.substring(endTagIndex)
                                        } else {
                                            // Đề phòng file lỗi không có thẻ đóng
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

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage("Đã thêm $totalAdded chuỗi mới!", "Dịch Thành Công")
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
        // 📍 ÉP HIỂN THỊ Ở MỌI NƠI! Không thèm check tên file hay đường dẫn nữa.
        // Chỉ mờ đi khi đang bận dịch (isProcessing = true)
        e.presentation.isVisible = true
        e.presentation.isEnabled = !isProcessing
    }
    // ==========================================
    // CÁC HÀM TIỆN ÍCH HỖ TRỢ
    // ==========================================

    private fun parseXmlToMap(file: VirtualFile): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file.inputStream)
            val nodes = doc.getElementsByTagName("string")

            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
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

    private fun callGoogleTranslate(text: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=$targetLang&dt=t&q=$encodedText"

        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        // 📍 FIX KẸT TIẾN TRÌNH: Thêm Timeout 5 giây
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

    private fun escapeAndroidText(text: String): String {
        return text.replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}