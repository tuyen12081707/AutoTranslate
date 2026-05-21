# Auto Translate Strings Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# Auto Translate Strings Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.2] - 2026-05-21
### Added
- 🛑 **Task Cancellation:** Added support for the Cancel (X) button on the IDE progress bar. You can now instantly abort an ongoing background translation process without freezing or restarting Android Studio.
- 📁 **Dynamic File Support:** The plugin now supports and correctly generates both `strings.xml` and `string.xml` based on the source file clicked.

### Fixed
- 🧩 **Preserved XML/HTML Structure:** Replaced the DOM parser with a custom Regex parser. HTML tags (e.g., `<b>`, `<i>`) and `<![CDATA[...]]>` blocks inside strings are no longer stripped out during the read process.
- 🐛 **Android Placeholder Fix:** Resolved a critical issue where the translation API would inject invalid spaces into Android format specifiers (e.g., breaking `%1$s` into `% 1 $ s`). Placeholders are now automatically sanitized and restored.
- 🛡️ **XML Entity Escaping:** Fixed XML syntax errors in the generated output by ensuring special characters (`&`, `<`, `>`) returned from the translation API are properly escaped as `&amp;`, `&lt;`, and `&gt;`.

## [0.0.1] - 2026-05-19
### Added
- 🚀 Added "Auto Translate Strings" context menu action specifically for `strings.xml` files.
- 🌍 Integrated automatic translation from English to 9 target languages: German, Spanish, French, Hindi, Indonesian, Italian, Japanese, Korean, and Portuguese.
- 🛡️ Implemented smart diffing: Only missing string keys are translated, safely preserving any existing translations.
- 📝 Added safe XML appending: New strings are injected just before the `</resources>` tag to preserve existing formatting, spacing, and comments.
- ⚡ Added background processing with a native IDE progress bar to prevent Android Studio UI from freezing.
- 🔒 Implemented an anti-spam lock mechanism that disables the translate button while a background task is running.
- 🛠️ Added automatic text escaping for Android-specific special characters (`'`, `"`, `\n`).