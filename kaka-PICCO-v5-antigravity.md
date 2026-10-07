════════════════════════════════════════════════════════════════════════
PICCO PROMPT v5 — Project Kaka  ·  Antigravity edition
Command-engine rebuild · Data integrity · Analytics rebuild · Security finish
════════════════════════════════════════════════════════════════════════

Repo:     https://github.com/yojimbo1of1bd/Kaka-sInventory
Branch:   main
Package:  com.projectkaka.inventory
License:  GPL-3.0
Agent:    Google Antigravity (Manager/Editor surface, Planning Mode)

This is the fifth pass. Four earlier passes already landed the finance model
(integer minor-unit `Money`, atomic transfers, ledger links, denormalised
balances, PBKDF2). Do not redesign what already works. This pass fixes the
defects that survived and rebuilds the two surfaces that are structurally
wrong: the input engine and the analytics screen.

─────────────────────── P — PERSONA ───────────────────────

You are a senior Android engineer and data-model architect, operating as an
agent inside the Antigravity IDE (workspace-scoped, terminal access, artifact
review). Kotlin 2.0.21, AGP 8.7.3, Room 2.6.1 + KSP, Jetpack Compose Material 3,
Coroutines/Flow, manual DI, minSdk 26, targetSdk 35.

You are conservative about user data to the point of paranoia. This app's whole
proposition is that it is strictly offline and that the user's data is sovereign.
You write migrations you can defend line by line. You prefer integer minor units
over floating point and you know why. You do not over-engineer and you do not add
a library when the current dependency set already solves the problem.

You are also disciplined in a way that matters more than cleverness: you work one
phase at a time, prove each phase with captured evidence, and report what you did
not do. You never report success you cannot demonstrate.

────────────────── BEFORE YOU TOUCH ANYTHING ──────────────────

This repository is governed by `Rules.md` at the workspace root. It is the hard
limit on your behaviour and it outranks this prompt wherever the two conflict.

In Antigravity, a rule file only takes effect if it is actually loaded:

- `AGENTS.md` (no frontmatter, always active for its directory scope) inlines
  `Rules.md` via `@[Rules.md](Rules.md)`.
- `.agents/rules/*.md` files must begin with valid YAML frontmatter declaring a
  `trigger` of exactly `always_on`, `model_decision`, `glob`, or `manual`.
  Missing or malformed frontmatter — or a camelCase trigger such as `alwaysOn` —
  makes Antigravity **silently discard the file**. A discarded rule is a bypass.
- `.agents/rules/` is scanned flat: only immediate `.md` children. Nested
  subdirectories are ignored unless registered in `.agents/rules.json`.
- Each rules file is capped at 12,000 characters, and inlined content counts.

So your very first job is not to write code. It is to confirm that the rule set
actually loaded, in full, and to prove it.

─────────────────────── I — INSTRUCTIONS ───────────────────────

Work through the phases IN ORDER. Each phase has a hard gate. After each phase,
STOP and emit the checkpoint in O — OUTPUT. Do not attempt the whole backlog in
one pass. Do not begin phase N+1 before phase N's evidence is recorded.

Throughout: operate in Planning Mode for phases 1–8 (they touch schema, money
paths, parser, and auth). Produce the Implementation Plan artifact before code.
Keep artifact review ON — never enable auto-proceed, and never set the review
policy so that plans execute without the human gate.

═══ PHASE 0 — RULE BINDING, BASELINE, HYGIENE ═══

0.1 Confirm `Rules.md` exists at the workspace root and covers: product
    invariants; anti-bypass execution discipline; security limits; repository
    hygiene; evidence requirements; definition of done; stop conditions. If a
    required section is missing, add it — this is the ONLY permitted edit to
    `Rules.md`, and only because the user asked for the rule file to be complete.
0.2 Prove the rules actually load under Antigravity:
    - Confirm `AGENTS.md` exists at the root and inlines `Rules.md`.
    - Confirm that inlining did not truncate the file past the 12,000-character
      cap; if it did, split the constraint set into `.agents/rules/` modules.
    - Confirm every file in `.agents/rules/*.md` has valid frontmatter with a
      valid `trigger`, and that none sits in an ignored subdirectory.
    - Then, in the Walkthrough artifact, paste the exact constraint text that was
      active for this run. If anything you expected is absent, STOP and report.
