package com.projectkaka.inventory.search

/**
 * Linux-style manual pages repository and Debits & Credits Handbook for Project Kaka.
 */
object TerminalManPages {

    fun getManPage(topic: String): String {
        val normalized = topic.trim().lowercase().removeSuffix("/")
        return when (normalized) {
            "?", "help", "debits", "credits", "accounting", "handbook", "rules" -> handbook()
            "f", "financial" -> manF()
            "kaka" -> manKaka()
            "snapshot" -> manSnapshot()
            "due" -> manDue()
            "settle" -> manSettle()
            "log" -> manLog()
            "xfer", "transfer" -> manXfer()
            "cashout" -> manCashout()
            "charge" -> manCharge()
            "bal", "balance" -> manBal()
            "report" -> manReport()
            "init" -> manInit()
            "account", "alter" -> manAccount()
            "clear" -> manClear()
            "cmatrix", "matrix" -> manCMatrix()
            "pop", "push" -> manPop()
            "history" -> manHistory()
            "verify" -> manVerify()
            else -> generalIndex(topic)
        }
    }

    private fun handbook(): String = """
================================================================================
PROJECT KAKA MANUAL                                          DEBITS & CREDITS(7)
================================================================================

NAME
    accounting - The Rigorous Handbook of Debits and Credits in Project Kaka

THE FUNDAMENTAL ACCOUNTING EQUATION
    Assets + Expenses = Liabilities + Equity + Revenue

    Left Side (Normal Debit):     Right Side (Normal Credit):
    • Assets (Cash, bKash, Stock) • Liabilities (Debts, Payables)
    • Expenses (Food, Utilities)  • Equity (Capital, Retained Earnings)
                                  • Revenue (Salary, Sales, Gifts)

THE GOLDEN RULES OF DOUBLE-ENTRY
    ┌──────────────┬──────────────────────────┬──────────────────────────┐
    │ Account Type │ DEBIT (Dr) Effect        │ CREDIT (Cr) Effect       │
    ├──────────────┼──────────────────────────┼──────────────────────────┤
    │ ASSET        │ Increases (+) asset      │ Decreases (-) asset      │
    │ EXPENSE      │ Increases (+) expense    │ Decreases (-) expense    │
    │ LIABILITY    │ Decreases (-) liability  │ Increases (+) liability  │
    │ EQUITY       │ Decreases (-) capital    │ Increases (+) capital    │
    │ REVENUE      │ Decreases (-) revenue    │ Increases (+) revenue    │
    └──────────────┴──────────────────────────┴──────────────────────────┘

THE IMMUTABLE KAKA COMMAND RULE
    DEBIT ALWAYS COMES FIRST, THEN CREDIT:
        f/ [±]<amount> <DebitAccount> <CreditAccount> ["Comments"] [@date]

EVERYDAY TRADE-OFF EXAMPLES

    1. Buying lunch with bKash (Expense Trade-off):
       You trade an asset (bKash) for consumption (lunch).
       • Debit:  exp (Operating Expenses increases)
       • Credit: bkash (Asset decreases)
       Command:
           f/ -120 exp bkash "For lunch"

    2. Receiving monthly salary or gift (Income Inflow):
       You receive an asset (bKash) funded by earnings (Salary).
       • Debit:  bkash (Asset increases)
       • Credit: salary (Revenue increases)
       Command:
           f/ +5050 bkash salary "Father sent me 5050 taka for month August"

    3. Borrowing money from Babul Mama (Liability):
       Do NOT use f/. Use the ledger command so operating cash isn't mixed up:
           due/ out 500 babul mama cash "Grocery loan"

    4. Settling multiple debts with Babul Mama:
       Use the FIFO settlement engine:
           settle/ "babul mama" 500 cash

    5. Rebalancing money between wallets:
           xfer/ 1000 cash bkash "Top up bKash from cash"

SEE ALSO
    man f/, man log/, man settle/, man snapshot/, man report/
================================================================================
""".trimIndent()

