import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension
    intellijPlatform {
        // Dùng 2024.1.1 (Mã 241) để tương thích với Android Studio của bạn (Mã 251)
        intellijIdea("2024.1.1")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Mở khóa cho các bản IDE từ 2024.1 (Mã 241) trở lên
            sinceBuild.set("241")

            // Để trống để không giới hạn trần, thoải mái chạy trên IDE tương lai
            untilBuild.set("")
        }
    }
}