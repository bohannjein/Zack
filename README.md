&lt;div align="center"&gt;

&lt;img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" /&gt;
&lt;img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white" /&gt;
&lt;img src="https://img.shields.io/badge/Material%20Design%203-757575?style=for-the-badge&logo=material-design&logoColor=white" /&gt;

# ⚡Zack! – Instant SMB File Uploads

**Stop emailing files to yourself. Start "Zacking" them.**

Zack is the fastest, most lightweight way to beam photos, documents, and videos from your Android device directly to your home server, NAS, or PC. No cables, no cloud delays, no privacy concerns.

&lt;p align="center"&gt;
  &lt;img src="screenshots/preview.png" width="32%" /&gt;
  &lt;img src="screenshots/screenshot-2.png" width="32%" /&gt;
  &lt;img src="screenshots/screenshot-settings.png" width="32%" /&gt;
&lt;/p&gt;

&lt;/div&gt;

## Why Zack?

Most file transfers are a chore: you either need a USB cable or you have to upload your private data to a third-party cloud just to get it onto your computer. Zack changes the game by creating a direct, secure bridge between your phone and your local network (SMB).

### Key Features

**One-Tap Sharing**: Use the standard Android "Share" menu from any app (Gallery, Files, Browser) and select Zack. Your file is gone and uploaded before you can say "Zack!".

**Privacy First**: Your data never leaves your local network. Zack uses the SMB protocol to talk directly to your devices. No external servers, no tracking, no middleman.

**Biometric Security**: Keep your server configurations safe. Protect the app with your fingerprint or face scan so only you can access your transfer history and settings.

**Transfer History**: A clean, visual log of all your uploads. See exactly what was sent, when, and to which server.

**Modern & Clean**: Built with the latest design standards. Features a beautiful, centered navigation and a true AMOLED dark mode for battery saving and style.

**Stay Informed**: Get optional notifications about your upload status, even when the app is in the background.

## How it works

1. **Configure** your SMB server (NAS, Windows Share, Mac, or Linux) once
2. **Select** a file on your phone
3. **Hit "Share"** and pick Zack
4. **Done.** Zack!

Whether you are a photographer moving shots to a NAS, a student backing up PDFs, or just someone who hates cables – Zack is the tool you've been waiting for.

## Installation

### Requirements
- Android 8.0 (API 26) or higher
- Internet access for cloud storage connections

### Build from Source
```bash
git clone https://github.com/bohannjein/zack.git
cd zack
./gradlew assembleRelease
