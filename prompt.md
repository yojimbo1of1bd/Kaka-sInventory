<USER_REQUEST>
Follow the plan and it is given below..

════════════════════════════════════════════════════════════════════════
PICCO PROMPT — Project Kaka: Financial Engine Rework + Security Hardening
════════════════════════════════════════════════════════════════════════

───────────── P — PERSONA ─────────────
You are a senior Android engineer and data-model architect with deep,
shipping experience in offline-first apps: Kotlin 2.0.21, Jetpack Compose
(Material 3, BOM 2024.12.01), Room 2.6.1 with KSP, Coroutines/Flow, and
manual DI containers. You are conservative with user data to the point of
paranoia: this app's entire selling proposition is that it is strictly
offline and that the user's data is sovereign. You never introduce an
INTERNET permission, never add a cloud call, never perform a destructive
migration, and never silently drop a user's rows. You write migrations you
can defend line by line. You prefer integer minor-units over floating point
for currency and you know why. You do not over-engineer: no new libraries
unless you can justify them against an existing alternative in the current
dependency set.

───────────── I — INSTRUCTIONS ─────────────
Work through the phases IN ORDER. Each phase has a hard gate: you do not
start phase N+1 until phase N compiles AND its gate criterion is met. After
each phase, STOP and emit the checkpoint report described in O — OUTPUT.
Do not attempt the whole thing in one pass.

═══ PHASE 0 — RESTORE THE BASELINE BUILD (blocker, do this first) ═══
The repository as committed CANNOT build. Fix this before anything else.
 0.1 The .gitignore excludes `gradle/`, so `gradle/libs.versions.toml`
     (the version catalog that BOTH build.gradle.kts files reference via
     `alias(libs.plugins.*)` / `implementation(libs.*)`) was never
     committed, and neither was `gradle/wrapper/`. Reconstruct and commit:
     `gradle/libs.versions.toml` with every alias actually referenced across
     root `build.gradle.kts` and `app/build.gradle.kts`, pinned to the
     versions the README states: minSdk 26, compileSdk/targetSdk 35,
     Kotlin 2.0.21, AGP 8.7.3, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01,
     Room 2.6.1, CameraX 1.4.1, Coil 2.7.0, androidx.exifinterface 1.3.7,
     JUnit 4.13.2, plus `androidx.biometric`, `androidx.navigation.compose`,
     `kotlinx-coroutines-android`, and the Vico trio
     (`vico.compose`, `vico.compose.m3`, `vico.core`) that
     `app/build.gradle.kts` also imports.
 0.2 Commit a working `gradle/wrapper/gradle-wrapper.properties` (+ jar)
     targeting Gradle 8.9, so `./gradlew` actually boots. Keep gradlew and
     gradlew.bat.
 0.3 Untrack and delete `app/local.properties` (it is committed but must
     not be — it leaks a foreign `sdk.dir`). Ensure local.properties is
     gitignored at every path.
 0.4 Remove the bogus `org.gradle.tooling.parallel` property and the
     "Gradle 9.4+" comment from gradle.properties (that property is not a
     documented Gradle flag and the version does not exist). Keep only
     real, verified Gradle properties.
 0.5 Fix `app/build.gradle.kts`: remove `isMinifyEnabled`/`isShrinkResources`
     from release OR add a complete `proguard-rules.pro` with keep rules for
     Room entities/DAOs, Compose, Kotlin metadata, kotlinx.serialization (if
     present), and Vico; and add an explicit `signingConfig` placeholder
     using a gitignored keystore path so `assembleRelease` is honest about
     what it needs. Bump `versionCode`/`versionName` to match the shipped
     feature set (it claims 0.2.0-phase2 while the README documents Phase 6
     and source comments claim Phase 8/9).
 0.6 Correct `gradle.properties` claims and align README's "no network,
     camera only" statement with reality: the manifest actually requests
     USE_BIOMETRIC, POST_NOTIFICATIONS, SEND_SMS, READ_CONTACTS, RECORD_AUDIO,
     READ_MEDIA_IMAGES, and legacy WRITE_EXTERNAL_STORAGE (maxSdk 28). Either
     justify each in the README or remove the ones the code does not use.
 GATE 0: `./gradlew :app:assembleDebug` succeeds from a clean clone, and
 `./gradlew test` runs the two existing JVM tests green.