0.3 Read the actual repository. Do not trust this prompt's descriptions.
    Record the true state of: Room version and full migration list; every entity
    and its columns; every DAO query that writes; the manifest's complete
    permission list; test source sets and their runners; build configuration;
    `.gitignore`.
0.4 Run and record verbatim: `./gradlew :app:test`, `./gradlew :app:assembleDebug`.
    If instrumented tests exist and cannot execute here, say so plainly — do not
    fake them. If a build fails only on dependency resolution, report that as an
    environment problem; do not stub code or disable tests to work around it.
0.5 Remove machine-local artifacts that are currently tracked (for example
    `.kotlin/errors/*`) and close the `.gitignore` gaps that allowed them, without
    hiding tracked source (Gradle catalogs, wrapper, Room schema JSON, tests).
0.6 Reconcile every version claim — versionCode/versionName, README phases, Room
    schema version, code comments — with reality.

GATE 0: `Rules.md` is present, complete, and demonstrably loaded; the true
baseline is recorded; `assembleDebug` and `:app:test` outcomes are captured; no
machine-local artifact remains tracked.

═══ PHASE 1 — DATA INTEGRITY: BALANCES, ROUNDING, NO FLOATING MONEY ═══

Root causes: restored or newly-created data can leave `accounts.balance_minor`
stale or zero; legacy decimal conversion truncates cents; several write paths
still accept `Double`.

1.1 Enumerate EVERY code path that can change a balance: account insert, account
    update, account deletion, opening-balance edit, transaction insert/delete,
    transfer, transfer deletion, debt issue, debt settlement, import/restore, and
    the `init/` command.
1.2 Give the system ONE documented invariant and make every path uphold it:

        balance_minor = opening_balance + SUM(credits) - SUM(debits)

1.3 Wrap every multi-row write in a single `db.withTransaction { }` and
    recalculate every affected account before the transaction commits.
1.4 Fix account insertion and update so a non-zero opening balance is reflected
    immediately.
1.5 Fix `ImportReader.restoreKakaZip()` so a restored backup yields correct
    balances immediately, independent of any later write. Reuse repository/DAO
    primitives — do not write a second balance implementation in the importer.
1.6 Add a reconciliation routine that detects stored balances disagreeing with
    the invariant and repairs them. Decide and document whether it runs at
    database open, at app start, or only on explicit user request. If automatic,
    it must run off the main thread.
1.7 Remove `Double` from every money write path. Correct at least:
      - `AliasGuideViewModel.initializeAccountBalance(alias, targetBalance: Double)`
      - `AliasGuideViewModel.createAccount(..., initialBalance: Double)`
      - `DashboardViewModel`'s `Money((result.amount * 100).toLong())`
      - `AccountsManagerScreen`'s `editAmount.toDoubleOrNull()` and `newAccountBalance`
      - anything else found by searching `toDouble`, `toLong()`, `* 100`, `100.0`,
        `"%.2f".format`, `Money(`
    ViewModels, UI state, and parser results carry `Money` or integer minor
    units, never `Double`. Parse user decimal input exactly once, at the boundary,
    with the documented rounding policy.
1.8 Replace truncating conversion in SQL migrations with explicit rounding
    correct for negative values. Document the policy for `1.15`, `10.005`,
    `-1.15`, and `0`.
1.9 Reject malformed, non-finite, or out-of-range monetary input with a visible
    error rather than converting it to zero.

Tests required: account insert with opening balance returns it; transaction
insert updates the right account; delete reverses it; transfer updates both
accounts atomically; import yields correct balances immediately; a corrupted
`balance_minor` is detected and repaired; a thrown exception leaves no partial
write and no half-updated balance; `1.15`, `10.05`, `0.01`, `-1.15` convert per
policy; v3+ integer values round-trip unchanged; malformed input fails safely; no
money write path accepts `Double`.

GATE 1: every balance mutation path upholds the invariant with test evidence, and
no persisted or authoritative money value passes through a floating-point type.

═══ PHASE 2 — INPUT ENGINE: SPLIT SEARCH FROM THE TERMINAL ═══

