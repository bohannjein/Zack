# Changelog

## v1.0 — The Real One

> From "it works on my phone" to something you'd actually trust with your files.

### What's New

- Upload progress now lives in the notification shade — no more staring at the app while your file transfers
- Upload count badge on the History tab so you always know what's in flight
- Swipe any item left or right to delete it — works on both history and servers
- Cancel an in-progress upload directly from the History list
- Added an accent color picker in Settings — 8 colors to choose from
- History retention setting: keep uploads Forever, 90, 30, or 7 days
- "Test Connection" button on server setup (SMB) — know before you save
- Password field now has a show/hide toggle
- SFTP, FTP, and WebDAV show a "Coming Soon" badge instead of silently doing nothing
- Friendly error messages for failed uploads — no more raw stack traces in your history

### Fixes

- AMOLED theme now actually goes full black — Scaffold background, nav bar, all of it
- Navigation pill indicator is centered correctly on both tabs
- Uploads no longer silently vanish on network hiccup — WorkManager retries up to 3× on IOException, then marks as failed
- Server passwords are stored with proper AES-256 encryption and excluded from Google backup
- Keystore credentials moved out of the build file

### Under the Hood

- Dropped all alpha/pre-release dependencies — biometric and security-crypto are now on stable releases
- Database migration preserved (no destructive reset on upgrade)
- History entries now record file size, error reason, and work tag for cancellation
- NSD (local network scan) handles Android 14's new ServiceInfoCallback API
