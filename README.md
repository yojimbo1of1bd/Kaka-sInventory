# Project Kaka — MVP

A strictly offline Android inventory & decluttering tool.
No cloud. No Firebase, no Supabase, no network permission at all.

Stack: Kotlin · Jetpack Compose (Material 3) · Room (SQLite) · CameraX · Coroutines · MVVM + Repository.

---

## Phase 1 — Foundation & Data Layer
Room schema (`ItemEntity`, `CareTaskEntity`), DAOs, WAL `AppDatabase`, `InventoryRepository`,
and the Clash-of-Clans style splash screen.

## Phase 2 — Ingestion & Storage
Custom CameraX burst viewfinder in Compose, the WebP pipeline (EXIF rotate -> 1600px cap ->
`WEBP_LOSSY` @75% -> `filesDir/kaka_webp_store/`), and the draft-activation flow.

## Phase 3 — The Engine & UI
Magic Input Bar (`i/ c/ l/ v/>`), `@RawQuery` search, swipe-to-liquidate, live dashboard stats.

## Phase 4 — Maintenance & Liquidation
Triage clock, overdue math, joined due-task query, the Red Ring on the profile icon, the
due-task bottom sheet, and the Maintenance dialog (long-press an item).

## Phase 5 — Detail, Export & Extended Grammar
Extended grammar (`m/due`, `m/none`, `s/sold|active|donated|trashed`), the Item Detail screen
with full care-task management, and CSV/JSON export to local app storage.

## Phase 6 — Appearance & Settings
- Switchable light / dark theme (`ui/theme/Theme.kt`).
- Settings screen (`ui/settings/SettingsScreen.kt`) with dashboard visibility toggles.
- Real preference store (`data/settings/UserPreferences.kt`) backed by SharedPreferences.

## Phase 7 — Granular Permission Kill-Switch
- Full opt-in/opt-out toggles for all capabilities (Camera, Biometrics, Contacts).
- Graceful degradation when permissions are denied or toggled off in settings.

## Phase 8 — Security & Lock Lifecycle
- PBKDF2 hashed PIN storage (no plaintext PINs).
- Rate-limiting, throttling, and lockout mechanisms for PIN retries.
- App lock state that survives process backgrounding and navigation.

## Phase 9 & 10 — Lifecycle Correctness & Consistency
- Lifecycle correctness in MainActivity.
- Full round-trip testing for Schema migrations (V1 -> V2 -> V3 -> V4 -> V5).
- Strict minor-unit money arithmetic and unified image storage paths.
- Backup configuration limits to `.db` and `kaka_webp_store`.



---

## Requirements
- Android Studio Ladybug (2024.2) or newer · JDK 17 · Android SDK 35
- A device/emulator on Android 8.0 (API 26)+ with a camera (for Phase 2)

## How to run
1. Unzip somewhere with no spaces in the path.
2. Android Studio -> **File > Open** -> select the `ProjectKaka` folder (containing `settings.gradle.kts`).
   Do NOT open `app/`.
3. If Studio reports a missing Gradle wrapper JAR, let it use a local Gradle distribution, or run
   once: `gradle wrapper --gradle-version 8.9`.
4. Let **Gradle Sync** finish.
5. Press **Run ▶**.
6. Optional — pure-JVM tests: `gradlew test`

## Things to try
- **Appearance**: gear icon -> toggle "Dark theme" and watch the app repaint instantly.
- **Search**: `c/electronics v/>500`, `m/due` (overdue maintenance), `s/sold` (liquidated items).
- **Detail**: tap a row to open it; add, complete, or delete care tasks there.
- **Export**: download icon -> Export CSV / JSON.

## Why this is genuinely offline and secure
- `AndroidManifest.xml` declares **no** `INTERNET` permission. (Permissions exist only for Camera, Biometrics, and Contacts, and are individually gated).
- Cloud backup is excluded in `data_extraction_rules.xml`; only device-to-device transfer carries `kaka_inventory.db` and `kaka_webp_store/`.
- Coil only ever renders local `File` paths. Exports and preferences are app-private files.
- App state = one `.db` file + one image folder + one export folder + one prefs XML, all sandboxed.
- PINs are PBKDF2 hashed, and full app-locking is enforced upon backgrounding.

## Pinned versions
minSdk 26 · compileSdk/targetSdk 35 · Kotlin 2.0.21 · AGP 8.7.3 · KSP 2.0.21-1.0.28 ·
Compose BOM 2024.12.01 · Room 2.6.1 · CameraX 1.4.1 · Coil 2.7.0 · androidx.exifinterface 1.3.7 ·
JUnit 4.13.2.
