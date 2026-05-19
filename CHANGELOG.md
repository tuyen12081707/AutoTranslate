# Auto Translate Strings Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.1] - 2026-05-19
### Added
- 🚀 Added "Auto Translate Strings" context menu action specifically for `strings.xml` files.
- 🌍 Integrated automatic translation from English to 9 target languages: German, Spanish, French, Hindi, Indonesian, Italian, Japanese, Korean, and Portuguese.
- 🛡️ Implemented smart diffing: Only missing string keys are translated, safely preserving any existing translations.
- 📝 Added safe XML appending: New strings are injected just before the `</resources>` tag to preserve existing formatting, spacing, and comments.
- ⚡ Added background processing with a native IDE progress bar to prevent Android Studio UI from freezing.
- 🔒 Implemented an anti-spam lock mechanism that disables the translate button while a background task is running.
- 🛠️ Added automatic text escaping for Android-specific special characters (`'`, `"`, `\n`).