═══ PHASE 1 — MONEY TYPE + ATOMIC TRANSFER MODEL (schema v2 → v3) ═══
Root causes: Problems 1, 3, 4 (and it unblocks 2, 5, 8).
 1.1 Introduce money as Long minor units. Add a `Converters` function set
     (already exists as data/local/Converters.kt) that maps the DB's
     INTEGER minor-unit column to a Kotlin value type `Money(minorUnits: Long)`
     with `+`, `-`, `compareTo`, and a `format(locale)` helper. All display
     formatting reads Money; no Double ever touches arithmetic.
 1.2 Rewrite the four money columns to INTEGER minor units, multiplying
     existing values by 100 in the migration (amount, opening_balance,
     ledger amount, and any others). Write `MIGRATION_2_3` in
     data/local/AppDatabase.kt that ALTERs/rebuilds the affected tables and
     copies data ×100. Do NOT use fallbackToDestructiveMigration for this —
     and while you are here, REMOVE `.fallbackToDestructiveMigration()`
     entirely so future un-migrated bumps fail loudly instead of wiping data.
 1.3 Model a transfer as TWO linked rows, inserted in ONE
     `db.withTransaction { } }`. Extend `TransactionEntity` with:
       - `categoryId: Int?` (NULLABLE — Problem 3)
       - `transferId: String?` (groups the two legs; UUID)
       - `counterAccountId: Int?` (the other leg's account)
     Add a `TransactionType` enum (EXPENSE, INCOME, TRANSFER, DEBT_ISSUE,
     DEBT_SETTLE, ADJUSTMENT) persisted as TEXT, replacing the boolean
     `is_credit` OR keeping `is_credit` as a derived compatibility column —
     choose one and document why.
 1.4 Add `FinanceRepository.transfer(fromAccountId, toAccountId, amount, note)`
     that creates the DEBIT leg and CREDIT leg atomically with the same
     transferId. Add `FinanceRepository.deleteTransfer(transferId)` that
     deletes both legs atomically. No transfer may ever exist with one leg.
 1.5 Update `FinanceDao` balance queries to sum minor units and to exclude
     nothing by accident: confirm transfers net to zero across the two
     accounts, and that category-based analytics EXCLUDE transfers and
     debt rows (because they have null category / a non-expense type).
 GATE 1: a JVM test (or instrumented Room test if unavoidable) proves:
 (a) transfer leaves the sum of both account balances unchanged,
 (b) deleting the transfer removes both legs,
 (c) category spending totals ignore transfers.
 Also: an on-device migration test from a v2 DB with seeded rows proves no
 balance changes value (v2 ¥100.00 → v3 10000 minor units, same display).

═══ PHASE 2 — LEDGER LINKED TO REAL BALANCES (Problems 2, 6, 7) ═══
 2.1 Add to `LedgerEntryEntity`: `accountId: Int?` (the cash account the
     debt touched) and `linkedTransactionId: Long?` (the transaction that
     moved the money). A Receivable (you lent out) issues a DEBIT-side
     transaction from that account; a Payable (you borrowed) issues a
     CREDIT-side transaction to it. Settling posts the compensating
     transaction and flips `isSettled` — both inside one withTransaction.
 2.2 Add `FinanceRepository.issueDebt(...)` and `.settleDebt(...)` that
     keep ledger and account balance consistent by construction. It must be
     impossible to settle without the matching transaction and vice versa.
 2.3 Per-contact aggregation: expose a DAO query returning, per
     contact_name (+ phone), the net outstanding = SUM(open receivables) −
     SUM(open payables), plus entry count and earliest due date. Add
     `FinanceRepository.observeContactSummaries()`.
 2.4 Ledger search: add a `@RawQuery`-style or parameterised search over
     ledger_entries supporting contact-name contains, amount range, settled
     state, and due-date range, mirroring the binding discipline already used
     in `MagicInputParser` (bind every value; escape LIKE wildcards).
 2.5 Ledger edit/delete: real update + delete operations surfaced in the
     ledger UI, with the linked account balance adjusting accordingly.
 GATE 2: aggregate query returns correct totals across multiple entries to
 the same contact; a settle operation is reflected in BOTH the ledger row
 and the account balance in the same frame; ledger search returns bounded,
 correct results.

═══ PHASE 3 — BALANCE PERFORMANCE (Problem 5) ═══
 3.1 Stop recomputing balances with inline correlated SUM subqueries on
     every read. Choose ONE and justify it in the checkpoint:
     (a) a Room `@DatabaseView` / materialised `account_balances` table
         maintained by triggers or by the repository, or
     (b) a denormalised `balance_minor` column on accounts updated inside
         every transaction's withTransaction block, reconciled by a
         verify-and-repair routine on app start.
 3.2 Whatever you choose, the invariant is: the stored balance MUST equal
     `opening_balance + SUM(credits) − SUM(debits)` after every write. Add a
     DAO query that asserts this and a startup self-check that logs (and
     optionally repairs) any divergence rather than silently trusting it.
 3.3 Keep the existing reactive Flows firing on the right tables.
 GATE 3: with N synthetic transactions (e.g. 5,000) the dashboard balance
 Flow emits without an O(N) scan; assert query plan / timing in the report.

═══ PHASE 4 — ANALYTICS GRAPH CORRECTNESS (Problem 8) ═══
Rewrite `ui/dashboard/GraphViewModel.kt` and `GraphScreen.kt`.
 4.1 Time axis spans [earliest transaction date .. today] only. NO future
     padding — the flat line into the future must not exist.
 4.2 Historical data must plot: bucket by day/week/month from the earliest
     row, not from today backwards.
 4.3 Aggregate the balances of ALL selected accounts correctly (sum per
     account per bucket, then across accounts) — eliminate the random/
     incorrect values, which come from mixing per-account and total series.
 4.4 Plot liabilities: a separate net-worth series = assets − (open
     payables). Receivables shown as a distinct series or clearly labelled.
 4.5 Money in charts is Money minor-units converted at the axis formatter,
     never Double math mid-pipeline.
 GATE 4: unit tests over a fixed fixture (several accounts, transfers,
 debts, dated transactions) assert exact series values and that the last
 bucket is today.

═══ PHASE 5 — TERMINAL & FLEXIBLE f/ GRAMMAR (Problem 9) ═══
 5.1 Make the terminal USEFUL: it must execute commands in place and render
     output, not redirect the user to the home magic bar. Reuse
     `MagicInputParser` + repositories so terminal and home share one engine.
 5.2 Relax `f/` into a flexible grammar. Required: amount, and either an
     account or a category/contact. Everything else optional and
     order-independent. Support:
       f/ 5050 bkash lunch        (current rigid form)
       f/ 5050 breakfast bkash    (reordered)
       f/ income 5050 bkash salary
       f/ 5050 transfer wallet bkash
       f/ 5050 bkash              (no category → uncategorized is OK now)
     Use keyword sets keyed by the user's own configured aliases (the alias
     system already exists). On ambiguity, return a clarifying prompt string
     instead of guessing. No positional-only parsing.
 5.3 Keep every value bound as `?`; no string interpolation into SQL. Mirror
     the existing blank-input fallback behaviour.
 GATE 5: `MagicInputParserTest.kt` gains cases for reordered, partial, and
 ambiguous f/ input; each returns Success | Financial | clarification | Error
 deterministically.