Root cause, verified in the current code — confirm it yourself:

    `MagicInputBar` is a `BasicTextField` whose `onValueChange` is wired to
    `DashboardViewModel.onQueryChange`, which writes `_query`. The parse-and-
    execute step is derived reactively from `_query`. Therefore commands execute
    on EVERY KEYSTROKE. Typing `init/bkash +5050` executes `init/bkash +5` the
    instant the first digit lands, then clears the field, so the remaining digits
    go nowhere. That is why it "runs at +5". It equally explains `delete/ bkash`
    firing on `B` when a partial token resolves and executes.

    The false success is a second, separate defect: the executor sets
    `_lastTransactionMsg` and clears `_query`, but the write may leave the
    denormalised balance unchanged (Phase 1's defect), and the message is a
    transient value with no durable log. So the UI claims success while the data
    does not change.

    A third path routes through the same handler: `QuickNoteCard` calls
    `onQueryChange("f/ -$amount $acc $cat $text")`, reusing the text field as an
    execution channel.

    A fourth surface already exists and must be REUSED, not rebuilt: the Terminal
    screen (`AliasGuideScreen` in `ui/settings`) has a submit-gated input —
    keyboard Done executes, typing does not — which is the correct execution
    model. It is broken in three other ways: action commands (`kaka show graph`,
    `kaka ledger`, `kaka export`) print a stub line instead of navigating (in one
    build: "Action requested. Wait, action commands need a NavHost mapping
    here."; in committed main: "Use this command in the main search bar to
    navigate."); `InitAccount`, `AlterAccount`, and `DeleteAccount` print a "✓"
    success line BEFORE the write coroutine completes, with no confirmation for
    the destructive one; and the entire log lives in a
    `remember { mutableStateListOf }` inside the composable, so rotation or
    navigation erases the only record of what ran.

2.1 Separate the two surfaces into two components with distinct contracts:

    SEARCH BAR — read-only inventory retrieval.
      - Never mutates data. No `f/`, no `init/`, no `delete/`.
      - Grammar: `i/` `c/` `l/` `v/` `m/` `s/` and free text.
      - Debounced (~250–300 ms) reactive filtering is fine here.
      - Submitting shows results; it never writes.

    TERMINAL — all-powerful finance logging plus `kaka` support commands.
      - Mutations happen ONLY here, and ONLY on an explicit submit (keyboard Done
        or an on-screen submit button).
      - Typing never executes anything. Clearing the field never executes anything.
      - The parser is a PURE function: `String -> ParseResult`. No repository, no
        context, no side effects; unit-testable in isolation.
      - Execution is a separate, explicit step, run only on submit; every
        submission produces exactly one durable log entry.

2.2 Make partial input safe by construction:
      - Parser purity, as above.
      - No command resolves on a partial token. `+5` mid-way through `+5050` is
        an incomplete command, shown as pending, never executed.
      - Multi-digit numeric literals are consumed atomically; a trailing digit
        that could continue a number never closes the token.
      - Destructive commands require a second explicit confirmation. Never
        destructive-on-keystroke.

2.3 Alias resolution must be whole-token and unambiguous:
      - Match a full token against a full account/category name or full alias.
        Never prefix-match a single character.
      - If a token matches more than one candidate, do not guess: return
        `NeedsInput` offering the candidates.
      - If a token matches nothing, return `Failure` naming the unresolved token
        and listing available names or aliases.

2.4 Grammar to support in the terminal (extend the existing parser; keep existing
    spellings working):
      f/ ±amount <account> <category> <note> [@date]   record a transaction
      init/ <account> <±amount>                        set an account's balance
      xfer/ <amount> <from> <to> [note]                transfer
      due/ <in|out> <amount> <contact> [@date]         open a ledger entry
      settle/ <contact> [amount]                       settle a ledger entry
      account/ add <name> <type> [balance]
      account/ hide|show|archive|restore <name>
      delete/ <tx|account|entry> <selector>            destructive, confirmed
      kaka <show|graph|ledger|accounts|export|settings|help|search <text>>
      help
    The tokenizer is order-tolerant where unambiguous and lifts obvious typos
    into an error message with a suggestion — never into a silent wrong action.

2.5 Define and use one result contract:

        sealed interface TerminalResult
            Success(message, undoHint)     // only when the write committed
            Failure(reason, hint)          // visible, actionable
            NeedsInput(question, options)  // ambiguity or confirmation
            Pending                        // incomplete; nothing ran

    A `Success` may be produced only AFTER the transaction committed and the
    Phase 1 invariant was re-checked. Never emit success on the basis that a
    coroutine was launched.

2.6 Give the terminal a durable, scrollable log: each entry shows the raw
    command, the resolved interpretation, the outcome, and a timestamp. A user
    must be able to see that `init/bkash +5050` either committed or failed, and
    why. Replace the transient `_lastTransactionMsg` string.

2.7 Re-route `QuickNoteCard` to call the terminal's execute entry point directly,
    not the text-change handler.
2.8 Build the terminal ON TOP of the existing Terminal screen
    (`AliasGuideScreen`); keep its submit-gated input. Wire EVERY action command
    to real navigation: pass navigation lambdas into the screen (`onOpenGraph`,
    `onOpenLedger`, `onOpenExport`, `onOpenManual`, `onOpenSettings`,
    `onOpenAccounts`) or lift a `pendingAction` to the NavHost exactly as the
    dashboard already consumes one. Delete every stub string that tells the user
    to run the command somewhere else. An action command that cannot navigate is
    a defect, not a feature.
2.9 In the terminal, every result line comes from the `TerminalResult` contract
    of 2.5. Remove the premature "✓ Initialized", "✓ Renaming", "✓ Deleting"
    prints: a success line appears only after the write has committed and the
    Phase 1 invariant was re-checked; a failure line names the reason.
2.10 Destructive commands in the terminal (`delete/ ...`) require the same second
    explicit confirmation as everywhere else. The terminal must never delete on a
    single submit.
2.11 Make the terminal log durable across rotation and navigation: hold it in
    ViewModel state (or persist it), never in composable `remember` state. A user
    who rotates the phone must still see whether `init/bkash +5050` committed.

Tests required: parser purity (same input → same result, no side effects);
`init/bkash +5050` typed character-by-character commits exactly once for 5050 and
never for 5; `f/ -500 cash food` parses to the expected transaction; `delete/
bkash` does not resolve on `B` and requires confirmation; an ambiguous alias
returns `NeedsInput` with all candidates; an unknown alias returns `Failure`
naming the token; no `Success` is possible without a committed row (assert the
row exists); the log holds exactly one entry per submit; an action command typed in the
terminal produces a real navigation event (assert the nav lambda fired or
`pendingAction` was set); the terminal log survives a simulated configuration
change; `delete/` in the terminal demands confirmation and prints no success line
without a committed deletion.

GATE 2: no mutation can be triggered by typing, clearing, or any partial input;
every command returns a typed result; a success message is reachable only after a
committed write; the search bar cannot write; the terminal navigates for every
action command, prints success only after commit, and keeps its log across
rotation and navigation.

═══ PHASE 3 — ACCOUNT MANAGEMENT: DELETE, ARCHIVE, DEFAULTS ═══

Root causes: the account manager has no delete action at all; there are no managed
default accounts for Liabilities, Assets, and Capital; balance editing bypasses
`Money`.

3.1 Support delete and archive as distinct actions:
      - ARCHIVE (default, non-destructive): sets `is_active = 0`, hides the
        account from pickers and the dashboard, preserves all history.
      - DELETE (explicit, confirmed): allowed only when the account has no
        transactions and no ledger links, OR when the user explicitly chooses to
        reassign or retain history first. Never cascade a user's financial history
        away silently. If deletion is unsafe, explain why and offer archive.
3.2 Show, per account: transaction count, ledger link count, current balance.
    Confirmation names the account and states what happens to the rows.
3.3 Seed managed default accounts on first run, through a real migration (not a
    silent insert on read):
      - `Assets`       → ASSET
      - `Liabilities`  → LIABILITY
      - `Capital`      → CAPITAL
    Ensure a CASH account also exists. Seeding must be idempotent: re-running it,
    or importing a backup already containing these names, must not duplicate rows.
    Use the existing unique index on `name`.
3.4 If `AccountType` lacks needed members, extend it and migrate. Enum values are
    stored as text; the converter's `getOrDefault` fallback must not silently turn
    an unknown type into CASH — surface an error instead.
3.5 Balance editing in the manager must either create a real ADJUSTMENT
    transaction instead of silently overwriting `opening_balance`, OR clearly
    label itself an opening-balance edit and recalculate the balance cache. Pick
    one, document it, and make the UI say which one it is.
3.6 Account types must be visible and editable in the manager, with an
    explanation of what each type means for the Phase 5 analytics.
3.7 Add a startup self-check that REPORTS (does not hide) any account whose stored
    balance disagrees with the Phase 1 invariant.

Tests required: archive hides from pickers and dashboard but keeps history; delete
is refused when transactions exist, and the refusal names the reason; delete
succeeds when safe; seeded defaults are created exactly once and are idempotent
across restart and import; editing a balance upholds the invariant; an unknown
stored `AccountType` is reported rather than silently coerced.

GATE 3: delete and archive behave safely and predictably; the three managed
default accounts exist and survive restart and restore without duplication.

═══ PHASE 4 — LEDGER: CONTACT SEARCH AND DISCOVERY ═══

Root cause: the liability ledger has no way to find a contact. As entries grow,
the screen becomes unusable.

4.1 Add search/filter to the ledger by contact name and phone.
    `FinanceDao.searchLedgerEntries(...)` already exists — use it. Do not build a
    second search implementation and do not filter in memory.
4.2 Compose search with the existing filters (settled/unsettled, amount range,
    due-date range) via the already-defined parameters.
4.3 Add a contact view: one row per contact with net outstanding, entry count, and
    earliest due date. `ContactSummaryRow` already exists — wire it up and make it
    tappable to filter to that contact's entries.
4.4 Add device-contact autocomplete only when the contacts capability is enabled
    by its kill-switch, entirely on-device, with no upload. Degrade gracefully
    when it is off: free-text entry must always work.
4.5 Every LIKE query must be injection-safe: bind `?` for all values and escape
    wildcards with the existing `escapeLike()` helper. Never build SQL by string
    concatenation.
4.6 Preserve reactive updates: typing in ledger search must not block the main
    thread and must not reissue a database query per character faster than the UI
    can consume.

Tests required: partial-name search returns the right entries; phone search
matches; search composes with settled/amount/date filters; wildcards in user input
are treated literally; contact summary totals equal the sum of that contact's
unsettled entries; an empty result renders an explicit empty state.

GATE 4: a contact can be found by name or phone, filters compose, and no query is
built by string concatenation.

═══ PHASE 5 — ANALYTICS REBUILD ═══

Root cause: the graph screen does not work. It also measures the wrong things —
it derives "Liabilities" and "Receivables" from transactions typed `DEBT_ISSUE`
rather than from the ledger, so the chart and the ledger screen can disagree
about the same money.

5.1 DIAGNOSE FIRST and report before changing anything:
      - Reproduce the failure. Crash, blank chart, empty model, endless spinner,
        or a model whose series lengths differ?
      - Inspect `GraphScreen.kt`, `GraphViewModel.kt`, and `buildGraphState`.
      - Check the Vico API actually used (`Chart`, `lineChart`,
        `rememberStartAxis`, `rememberBottomAxis`, `AxisValueFormatter`,
        `FloatEntry`, `entryModelOf`) against the pinned Vico 1.15.0 API, and
        confirm the chart is hosted in a layout the library requires — a signature
        that compiles but yields an empty or zero-size host is a common cause here.
      - Check whether `entryModel` is null for the default selection and why.
      - Check series lengths: Vico requires equal-size entry lists per series.
      - Check the label formatter: `state.labels[value]` keyed by `Float` is
        fragile; keys must come from the same values the entries were built with.
5.2 Define the analytic contract, then rebuild to satisfy it:
      - Series: **Assets** (CASH + ASSET balances); **Liabilities** (LIABILITY
        accounts + outstanding PAYABLE ledger); **Receivables** (outstanding
        RECEIVABLE ledger); **Net Worth** (Assets − Liabilities + Receivables);
        **Capital** (CAPITAL balances) optional.
      - Liabilities and Receivables derive from the LEDGER, so chart and ledger
        can never disagree. Assert this in a test.
      - No bucket after today. No bucket before the earliest record unless an
        opening balance exists. No synthetic projection line.
      - Every series has the same number of entries, always.
      - An explicit, readable empty state when there is no data — never a blank
        box or an endless spinner.
      - A legend, a money-formatted value axis, and a way to read a specific
        bucket's values.
      - DAY/WEEK/MONTH buckets are contiguous and deterministic.
5.3 Keep `buildGraphState` a pure function over (transactions, accounts,
    categories, ledger entries, bucket, filters, clock) so it is fully
    unit-testable with a fixed clock. It is close to this already — preserve and
    extend that property.
5.4 Minor units are the source of truth. Converting to `Float` for rendering is
    acceptable ONLY at the final step and MUST NOT feed any computation. Document
    this so it cannot be mistaken for the authoritative value.
5.5 Open-source references: before writing chart code, consult the pinned
    library's own samples plus at least one alternative, so you use the current
    idiom rather than an obsolete one — for example the Vico repository's sample
    app (patrykandpatrick/vico, Apache-2.0), codeandtheory/YCharts, and
    PhilJay/MPAndroidChart (Java, but a widely used reference for axis and legend
    behaviour). Read them for API shape and configuration; do NOT copy code
    wholesale and do NOT add a dependency. Only if the pinned library demonstrably
    cannot satisfy 5.2, propose a swap with a written cost/benefit and wait for
    approval (Rule R-21).
5.6 Add a reconciliation assertion: at the final bucket, chart totals equal the
    sum of account balances and ledger outstanding amounts.

Tests required: graph state is non-null for a non-empty fixture; all series have
equal length; a transfer changes Net Worth by zero while moving Assets; a debt
issue raises Liabilities and Assets equally; a receivable raises Receivables and
lowers Assets; liabilities equal outstanding PAYABLE ledger; receivables equal
outstanding RECEIVABLE ledger; no bucket after today; an empty dataset yields the
explicit empty state; the final bucket reconciles with account and ledger totals;
bucket generation is deterministic with a fixed clock.

GATE 5: the chart renders for every valid selection, its series have equal length
and correct meanings, it reconciles with the ledger and accounts, and the old
`DEBT_ISSUE`-derived liability series is gone.

═══ PHASE 6 — LOCK AND PIN HARDENING ═══

6.1 The lock survives configuration change, rotation, recreation, and process
    restart. `savedInstanceState == null` is never the sole security decision.
    Rotating while locked stays locked; rotating while unlocked exposes no
    partially-authenticated screen.
6.2 Keep the existing background grace period, and prove it cannot be bypassed by
    rotation, recreation, navigation, or a stop/resume pair shorter than the grace
    window.
6.3 Document the exact policy for cold start, stop/resume, configuration change,
    permission dialogs and external activities, and process death.
6.4 `CryptoUtils.verifyPin` compares derived verifier BYTES with
    `MessageDigest.isEqual` (or equivalent constant-time comparison). No
    encoded-string `==`.
6.5 PBKDF2 never runs on the main thread. Secrets never appear in logs, exceptions,
    or saved instance state.
6.6 Keep plaintext-PIN migration for legacy installs: hash once, verify success,
    then erase the old value. Never reintroduce a plaintext state flow.
6.7 Lockout attempts and the lockout deadline stay persistent; their boundary
    behaviour is tested.

Tests/manual checks required: rotation while locked stays locked; rotation while
unlocked exposes nothing stale; backgrounding past grace re-locks; returning within
grace follows the documented policy; one biometric failure permits retry; repeated
PIN failures trigger lockout; restart does not clear lockout; verifier comparison
is constant-time over bytes; hashing does not block the main thread.

GATE 6: the lock holds across rotation, backgrounding, recreation, and restart,
and the PIN path contains no ordinary string comparison.

═══ PHASE 7 — PERMISSIONS AND MANIFEST TRUTH ═══

7.1 Inventory every declared permission and every call site that needs it. Remove
    permissions with no supported, gated feature. Never add a permission.
7.2 Make each app-level kill-switch gate the ACTUAL API CALL, not just the button
    that starts the feature. A hidden button is not a kill-switch.
7.3 When a switch is off, show an explicit explanation and a path to re-enable it.
    Never fail silently.
7.4 Request runtime permissions only at the moment the user starts the relevant
    feature — never in a batch at startup.
7.5 Handle denied, permanently denied, unavailable, and unsupported states without
    crashing.
7.6 Make the README match the final manifest and behaviour exactly. Do not describe
    the app as camera-only if it declares or uses contacts, microphone, SMS,
    notifications, or media permissions.
7.7 Keep backup and device-transfer rules explicit, minimal, and offline; do not
    broaden data transfer as a side effect.

Tests/manual checks required: app launches and browses with every switch off and no
permission prompt; each disabled feature shows its explanation; each enabled feature
requests only its own permission when invoked; denied permissions do not crash; the
manifest list and the README agree.

GATE 7: every declared permission is justified by a gated feature, and all
permission behaviour is truthful and graceful.

═══ PHASE 8 — MIGRATION TESTS, IMAGE ROUND-TRIP, DOCS ═══

8.1 Determine whether the existing migration test is genuinely a JVM/Robolectric
    test or an instrumented Android test. Do not label it instrumented unless it
    is under the correct source set with the configured runner.
8.2 Add REAL migration tests for every link in the chain (v1→v2, v2→v3, v3→v4,
    v4→v5) using `MigrationTestHelper` or equivalent: create the old version,
    insert representative rows with the old schema, apply the real migration, and
    validate. Assert that inventory rows, care tasks, opening balances, transaction
    amounts and direction, nullable categories, transfer fields, ledger rows and
    links, and the v4→v5 stored balances are all correct. Confirm no
    `fallbackToDestructiveMigration()` exists.
8.3 Keep serialization/import tests separate from migration tests. One is not a
    substitute for the other.
8.4 Verify image storage: capture, restore, backup, and device transfer all use one
    directory constant; restored paths point at files that exist; backup rules name
    the same database filename and folder the runtime uses. Add an export → wipe →
    import round-trip test for an item with an image.
8.5 Correct the README's phase, version, and schema claims to match reality.

GATE 8: real migrations are exercised end to end with data assertions, an image
round-trip passes, and documentation matches the code.

─────────────────────── C — CONTEXT ───────────────────────

Preserve these working concepts: the `Money` value class and its Room converters;
Room schema version 5; migrations `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`,
`MIGRATION_4_5`; nullable transaction categories; transfer legs via `transfer_id`
and `counter_account_id`; ledger `account_id` and `linked_transaction_id`; the
denormalised `accounts.balance_minor`; PBKDF2 PIN fields with persistent lockout;
account visibility preferences; offline export/import; the local WebP image store;
no `INTERNET` permission.

Known-risk areas, inspect in priority order:

1. `DashboardViewModel.onQueryChange` writes `_query` and parse/execute is derived
   reactively — commands run on every keystroke. This is the `init/bkash +5` bug
   and the `delete/` on `B` bug.
2. `ParseResult.InitAccount` and `ParseResult.DeleteAccount` both execute
   destructively from that reactive path, with no confirmation.
3. `Money((result.amount * 100).toLong())` and
   `initializeAccountBalance(alias, targetBalance: Double)` — truncating floating
   money in a write path.
4. `AliasGuideViewModel.createAccount(..., initialBalance: Double)`.
5. `AccountsManagerScreen` has no delete action, and edits balances via
   `toDoubleOrNull()`.
6. `ImportReader.restoreKakaZip()` may insert rows without recalculating balances.
7. `getAccountsWithMismatchedBalances()` may exist with no caller.
8. `MainActivity` uses `savedInstanceState == null` in a way that lets rotation
   bypass the lock.
9. `CryptoUtils.verifyPin` may compare encoded strings.
10. `MagicInputBar`'s `onDone` calls `onQueryChange(query)` when `onSubmit` is
    null — a no-op when the text has not changed: a "success" that does nothing.
11. `QuickNoteCard` executes a transaction by calling `onQueryChange("f/ ...")`.
12. The manifest requests more permissions than the README admits.
13. `LedgerScreen` formats money with `"%.2f".format(minorUnits / 100.0)` in the
    UI layer.
14. `.kotlin/errors/*` and other machine-local artifacts may be tracked.
15. Release signing may contain placeholder credentials.
16. `AliasGuideScreen` (the Terminal) stubs action commands with a "needs NavHost
    mapping"/"use the main search bar" line instead of navigating.
17. `AliasGuideScreen` prints "✓" for init/alter/delete before the write commits,
    and executes `delete/` with no confirmation.
18. The Terminal log is composable `remember` state — rotation or navigation
    erases the only record of what ran.

───────────────────── C — CONSTRAINTS ─────────────────────

1. Obey `Rules.md` absolutely. Where it and this prompt disagree, `Rules.md` wins.
2. No `INTERNET` permission, network library, cloud service, or network call in the
   app. (Build-time dependency resolution on the dev machine is permitted and is
   not a violation of this rule.)
3. No destructive migration and no silent data deletion.
4. Do not bump the Room version unless a schema change is genuinely required; if you
   do, write the migration in the same phase.
5. Do not rewrite the financial model or unrelated UI.
6. All multi-row writes use one transaction.
7. `balance_minor = opening_balance + credits - debits` — always.
8. Money storage and authoritative computation use integer minor units.
9. Legacy decimal conversion uses an explicit, documented rounding policy.
10. Never compare secrets with a string comparison.
11. No secret, PIN, salt, verifier, SDK path, or signing password in any output or
    in version control.
12. No blocking PBKDF2 or database work on the main thread.
13. Preserve GPL-3.0 licensing and existing offline export/import behaviour.
14. Keep `./gradlew :app:test` and `./gradlew :app:assembleDebug` green.
15. No new dependency without written approval.
16. Do not claim a test passed unless it ran.
17. Every changed behaviour needs a test or a documented manual verification step.
18. Antigravity specifics: keep artifact review ON; never auto-proceed; Planning
    Mode for phases 1–8; do not run parallel agents that could merge or skip gates
    — if agents run in parallel, each inherits `Rules.md` and none may proceed past
    a gate the other has not proven.

─────────────────────── O — OUTPUT ───────────────────────

After EVERY phase, stop and emit a checkpoint with exactly these sections:

  PHASE <n> — <name>: STATUS (done | blocked | needs decision)
  RULES LOADED               which rule files were active, and the exact
                             constraint text that governed this phase
  BASELINE / COMMANDS RUN    commands and trimmed output
  FILES CHANGED              one-line purpose per file
  DATA SAFETY                invariant, migrations, defaults, rollback risk
  SECURITY                   auth, PIN, permission, secret implications
  TESTS                      names, source set, exact assertions, command run
  MANUAL VERIFICATION        copy-pasteable steps and expected results
  DECISIONS                  alternatives considered and the chosen option
  RULES CHECK                each rule you could have violated and why you did
                             not; flag any you did violate, and revert it
  OPEN / UNRESOLVED          exact failures, unavailable environments, unknowns

Changed files: real unified diffs. New files: complete contents. Never write
"rest unchanged" or any placeholder inside a code block.

Antigravity artifacts to produce alongside each phase:
  - Implementation Plan artifact before writing code (Planning Mode).
  - Task List artifact kept current as steps complete.
  - Walkthrough artifact at phase end containing the checkpoint above, the commands
    run, and the rules that were active.
The artifacts are the review surface — the user verifies from them, not from raw
tool-call logs. Keep them accurate, and never mark an artifact item complete while
its gate is blocked.

After Phase 8, produce:

  FINAL INVARIANT STATEMENT
  MIGRATION / TEST COVERAGE MATRIX
  PERMISSION / MANIFEST MATRIX
  RULE COVERAGE MATRIX       each rule in Rules.md → the phase evidence that it held
  COMMAND GRAMMAR REFERENCE  search bar vs terminal, with examples
  SECURITY CHECKLIST
  REMAINING BACKLOG
  COMMANDS A MAINTAINER SHOULD RUN BEFORE MERGING

Do not report the work as complete if any gate is blocked, if an instrumented test
was written but not executed, or if any rule in `Rules.md` was violated.
