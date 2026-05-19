# Auto Translate Strings

![Build](https://github.com/tuyen12081707/AutoTranslate/workflows/Build/badge.svg)

**Auto Translate Strings** is a powerful and essential plugin for Android developers working in Android Studio and IntelliJ IDEA. It helps you automatically translate your base `strings.xml` file into multiple target languages with just a single click.

## ✨ Features

- **🚀 1-Click Translation:** Right-click on your `strings.xml` file or anywhere inside the editor and select "Auto Translate Strings" to start the magic.
- **🌍 Multi-Language Support:** Automatically generates translations for popular languages (German, Spanish, French, Hindi, Indonesian, Italian, Japanese, Korean, Portuguese).
- **🛡️ Safe & Smart:** Only translates missing keys. If you already have existing translations, it will safely skip them.
- **📝 Preserves Formatting:** Appends new strings directly to your XML files without messing up your existing layout, comments, or XML tags.
- **⚡ Background Processing:** Runs asynchronously with a native IDE progress bar, ensuring your Android Studio never freezes during the translation process.
- **🔒 Anti-Spam Lock:** Automatically prevents multiple accidental clicks while a translation task is running.

## 🚀 Usage

1. Open your Android project in Android Studio / IntelliJ IDEA.
2. Locate your base English `strings.xml` file (inside the `res/values/` directory).
3. **Right-click** on the file in the Project tree (or right-click directly inside the code editor).
4. Select **Auto Translate Strings** from the context menu.
5. Sit back and watch the progress bar as your app becomes multilingual!

## 📦 Installation

- **Using the IDE built-in plugin system:**
  Go to <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > Search for `"Auto Translate Strings"` > <kbd>Install</kbd>.

- **Manual Installation:**
  1. Download the latest `.zip` release from the [Releases](https://github.com/tuyen12081707/AutoTranslate/releases) page.
  2. Go to <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>
  3. Select the downloaded `.zip` file and restart your IDE.

## 🛠️ Tech Stack
- Kotlin
- IntelliJ Platform SDK
- Google Translate API

---
*Developed with ❤️ to boost Android Development productivity.*