═══ PHASE 6 — DASHBOARD ACCOUNT VISIBILITY (Problem 10) ═══
 6.1 Do not auto-create an "Account" per ledger contact. Keep contacts as
     ledger entities (Phase 2 already does this); they must not inflate the
     accounts list.
 6.2 Add a persisted `hiddenAccountIds: Set<Int>` (or an explicit
     `visibleAccountIds` allow-list) in `data/settings/UserPreferences.kt`,
     surfaced as StateFlow, with a Settings screen to toggle each account's
     visibility on the home dashboard.
 6.3 Dashboard renders only visible accounts; totals/a budget denominator
     must state whether they include hidden accounts (choose: hidden accounts
     are excluded from the headline but still counted in net worth, or fully
     excluded — document the choice).
 GATE 6: toggling an account off the dashboard persists across process
 death and updates totals per the documented rule.

═══ PHASE 7 — GRANULAR PERMISSION KILL-SWITCH (Problem 11) ═══
 7.1 Add a `PermissionGate` (or equivalent) that centralises runtime
     permission state for: Camera, Contacts, SMS/Calls, Microphone, Storage/
     Media images. Each capability has an app-level ON/OFF switch persisted
     in UserPreferences on top of the OS grant.
 7.2 When the app-level switch is OFF, the feature degrades gracefully with
     an explanatory message and a link to the toggle — it must not crash and
     must not silently no-op.
 7.3 Provide "limited / on-demand" access: e.g. request camera only at the
     moment of capture, contacts only when the user taps "pick contact",
     each with rationale. Do NOT request any permission at startup that is
     not required for the current screen.
 7.4 When a switch is turned OFF, revoke/ignore that capability immediately
     for the running session.
 GATE 7: with all switches OFF the app launches, browses, and edits records
 with zero permission prompts and zero crashes; each disabled feature shows
 its explanatory affordance.

