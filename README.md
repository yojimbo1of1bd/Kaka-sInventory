# 📦 Project Kaka — Sovereign Inventory & Personal Finance System

> **Strictly Offline · Double-Entry Accounting · Moving Carton Baskets · Visual Infographics · Zero Cloud**

**Project Kaka** is a privacy-first personal asset tracking, moving management, and double-entry budgeting application built for Android. It operates under a strict **zero-network policy**—the application manifest contains **no `INTERNET` permission**, ensuring your personal belongings, financial statements, and receipts never leave your physical device.

---

## 🌟 Key Architecture & Capabilities

### 1. 💰 Collapsible Daily Budget & Real-Time Financial Engine
- **Single-Bar Collapsible Dashboard**:
  - **Collapsed State**: A sleek, minimal card displaying `৳{dailyBudget}/d • Today: ৳{todaySpend}` on the left and `Total: ৳{totalCashBalance}` on the right with an intuitive chevron.
  - **Expanded State**: Smoothly unfolds via `.animateContentSize()` to reveal full budget metrics (remaining days, visible cash balance, today's spend) and a horizontally scrollable row of active account chips (`LazyRow`) that accommodates any number of accounts.
- **Strict Minor-Unit Money Arithmetic**:
  - Implements a dedicated `Money` value class backed by integer minor units (`Long`).
  - Eliminates floating-point rounding errors and financial discrepancies across all accounts, ledger postings, and inventory valuations.
- **Overspending Spike Detection & Safety Guardrails**:
  - Automated detection when daily expenditures exceed the calculated threshold.
  - Configurable emergency alert dispatch via system intents (WhatsApp, SMS, or Phone Call) if overspending strikes persist.

### 2. 🚚 Moving Cartons & Baskets System
- **Carton & Box Organization**:
  - Create color-coded cartons/boxes (`BasketEntity`) designed for home relocation, storage organization, or transportation.
  - Track item counts, total carton valuation, and packed/unpacked status.
- **Responsive Inventory Packing**:
  - Search inventory live by name, category, or location tag.
  - Supports packing both active inventory and drafts (`is_draft = 0` and `is_draft = 1`).
  - Integrated soft-keyboard handling with `imePadding()` and real-time item counter.
- **Offline Barcode & QR Code Label Generator**:
  - Generates customizable QR codes and 1D Code-128 barcodes offline using ZXing.
  - Exports printable high-resolution box labels directly to the Android MediaStore gallery (`Pictures/ProjectKaka`).
- **CameraX Carton Scanner**:
  - Real-time viewfinder that identifies carton QR codes and barcodes.
  - **Runtime Camera Permission**: Seamless permission request flow with fallback UI and manual code entry.
  - **Hardware Stride Compensation**: Extracts pure Y-plane luminance from CameraX frames, stripping hardware `rowStride` padding to guarantee reliable scanning across all camera sensors.
  - Dual-pass binarization (HybridBinarizer + GlobalHistogramBinarizer) for low-light and high-glare label decoding.

### 3. 🗺️ Visual Belongings Map (Infographic PNG Export)
- **On-Device Infographic Rendering**:
  - Generates high-resolution graphical breakdown posters (1080px wide) of your belongings directly onto an Android `Bitmap`.
  - Displays category headers, item count, total estimated valuation, individual item badges, and visual tags.
- **Flexible Category Selection**:
  - Export complete inventory ("All Belongings") or filter by any category.
  - Interactive selection dialog (`VisualMapDialog.kt`) featuring category chips, custom category text search, and live matching item counts.
  - Direct gallery export via `MediaStore` with zero cloud dependencies.

### 4. ⚡ Magic Input Bar & Power Search Grammar
- Power-user query grammar processed by `MagicInputParser` and compiled into Room `@RawQuery`:
  - `i/laptop` — Filter by item name prefix or substring.
  - `c/electronics` — Filter by category.
  - `l/office` — Filter by location tag.
  - `v/>5000` / `v/<1000` — Numerical value comparisons.
  - `m/due` — Items with overdue maintenance or care tasks.
  - `m/none` — Items without active care tasks.
  - `s/active|sold|donated|trashed` — Filter by lifecycle status.
- Integrated quick toggle between Grid view and List view.

### 5. 🛠️ Maintenance, Care Tasks & Liquidation
- **Recurring Care Tasks**:
  - Attach maintenance schedules (e.g., lens cleaning, battery cycling, lubrication) with custom recurrence intervals.
  - Visual urgency indicators and dedicated triage sheet.
- **Swipe-to-Liquidate**:
  - Liquidate items as Sold, Donated, or Trashed.
  - Automatically records cash recovery into the double-entry accounting ledger.

### 6. 🔒 Privacy, Security & Data Sovereignty
- **No Internet Access**: Zero network permissions declared in `AndroidManifest.xml`.
- **Granular Permission Kill-Switches**: Individual toggle switches in Settings for Camera, Biometrics, Contacts, and Storage.
- **PBKDF2 Hashed PIN**: Hardware-backed authentication and rate-limited PIN verification with biometric support.
- **Complete Offline Backup & Restore**:
  - Export structured CSV and JSON exports.
  - Single-archive `.kaka` ZIP backup bundling the entire SQLite database and full-resolution WebP images.

---

## 🏗️ Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI Toolkit** | Jetpack Compose (Material 3, Compose BOM 2024.12.01) |
| **Architecture** | MVVM + Repository Pattern + Clean Architecture |
| **Local Database** | Room SQLite 2.6.1 with Write-Ahead Logging (WAL) |
| **Camera & Vision** | CameraX 1.4.1 (Camera2, Lifecycle, View) |
| **Barcode / QR** | ZXing Core 3.5.3 (Offline generation & analysis) |
| **Image Loading** | Coil 2.7.0 (Local File pipeline) |
| **Image Compression** | Native Android WebP Lossy (75% quality, 1600px cap) |
| **Async & State** | Kotlin Coroutines & `StateFlow` / `SharedFlow` |
| **Testing** | JUnit 4, AndroidX Test, Room MigrationTestHelper |

---

## 📁 Repository Structure

```
Kaka-sInventory/
├── app/
│   ├── build.gradle.kts                # App-level build configuration & dependencies
│   ├── schemas/                        # Exported Room database migration schemas (v1..v11)
│   └── src/
│       ├── androidTest/                # Instrumented tests & Room migration verification
│       ├── test/                       # Unit tests (Money, BarcodeResolution, VisualMap, etc.)
│       └── main/
│           ├── AndroidManifest.xml     # Offline manifest (No INTERNET permission)
│           ├── java/com/projectkaka/inventory/
│           │   ├── KakaApplication.kt   # Application entry point & dependency graph
│           │   ├── MainActivity.kt      # Main lifecycle host & biometric gate
│           │   ├── data/
│           │   │   ├── local/          # Room AppDatabase, DAOs, Type Converters, Migrations
│           │   │   ├── repository/     # InventoryRepository & FinanceRepository implementations
│           │   │   └── settings/       # SharedPreferences & UserPreferences store
│           │   ├── model/              # Domain models (Money, ItemStatus, AccountType)
│           │   ├── search/             # MagicInputParser, TerminalExecutor, SearchHelper
│           │   ├── ui/
│           │   │   ├── basket/         # Carton management, Pack dialog, Box scanner & barcode preview
│           │   │   ├── dashboard/      # Home dashboard, Collapsible budget bar, Visual map trigger
│           │   │   ├── detail/         # Item detail & care task management
│           │   │   ├── drafts/         # Rapid-capture intake & draft activation
│           │   │   ├── export/         # CSV/JSON/ZIP backup and VisualMapDialog
│           │   │   ├── liquidate/      # Ledger, statements, and liquidation dialogs
│           │   │   ├── search/         # MagicInputBar with grammar hints
│           │   │   └── settings/       # Account manager, privacy kill-switches, theme settings
│           │   └── util/               # BarcodeAnalyzer, BarcodeGenerator, GalleryHelper, VisualMapGenerator
│           └── res/                    # Drawables, mipmaps, strings, and XML extraction rules
├── gradle/                             # Gradle wrapper & version catalog (libs.versions.toml)
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Project settings & repositories
└── .gitignore                          # Comprehensive Android / Gradle ignore rules
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2) or newer.
- **JDK**: Java Development Kit 17.
- **Android SDK**: API Level 35 (compileSdk & targetSdk), Min SDK 26 (Android 8.0 Oreo+).
- **Physical Device or Emulator**: Armed with a camera for rapid scanning and carton barcode detection.

### Building & Running
1. Clone or open the repository in Android Studio:
   ```bash
   git clone <repository-url>
   ```
2. Open Android Studio and choose **File > Open**, selecting the root directory containing `settings.gradle.kts`.
3. Allow Gradle to synchronize dependencies.
4. Connect an Android device with USB debugging enabled (or start an AVD emulator).
5. Build and install the debug APK:
   ```powershell
   .\gradlew installDebug
   ```
6. Alternatively, run the app directly using the green **Run ▶** button in Android Studio.

---

## 🧪 Testing Suite

### Unit Tests
Execute the local JVM test suite (testing money math, search grammar, barcode decoding, and visual mapping):
```powershell
.\gradlew testDebugUnitTest
```

### Instrumented Migration Tests
Execute the on-device migration validation suite to verify non-destructive database evolution:
```powershell
.\gradlew connectedAndroidTest
```

---

## 📜 Database Migration Policy

The local database schema evolves strictly through non-destructive migrations:
1. Every change to database entities or columns requires an increment in `AppDatabase.kt` version.
2. The schema JSON is exported to `app/schemas/com.projectkaka.inventory.data.local.AppDatabase/` via Room KSP.
3. A migration object (e.g., `MIGRATION_10_11`) must be declared and tested using `MigrationTestHelper` in `app/src/androidTest/`.
4. Financial columns must preserve integer minor units (`Long`) without loss of precision.

---

## 📄 License & Sovereignty

Project Kaka is released as sovereign, open-source software. All data collected by the application remains strictly in the user's custody on the physical device.