    private fun manF(): String = """
================================================================================
PROJECT KAKA MANUAL                                                         F(1)
================================================================================

NAME
    f/ - Record a rigorous double-entry financial transaction

SYNOPSIS
    f/ [±]<amount> <debit_account> <credit_account> ["comment/note"] [@date]

DESCRIPTION
    Records an immutable transaction into the double-entry journal.
    In accordance with universal accounting principles, THE DEBIT ACCOUNT
    MUST ALWAYS BE SPECIFIED FIRST, FOLLOWED BY THE CREDIT ACCOUNT.

    Generic accounts are automatically resolved:
    • 'exp' or 'expense' maps to Operating Expenses (AccountType.EXPENSE).
    • 'inc' or 'income' maps to General Income (AccountType.REVENUE).
    • Specific accounts (e.g. 'bkash', 'cash', 'salary', 'gift') are matched
      against existing accounts or created on the fly.

EXAMPLES
    f/ -120 exp bkash "For lunch"
        Debits Expenses by ৳120.00, Credits bKash by ৳120.00.

    f/ +5050 bkash salary "Father sent me 5050 taka for month August"
        Debits bKash by ৳5,050.00, Credits Salary by ৳5,050.00.

    f/ 450 exp cash "DESCO electricity bill for September"
        Debits Expenses by ৳450.00, Credits Cash by ৳450.00.

    f/ 2000 bkash gift "Eid gift from elder brother" @2026-06-17
        Backdates a ৳2,000 gift credit to bKash.

SEE ALSO
    man ?, man log/, man xfer/, man report/
================================================================================
""".trimIndent()

    private fun manLog(): String = """
================================================================================
PROJECT KAKA MANUAL                                                       LOG(1)
================================================================================

NAME
    log/ - Inspect and audit chronological financial trade-offs

SYNOPSIS
    log/ [month | date | time_range | today | all]

DESCRIPTION
    Reconstructs all financial tradeoffs recorded in the journal.
    Unlike rapid summary screens, log/ reveals every double-entry flow:
    Debit and Credit accounts, monetary movement, comment purpose,
    and whether the entry was created by a terminal command or by the app UI.

FILTERS
    log/             Display trade-offs for the current month.
    log/ august      Display all trade-offs during August of the current year.
    log/ 2026-10-07  Display trade-offs on a specific date.
    log/ 10:00-18:00 Display today's trade-offs within a time range.
    log/ today       Display all trade-offs recorded today.
    log/ all         Display full lifetime financial journal log.

SEE ALSO
    man f/, man report/, man history/
================================================================================
""".trimIndent()

    private fun manSettle(): String = """
================================================================================
PROJECT KAKA MANUAL                                                    SETTLE(1)
================================================================================

NAME
    settle/ - FIFO multi-debt settlement and partial liability clearing

SYNOPSIS
    settle/ <contact> [amount] [payment_account]

DESCRIPTION
    Settles outstanding personal liabilities (payables/receivables).
    If a contact has multiple outstanding entries, the engine uses
    First-In First-Out (FIFO) allocation:

    1. Settle All (No Amount):
       settle/ "babul mama" cash
       Clears all open liabilities for the contact in FIFO order.

    2. Excess Payment:
       settle/ "babul mama" 500 cash
       If you only owe ৳123.00, the system settles all entries in full,
       deducts only ৳123.00 from Cash, and reports ৳377.00 change returned.

    3. Partial Payment:
       settle/ "babul mama" 50 cash
       If the earliest debt is ৳100.00, the entry is split:
       ৳50.00 is settled and linked to a journal entry, and a new open
       remainder entry for ৳50.00 is preserved in Room.

SEE ALSO
    man due/, man ?, man log/
================================================================================
""".trimIndent()

    private fun manDue(): String = """
================================================================================
PROJECT KAKA MANUAL                                                       DUE(1)
================================================================================

NAME
    due/ - Record personal liabilities and receivables in the ledger

SYNOPSIS
    due/ <in|out> <amount> <contact> [note] [@date]

DESCRIPTION
    Tracks debts without immediately depleting operating accounts:
    • 'out': You owe money to someone (Payable / Liability).
    • 'in':  Someone owes money to you (Receivable / Asset).

EXAMPLES
    due/ out 500 "babul mama" grocery
    due/ in 1200 karim loan @2026-10-01

SEE ALSO
    man settle/, man ?
================================================================================
""".trimIndent()

    private fun manClear(): String = """
================================================================================
PROJECT KAKA MANUAL                                                       CLEAR(1)
================================================================================

NAME
    clear/ - Clear all terminal output history and reset view

SYNOPSIS
    clear/  (alias: clear)

DESCRIPTION
    Wipes the active terminal console output buffer, removing all previous command
    echoes, calculation lines, and audit summaries.

SEE ALSO
    man history/, man ?
================================================================================
""".trimIndent()