═══ PHASE 8 — SECURITY: PIN + LOCK LIFECYCLE (Problems 12, 13) ═══
 8.1 NEVER store the PIN in plaintext. Replace `KEY_APP_PIN` with a PBKDF2
     (PBKDF2WithHmacSHA256, per-install random salt, high iteration count)
     or an equivalent KDF-derived verifier. Store only salt + verifier +
     iteration count. Comparison is constant-time on the derived value.
 8.2 Add attempt throttling and lockout: track consecutive failures
     (persisted), enforce an increasing back-off, and a lockout after N
     failures. Reset on success. Persist so a restart does not reset the
     counter.
 8.3 Move the lock OUT of MainActivity.onCreate into a proper lock state
     that survives navigation: lock on cold start AND on onStop (with a
     short grace window so a permission dialog or rotation does not lock
     mid-use). Re-authenticate on onResume after the window elapses.
 8.4 Biometric: allow the sensor to retry on `onAuthenticationFailed` — do
     NOT eject to the PIN screen on a single failure. Only fall back to PIN
     on error/unavailable, not on a failed scan. Keep DEVICE_CREDENTIAL as a
     fallback where appropriate.
 8.5 Migration note: if an existing install has a plaintext PIN, on first
     unlock after upgrade, hash it and erase the plaintext value.
 GATE 8: grep proves no plaintext PIN column/constant remains; a test proves
 N failed attempts trigger lockout and that a restart does not clear it;
 manual test proves backgrounding past the grace window forces re-auth.

═══ PHASE 9 — LIFECYCLE CORRECTNESS (Problem 14) ═══
 9.1 In MainActivity.kt, call `super.onCreate(savedInstanceState)` FIRST,
     then `enableEdgeToEdge()`, then read `application as KakaApplication`
     and install content. Remove the current inverted order.
 9.2 While you are in MainActivity with Phases 7–8 landed, make sure the
     lock/biometric flow does not re-enter setContent from a callback in a
     way that leaks a stale composition.
 GATE 9: onCreate order is correct; rotation and cold start behave.

═══ PHASE 10 — CONSISTENCY SWEEP ═══
10.1 Make ImportReader and ExportWriter round-trip the NEW schema: money as
     minor units, transfers (both legs), ledger links, nullable categories.
     Restore must go through the repository so there is ONE write path.
10.2 Fix the image-folder mismatch: LocalImageStore writes
     `filesDir/kaka_webp_store/` but ImportReader unzips into
     `filesDir/images/` and stores those absolute paths. Unify on ONE
     folder and ONE constant, and make backup_rules.xml /
     data_extraction_rules.xml cover exactly that folder plus the .db.
10.3 Bump the DB version to 3 and add a full serialised migration test.
10.4 Update the README to match reality (phases, permissions, version).
 GATE 10: export → wipe → import restores an identical database and images;
 the migration chain v1→v2→v3 is covered by tests.

───────────── C — CONTEXT ─────────────
Repo: github.com/yojimbo1of1bd/Kaka-sInventory (branch `main`), GPL-3.0.
Package root: com.projectkaka.inventory. ~62 Kotlin files.

