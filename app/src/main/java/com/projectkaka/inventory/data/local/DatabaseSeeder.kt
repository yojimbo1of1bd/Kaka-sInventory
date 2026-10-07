package com.projectkaka.inventory.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.CategoryType

/**
 * Seeds default accounts and financial categories on first install or DB reset.
 *
 * Aliases are tuned for a Bangladeshi university student:
 * - Bengali transliterations (khabar, cha, bhat, riksha, pagaar …)
 * - Local mobile-money brands (bKash, Nagad, Rocket)
 * - Student slang (tiffin, addaa, photocopy, xerox …)
 *
 * All strings are SQL-safe literals; no user input reaches this class.
 */
object DatabaseSeeder {

    fun seed(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        seedAccounts(db, now) // legacy v1 format (REAL opening_balance)
        seedCategories(db, now)
    }

    fun seedV6(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        seedAccountsV6(db, now)
        seedCategories(db, now)
        seedPhase3Defaults(db, now)
    }

    // ── Accounts ─────────────────────────────────────────────────────────

    private fun seedAccounts(db: SupportSQLiteDatabase, now: Long) {
        data class Seed(val name: String, val type: AccountType, val aliases: String)

        val accounts = listOf(
            Seed("Cash",   AccountType.CASH,      "cash,naqd,haat,hand,pocket,taka,haaterkhor"),
            Seed("bKash",  AccountType.CASH,      "bkash,bikash,bk"),
            Seed("Nagad",  AccountType.CASH,      "nagad,ngd"),
            Seed("Rocket", AccountType.CASH,      "rocket,rkt,dbbl"),
            Seed("Bank",   AccountType.CASH,      "bank,account,saving,savings,sonchoy"),
            Seed("Credit", AccountType.LIABILITY, "credit,udhar,dhar,loan,joma")
        )

        accounts.forEach { acct ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts (name, type, aliases, opening_balance, is_active, created_at)
                VALUES ('${acct.name}', '${acct.type.name}', '${acct.aliases}', 0.0, 1, $now)
                """.trimIndent()
            )
        }
    }

    private fun seedAccountsV6(db: SupportSQLiteDatabase, now: Long) {
        data class Seed(val name: String, val type: AccountType, val aliases: String)

        val accounts = listOf(
            Seed("Cash",   AccountType.CASH,      "cash,naqd,haat,hand,pocket,taka,haaterkhor"),
            Seed("bKash",  AccountType.CASH,      "bkash,bikash,bk"),
            Seed("Nagad",  AccountType.CASH,      "nagad,ngd"),
            Seed("Rocket", AccountType.CASH,      "rocket,rkt,dbbl"),
            Seed("Bank",   AccountType.CASH,      "bank,account,saving,savings,sonchoy"),
            Seed("Credit", AccountType.LIABILITY, "credit,udhar,dhar,loan,joma")
        )

        accounts.forEach { acct ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts (name, type, aliases, opening_balance, balance_minor, is_active, created_at)
                VALUES ('${acct.name}', '${acct.type.name}', '${acct.aliases}', 0, 0, 1, $now)
                """.trimIndent()
            )
        }
    }

    fun seedPhase3Defaults(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        data class Seed(val name: String, val type: AccountType, val aliases: String)

        val accounts = listOf(
            Seed("Assets",      AccountType.ASSET,     "assets,sompod,property"),
            Seed("Liabilities", AccountType.LIABILITY, "liabilities,daya,dhyan"),
            Seed("Capital",     AccountType.CAPITAL,   "capital,equity,muldon"),
            Seed("Cash",        AccountType.CASH,      "cash"),
            Seed("Sales Revenue", AccountType.REVENUE, "sales,bichal"),
            Seed("COGS",        AccountType.EXPENSE,   "cogs,cost of goods sold"),
            Seed("Inventory",   AccountType.ASSET,     "inventory,stock,mala"),
            Seed("Charity Expense", AccountType.EXPENSE, "charity,zakat,sadaqah"),
            Seed("Loss",        AccountType.EXPENSE,   "loss,lokshan"),
            Seed("Prepaid Expenses", AccountType.ASSET, "prepaid,advance"),
            Seed("Unearned Revenue", AccountType.LIABILITY, "unearned,advance_income")
        )

        accounts.forEach { acct ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts (name, type, aliases, opening_balance, balance_minor, is_active, created_at)
                VALUES ('${acct.name}', '${acct.type.name}', '${acct.aliases}', 0, 0, 1, $now)
                """.trimIndent()
            )
        }
    }

    // ── Financial Categories ─────────────────────────────────────────────

    private fun seedCategories(db: SupportSQLiteDatabase, now: Long) {
        data class Seed(val name: String, val type: CategoryType, val aliases: String)

        val categories = listOf(
            // ── Expense ──
            Seed(
                "Food", CategoryType.EXPENSE,
                "food,khabar,lunch,dinner,breakfast,meal,eat,snack,tiffin,khawa," +
                    "biryani,cha,tea,nasta,bhat,rice,ruti,kacchi,fuchka,daal,mach,mangsho"
            ),
            Seed(
                "Travel", CategoryType.EXPENSE,
                "travel,uber,rickshaw,riksha,bus,train,pathao,transport,ride,fare," +
                    "rick,auto,cng,tempo,ola,obhimukh,jatayat"
            ),
            Seed(
                "Bills", CategoryType.EXPENSE,
                "bills,bill,electric,wifi,internet,mobile,recharge,rent,current," +
                    "biddut,flexiload,gp,robi,airtel,teletalk,pani,gas"
            ),
            Seed(
                "Academic", CategoryType.EXPENSE,
                "academic,book,print,photocopy,xerox,course,stationery,pen,notebook," +
                    "copy,boi,khata,lab,tuition,coaching,exam,fee,fees"
            ),
            Seed(
                "Clothing", CategoryType.EXPENSE,
                "clothing,clothes,shirt,pant,shoe,dress,kapor,jama,lungi,panjabi,sandal"
            ),
            Seed(
                "Health", CategoryType.EXPENSE,
                "health,medicine,doctor,pharmacy,medical,hospital,oushodh,daktar,pathology"
            ),
            Seed(
                "Entertainment", CategoryType.EXPENSE,
                "entertainment,movie,game,subscription,fun,hangout,addaa,adda,cinema,natok,concert"
            ),
            Seed(
                "Gadgets", CategoryType.EXPENSE,
                "gadgets,gadget,electronics,phone,laptop,charger,cable,headphone,earphone," +
                    "adapter,pendrive,mouse,keyboard"
            ),
            Seed(
                "Grooming", CategoryType.EXPENSE,
                "grooming,haircut,salon,shave,cream,soap,shampoo,nailcut,parlour"
            ),
            Seed(
                "Gifts", CategoryType.EXPENSE,
                "gifts,gift,present,upohar,donation,dan,daan"
            ),
            Seed(
                "Miscellaneous", CategoryType.EXPENSE,
                "misc,other,random,extra,miscellaneous,ajob,onnanno"
            ),
            // ── Income ──
            Seed(
                "Salary", CategoryType.INCOME,
                "salary,pay,income,earning,beton,pagaar,maash"
            ),
            Seed(
                "Family", CategoryType.INCOME,
                "family,parents,baba,maa,mama,chacha,allowance,pocket,abba,ammu,abbu,amma," +
                    "dada,dadi,nana,nani,fupu,khalu,mami"
            ),
            Seed(
                "Freelance", CategoryType.INCOME,
                "freelance,gig,project,client,upwork,fiverr,toptal,contract"
            ),
            Seed(
                "Scholarship", CategoryType.INCOME,
                "scholarship,stipend,award,grant,brishti,puroskar"
            ),
            Seed(
                "Refund", CategoryType.INCOME,
                "refund,cashback,return,ferot,payback"
            ),
            Seed(
                "Other Income", CategoryType.INCOME,
                "other_income,bonus,prize,lottery,baksheesh,eid,tip"
            )
        )

        categories.forEach { cat ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO financial_categories (name, type, aliases, created_at)
                VALUES ('${cat.name}', '${cat.type.name}', '${cat.aliases}', $now)
                """.trimIndent()
            )
            val accType = if (cat.type == CategoryType.INCOME) AccountType.REVENUE else AccountType.EXPENSE
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts (name, type, aliases, opening_balance, balance_minor, is_active, created_at)
                VALUES ('${cat.name}', '${accType.name}', '${cat.aliases}', 0, 0, 1, $now)
                """.trimIndent()
            )
        }
    }
}