    private fun manCMatrix(): String = """
================================================================================
PROJECT KAKA MANUAL                                                     CMATRIX(1)
================================================================================

NAME
    cmatrix/ - Terminal falling neon digital rain screensaver

SYNOPSIS
    cmatrix/  (aliases: cmatrix, matrix/, matrix)

DESCRIPTION
    Launches a full-screen Matrix screensaver displaying cascading neon glyphs,
    alphanumerics, and currency symbols against a deep black void.
    Tap anywhere on the screen to dismiss and return to the terminal.

SEE ALSO
    man clear/, man snapshot/
================================================================================
""".trimIndent()

    private fun manPop(): String = """
================================================================================
PROJECT KAKA MANUAL                                                         POP(1)
================================================================================

NAME
    pop/ - Detach terminal into a floating, hovering draggable overlay window

SYNOPSIS
    pop/   (alias: pop)  - Detaches terminal into a floating window
    push/  (alias: push) - Docks floating terminal back to full screen

DESCRIPTION
    pop/ minimizes the terminal into a hovering draggable overlay pill/window that
    persists across all screens of Project Kaka. Allows executing commands while
    navigating inventory items, boxes, or financial reports.
    push/ (or tapping the Terminal icon from Home) docks the terminal back.

SEE ALSO
    man kaka/, man clear/
================================================================================
""".trimIndent()

    private fun manSnapshot(): String = """
================================================================================
PROJECT KAKA MANUAL                                                  SNAPSHOT(1)
================================================================================

NAME
    snapshot/ - Hierarchical Merkle Tree whole-system cryptographic integrity engine

SYNOPSIS
    snapshot/ <take|check|list|delete> [passphrase|id]

DESCRIPTION
    Builds and audits a deterministic 5-branch Merkle state tree covering the
    entire system:
    1. Inventory Branch (items table: ID, status, values, categories, links)
    2. Finance Branch (accounts, journal entries, postings, ledger dues)
    3. Documents Branch (documents, document pages)
    4. Boxes/Cartons Branch (baskets, carton cross-references)
    5. Media Files Branch (streaming 64KB O(1) RAM SHA-256 hashes of on-disk photos)

COMMANDS
    snapshot/ take [passphrase]   (alias: snapshot/ create)
        Constructs a complete Merkle state tree, salts with user passphrase, and
        appends the snapshot to the protected secret ledger (snapshots_history.json).
        If no passphrase is typed, you will be prompted for one.

    snapshot/ check [passphrase]  (alias: snapshot/ verify)
        Computes a live Merkle tree snapshot right now and audits against the
        latest snapshot baseline. Pinpoints exact branches altered if any records
        or files were modified, restored, or deleted.

    snapshot/ list
        Lists all snapshots in history with timestamps, protection status, and
        entity counts. Raw 64-character SHA-256 strings are kept PROTECTED and hidden.

    snapshot/ delete <id | all>
        Deletes a specific snapshot by ID (e.g. 'snapshot/ delete SNAP-123456')
        or clears all history ('snapshot/ delete all').

SEE ALSO
    man verify/, man log/, man ?
================================================================================
""".trimIndent()

    private fun manXfer(): String = """
================================================================================
PROJECT KAKA MANUAL                                                      XFER(1)
================================================================================

NAME
    xfer/ - Transfer money between two asset accounts

SYNOPSIS
    xfer/ <amount> <from_account> <to_account> [note]

DESCRIPTION
    Executes an asset rebalancing transfer. Debits to_account and Credits from_account.

EXAMPLE
    xfer/ 1000 cash bkash "Load bKash balance"

SEE ALSO
    man cashout/, man f/
================================================================================
""".trimIndent()

    private fun manCashout(): String = """
================================================================================
PROJECT KAKA MANUAL                                                   CASHOUT(1)
================================================================================

NAME
    cashout/ - Withdraw cash from mobile wallets with configured service fee

SYNOPSIS
    cashout/ <amount> <source_wallet> [target_cash_account]

DESCRIPTION
    Transfers principal to Cash while automatically deducting the configured
    cashout percentage fee (set via charge/) against Capital.

EXAMPLE
    cashout/ 1000 bkash
    Deducts ৳1,000 to Cash and ৳18.50 fee from bKash (1.85%).

SEE ALSO
    man charge/, man xfer/
================================================================================
""".trimIndent()