Key files you will touch:
 - app/build.gradle.kts, gradle/libs.versions.toml (MISSING), gradle.properties
 - app/src/main/java/com/projectkaka/inventory/data/local/AppDatabase.kt
   (version 2; has MIGRATION_1_2 and a DESTRUCTIVE fallback — remove the latter)
 - data/local/Converters.kt, data/local/entity/* (ItemEntity, CareTaskEntity,
   AccountEntity, AccountType, FinancialCategoryEntity, CategoryType,
   TransactionEntity, LedgerEntryEntity, LedgerType, and enums)
 - data/local/dao/FinanceDao.kt (~13 KB: balance SUM subqueries, category
   spending, day/month spending, transaction counts)
 - data/local/dao/ItemDao.kt (@RawQuery searchItemsRaw with
   observedEntities = [ItemEntity, CareTaskEntity])
 - data/repository/{FinanceRepository,FinanceRepositoryImpl,
   InventoryRepository,InventoryRepositoryImpl}.kt
 - data/local/{ImportReader,ExportWriter,LocalImageStore,ImageProcessor}.kt
 - data/settings/UserPreferences.kt (SharedPreferences, StateFlow, plaintext PIN)
 - search/MagicInputParser.kt (~17 KB; tokens i/ c/ l/ v/ m/ s/ f/ credit/
   debit/ init/ alter/ delete/ kaka/ help/; binds args as ?, escapeLike for
   \ % _; hardcoded ORDER BY items.date_added DESC; NO LIMIT)
 - ui/dashboard/{DashboardScreen.kt ~27 KB, DashboardViewModel.kt ~21 KB,
   GraphScreen.kt, GraphViewModel.kt, SwipeableItemRow.kt, ItemGridCard.kt,
   QuickNoteCard.kt}
 - ui/liquidate/{LedgerScreen.kt ~21 KB, LedgerViewModel.kt, LiquidationDialog.kt}
 - ui/settings/{SettingsScreen.kt ~21 KB, AccountsManagerScreen.kt,
   AliasGuideScreen.kt ~22 KB, AliasGuideViewModel.kt, EmergencyContactScreen.kt}
 - ui/detail/{ItemDetailScreen.kt, ItemDetailViewModel.kt}
 - MainActivity.kt (FragmentActivity; plaintext PIN TextField; inverted
   enableEdgeToEdge; single biometric failure → PIN screen)
 - AndroidManifest.xml (no INTERNET permission — must STAY that way)
 - app/src/test/... only 2 JVM tests: search/MagicInputParserTest.kt,
   triage/DueTaskTest.kt. No androidTest dir — create one for Room tests.

Current data model (as committed):
 - TransactionEntity: id, amount(Double), account_id, category_id,
   is_credit(Boolean), note, timestamp. Single-account only ⇒ cannot express
   a transfer; category mandatory ⇒ fake categories pollute analytics.
 - AccountEntity: id, name, type(CASH/ASSET/CAPITAL), aliases,
   opening_balance(Double), is_active, created_at. Balance = computed on read.
 - FinancialCategoryEntity: id, name, type(EXPENSE/INCOME), aliases, created_at.
 - LedgerEntryEntity: id, contact_name, contact_phone, amount(Double),
   type, is_settled, note, due_date, created_at. NO account link.
 - Daily budget denominator = SUM of CASH-type active account balances.

───────────── C — CONSTRAINTS (hard rules, non-negotiable) ─────────────
1. NO `INTERNET` permission. NO network library. NO cloud. Ever. AndroidManifest
   must end with zero INTERNET declaration. If a solution would need the
   network, it is the wrong solution — say so and propose an offline one.
2. NO destructive migration. Remove `.fallbackToDestructiveMigration()`.
   Every version bump ships a real, tested Migration. Existing user rows must
   survive every upgrade with identical displayed values.
3. NO new third-party dependency unless you can (a) name the alternative
   already in the project it replaces, and (b) justify it against the
   minSdk 26 / offline / privacy constraints. Prefer stdlib + existing deps.
4. Currency arithmetic uses integer minor units ONLY. No Double/Float for
   money in storage, repository, or view-model state. Formatting is the only
   place a human-readable string is produced.
5. Every multi-row write (transfer, debt issue/settle, import) is wrapped in
   a single `db.withTransaction { }`. No partial writes.
6. SQL values are always bound (`?`); LIKE patterns are escaped exactly as
   the existing `escapeLike()` does. Column/enum fragments come from fixed
   internal maps, never from user input.
7. Do not request new runtime permissions. Reduce/justify all existing ones;
   startup must request none that the launch screen does not need.
8. Kotlin style: 4-space indent, no wildcard imports, explicit visibility,
   `StateFlow` for UI state, no blocking calls on the main thread.
9. Every phase must compile with `./gradlew :app:assembleDebug` and keep
   `./gradlew test` green. Pure logic (parser, money math, triage, series
   aggregation) needs a JVM unit test. Persistence/migration needs a Room test.
10. Keep GPL-3.0 headers/intent intact. Do not relicense or add a new
    LICENSE.
11. Preserve offline export/import: CSV + JSON + the `.kaka` ZIP must remain
    fully local (currently MediaStore Downloads/ProjectKaka).

───────────── O — OUTPUT ─────────────
After EACH phase, emit a checkpoint in exactly this shape:
 1. PHASE <n> — <name>: STATUS (done | blocked | needs decision).
 2. FILES: each file created/modified with a one-line summary. Provide FULL
    file contents for new files and a unified diff for modified files.
    No placeholders, no "// ... rest unchanged" inside a changed block.
 3. SCHEMA: the exact Room version and the exact Migration object(s) added,
    with a plain-English account of what each statement does to existing rows.
 4. MONEY: confirm no Double remains in the money path you touched (or list
    the exact spots still to migrate and why).
 5. TESTS: the test names added/changed and what each asserts. Actually show
    the test code.
 6. VERIFICATION: a copy-pasteable manual script a human can run to confirm
    the gate (commands, taps, expected observable result).
 7. DECISIONS: every place you had to choose (e.g. stored-balance column vs
    database view; keep vs drop is_credit; hidden-accounts counting rule) with
    the option you picked and the reason.
 8. OPEN / UNRESOLVED: anything you could not complete, any assumption you
    had to make, any file you could not read, any risk you are flagging.

At the very END (after Phase 10), emit one consolidated:
 - MIGRATION PLAN: v1→v2→v3 table of what changed when.
 - RISK REGISTER: ordered by severity, each with the mitigation you shipped.
 - REGRESSION CHECKLIST: the behaviours that must still work (offline claim,
   magic search, capture pipeline, export/import, biometrics, triage).
 - BACKLOG: the items you deliberately left out of scope.

If the repo state contradicts anything in this prompt (e.g. libs.versions.toml
turns out to exist, or an entity already has the columns you were told to
add), STOP and report the contradiction in OPEN / UNRESOLVED before changing
anything.
════════════════════════════════════════════════════════════════════════


</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-09-26T22:42:16+06:00.

The user's current state is as follows:
Other open documents:
- e:\Cyber Lab\ka\ProjectKaka-Phase6\ProjectKaka\app\src\main\java\com\projectkaka\inventory\ui\settings\SettingsScreen.kt (LANGUAGE_KOTLIN)
- e:\Cyber Lab\ka\ProjectKaka-Phase6\ProjectKaka\app\src\main\java\com\projectkaka\inventory\ui\settings\EmergencyContactScreen.kt (LANGUAGE_KOTLIN)
- e:\Cyber Lab\ka\ProjectKaka-Phase6\ProjectKaka\app\src\main\java\com\projectkaka\inventory\ui\dashboard\DashboardViewModel.kt (LANGUAGE_KOTLIN)
- e:\Cyber Lab\ka\ProjectKaka-Phase6\ProjectKaka\app\src\main\java\com\projectkaka\inventory\ui\KakaApp.kt (LANGUAGE_KOTLIN)
- e:\Cyber Lab\ka\ProjectKaka-Phase6\ProjectKaka\app\build.gradle.kts (LANGUAGE_UNSPECIFIED)
</ADDITIONAL_METADATA>