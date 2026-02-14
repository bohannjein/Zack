# ⚡ Zack - Network Storage Drop

&lt;p align="center"&gt;
  &lt;img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" /&gt;
  &lt;img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white" /&gt;
  &lt;img src="https://img.shields.io/badge/Material%20Design%203-757575?style=for-the-badge&logo=material-design&logoColor=white" /&gt;
&lt;/p&gt;

**Zack** is a modern Android app for quickly uploading files to network storage (NAS, Servers, Cloud Storage). Supports SMB, SFTP, FTP, and WebDAV with an intuitive Material You design.

&lt;p align="center"&gt;
  &lt;img src="screenshots/preview.png" width="300" /&gt;
&lt;/p&gt;

## ✨ Features

- **Multi-Protocol**: SMB/CIFS, SFTP, FTP, WebDAV support
- **Quick Share**: Share files directly from other apps to Zack
- **Auto-Discovery**: Automatically finds SMB servers in your local network (NSD)
- **Biometric Protection**: App lock with fingerprint/face recognition
- **Background Uploads**: Works with Android WorkManager (even when app is closed)
- **Smart Renaming**: Optionally append timestamps to filenames
- **Material You**: Dynamic colors, Light/Dark/AMOLED themes
- **Offline Support**: Local database (Room) for server configurations

## 🚀 Tech Stack

- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM, Coroutines, Flow
- **Database**: Room (SQLite)
- **Security**: EncryptedSharedPreferences for password storage
- **Background Processing**: WorkManager for uploads
- **Network**: NSD Service Discovery for local devices
- **Biometrics**: AndroidX Biometric Prompt

## 📱 Installation

### Requirements
- Android 8.0 (API 26) or higher
- Internet access for cloud storage connections

### Build from Source
```bash
git clone https://github.com/bohannjein/zack.git
cd zack
./gradlew assembleRelease
