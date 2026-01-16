# HotspotApp - WiFi Security Tester

## Project Overview
Android app to scan nearby WiFi networks and test connection with passwords from a text file. For testing security of your own networks.

## User Info
- **GitHub Username:** xeshP
- **Email:** nicolp123123@gmail.com
- **Device:** Google Pixel 9 Pro (Android 10+)

## Tech Stack
- **Language:** Kotlin
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 34
- **Build:** Gradle Kotlin DSL
- **CI/CD:** GitHub Actions (builds APK automatically)

## Repository
- **URL:** https://github.com/xeshP/hotspotapp
- **Actions:** https://github.com/xeshP/hotspotapp/actions

## Key Files
```
hotspotapp/
├── app/src/main/java/com/hotspotapp/
│   ├── MainActivity.kt           # Main UI
│   ├── WifiScanner.kt            # WiFi scanning
│   ├── PasswordTester.kt         # Connection testing (4s timeout)
│   ├── PasswordFileReader.kt     # Load passwords from .txt
│   └── adapter/WifiListAdapter.kt
├── app/src/main/res/layout/
│   ├── activity_main.xml
│   └── item_wifi.xml
├── pws.txt                       # Password list (1200+ passwords)
└── .github/workflows/build.yml   # GitHub Actions build
```

## Current Settings
- **Connection timeout:** 4 seconds per password
- **Auto-scan on startup:** NO (only scans when button clicked)
- **API used:** WifiNetworkSpecifier (shows popup for each attempt)

## Android 10+ Limitations
- WifiNetworkSpecifier requires user to tap "Connect" for each password attempt
- This is a Google security feature, cannot be bypassed without root
- WifiNetworkSuggestion API doesn't give immediate feedback (doesn't work for testing)

## Password List (pws.txt)
Contains 1200+ common passwords including:
- Number patterns: 12345678, 123123123, etc.
- German words: passwort, kennwort, geheim, sicherheit, wlan
- Keyboard patterns: qwerty, asdf, 1q2w3e4r
- Common words + numbers: password123, admin123, etc.
- Special characters: p@ssw0rd, pa$$word

## How to Build
1. Push changes to GitHub
2. GitHub Actions automatically builds APK
3. Download from Actions tab → Artifacts → HotspotApp-debug

## How to Use
1. Install APK on Android 10+ device
2. Grant location permission
3. Tap "Scan Networks" to find WiFi
4. Tap "Load Password File" to select pws.txt
5. Select a network from list
6. Tap "Start Testing"
7. For each password, tap "Verbinden" (Connect) on popup
8. If correct password found → app shows success dialog

## Known Issues / Notes
- Popup appears for EACH password (Android security requirement)
- ~4 seconds per password attempt
- For faster testing: need rooted device or Android 9 or lower

## Future Improvements (if needed)
- Root mode for faster testing (no popups)
- Adjustable timeout setting in UI
- Save/export found passwords
- Multiple password file support