    private fun manCharge(): String = """
================================================================================
PROJECT KAKA MANUAL                                                    CHARGE(1)
================================================================================

NAME
    charge/ - Configure cashout service fee percentage rates

SYNOPSIS
    charge/ <account> <*rate% | clear>
    charge/ all

EXAMPLES
    charge/ bkash *1.85%
    charge/ nagad *1.5%
    charge/ all
================================================================================
""".trimIndent()

    private fun manBal(): String = """
================================================================================
PROJECT KAKA MANUAL                                                       BAL(1)
================================================================================

NAME
    bal/ - Check account balances

SYNOPSIS
    bal/ <account_name | all>

DESCRIPTION
    Displays current dynamically computed double-entry balances.
================================================================================
""".trimIndent()

    private fun manReport(): String = """
================================================================================
PROJECT KAKA MANUAL                                                    REPORT(1)
================================================================================

NAME
    report/ - Generate financial statements

SYNOPSIS
    report/ <bs | pnl | trend>

DESCRIPTION
    • report/ bs    Generates complete Balance Sheet (Assets = Liabilities + Equity).
    • report/ pnl   Generates Income Statement (Net Income = Revenue - Expenses).
    • report/ trend Opens the Analytics graph view.
================================================================================
""".trimIndent()

    private fun manInit(): String = """
================================================================================
PROJECT KAKA MANUAL                                                      INIT(1)
================================================================================

NAME
    init/ - Initialize account with opening balance

SYNOPSIS
    init/ <account> <amount>

DESCRIPTION
    Sets or adjusts the opening balance of an account and synchronizes Capital equity.
================================================================================
""".trimIndent()

    private fun manAccount(): String = """
================================================================================
PROJECT KAKA MANUAL                                                   ACCOUNT(1)
================================================================================

NAME
    account/ - Manage account classifications, types, and archives

SYNOPSIS
    account/ add <name> [type] [opening_balance]
    account/ type <name> <type>
    account/ archive <name>
    alter/ <name> type <new_type>
    alter/ <name> rename <new_name>

VALID TYPES
    CASH, ASSET, LIABILITY, CAPITAL, EXPENSE, REVENUE
================================================================================
""".trimIndent()

    private fun manHistory(): String = """
================================================================================
PROJECT KAKA MANUAL                                                   HISTORY(1)
================================================================================

NAME
    history/ - Review passed terminal commands

SYNOPSIS
    history/ [count]
================================================================================
""".trimIndent()

    private fun manKaka(): String = """
================================================================================
PROJECT KAKA MANUAL                                                      KAKA(1)
================================================================================

NAME
    kaka - UI navigation and action shortcuts

COMMANDS
    kaka show graph     Open Analytics & Trajectory screen.
    kaka show alias     Open Terminal Reference Manual.
    kaka ledger         Open Personal Debt & Liability Ledger.
    kaka accounts       Open Accounts Manager screen.
    kaka export         Open Backup, Export & Import screen.
    kaka settings       Open App Settings.
================================================================================
""".trimIndent()

    private fun manVerify(): String = """
================================================================================
PROJECT KAKA MANUAL                                                    VERIFY(1)
================================================================================

NAME
    verify/ - Cryptographic integrity audit for items, files, and image storage

SYNOPSIS
    verify/ [item_id | filename | images | all]

DESCRIPTION
    Computes streaming SHA-256 cryptographic checksums and validates files
    against the baseline snapshot manifest and sidecars.

OPTIONS & TARGETS
    verify/ <item_id>
        Verifies the photo attached to a specific inventory item.
        Example: 'verify/ 3' audits Item #3's image, displays its file size,
        full SHA-256 hash, and baseline match status.

    verify/ <filename>
        Verifies a specific document scan or photo by filename or prefix.
        Example: 'verify/ doc_page_1.webp' or 'verify/ kaka_174000.webp'

    verify/ images  (or 'verify/' or 'verify/ all')
        Performs a global audit across all stored images and scans in sandbox.
        Displays total size verified, individual file statuses, and alerts.

SEE ALSO
    man snapshot/, man ?, man f/
================================================================================
""".trimIndent()

    private fun generalIndex(topic: String): String = """
No man entry for '$topic'.
Try:
  help/ ?         (The Rigorous Debits & Credits Handbook)
  man f/          (Double-entry financial entries)
  man log/        (Financial trade-offs query)
  man settle/     (Multi-debt FIFO settlements)
  man snapshot/   (Cryptographic integrity snapshots)
  man kaka/       (System shortcuts)
  man report/     (Balance sheet and P&L reports)
""".trimIndent()
}
