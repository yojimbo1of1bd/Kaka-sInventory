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
New in this build:

### 1. A real preference store (`data/settings/UserPreferences.kt`)
- Backed by `SharedPreferences` — a single XML file in the app sandbox, no account and no sync.
- Exposes the setting as a `StateFlow<Boolean>` so the UI reacts immediately; writes persist with
  `apply()` so the disk write never blocks a frame.
- It is deliberately the *only* state outside the sovereign `.db` + folders, because a theme flag
  does not belong in the inventory database.

### 2. Switchable light / dark theme (`ui/theme/Theme.kt`)
- Dark remains the product default, but a full **light** scheme is now defined too.
- `KakaTheme(darkTheme = ...)` takes the mode as an explicit parameter driven by the persisted
  preference, so an appearance change repaints the whole tree and survives process death.
- `KakaApp` hoists the preference at the top of the composition, so every screen — splash included —
  follows the chosen mode.

### 3. Settings screen (`ui/settings/SettingsScreen.kt`)
- Reached from a gear icon on the dashboard.
- A "Dark theme" toggle plus a short note that appearance is stored locally on the device only.

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

## Why this is genuinely offline
- `AndroidManifest.xml` declares **no** `INTERNET` permission (camera only).
- Cloud backup is excluded in `data_extraction_rules.xml`; only device-to-device transfer carries
  `kaka_inventory.db` and `kaka_webp_store/`.
- Coil only ever renders local `File` paths. Exports and preferences are app-private files.
- App state = one `.db` file + one image folder + one export folder + one prefs XML, all sandboxed.

## Pinned versions
minSdk 26 · compileSdk/targetSdk 35 · Kotlin 2.0.21 · AGP 8.7.3 · KSP 2.0.21-1.0.28 ·
Compose BOM 2024.12.01 · Room 2.6.1 · CameraX 1.4.1 · Coil 2.7.0 · androidx.exifinterface 1.3.7 ·
JUnit 4.13.2.
