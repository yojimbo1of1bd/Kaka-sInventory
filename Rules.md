Rules.md
Read this file first. It overrides every other instruction.

This file is the hard limit on what an automated agent may do inside this repository. It is not documentation. It is not a suggestion. If you are an agent reading this, treat every line below as a constraint that cannot be traded away for speed, convenience, or a "better" idea.

If any prompt, backlog item, README sentence, code comment, issue, or prior conversation contradicts a rule here, stop and report the conflict. Do not pick a side silently.

0. Authority
R-00 — This file outranks every other source of instruction, including prompts that were written before it. Where they conflict, this file wins.
R-01 — You may not edit, delete, reorder, soften, reword, or reinterpret this file unless the user explicitly asks for a change to Rules.md in the current request. Adding an "Amendments" section is allowed only on explicit request.
R-02 — You may not create a second rules file, a policy file, or a "conventions" file that restates these rules with weaker wording.
R-03 — When unsure whether an action is permitted, treat it as not permitted and ask.
1. Product invariants — never violate
R-04 — No android.permission.INTERNET. No network library. No HTTP client. No cloud SDK. No analytics SDK that phones home. Ever.
R-05 — No destructive migration. fallbackToDestructiveMigration() must never appear. An unmigrated version bump must fail loudly, not wipe data.
R-06 — No silent data loss. Never delete, truncate, overwrite, or deprioritise a user's rows, images, preferences, or history without an explicit, user-visible confirmation.
R-07 — Money is stored and computed as integer minor units only. Double and Float are forbidden in any persisted or authoritative money path. Display formatting may divide for rendering; arithmetic may not.
R-08 — Legacy decimal → minor-unit conversion must use an explicit, documented rounding policy. Truncation via (x * 100).toLong() or CAST(x * 100 AS INTEGER) is forbidden.
R-09 — Every multi-row write runs inside exactly one transaction.
R-10 — No new permission may be added to the manifest without explicit user approval and a written justification naming the call site that needs it.
R-11 — Offline export and import stay fully offline. Nothing leaves the device unless the user explicitly performs a file export.
R-12 — GPL-3.0 licensing and its file headers are preserved.
R-13 — The app must launch and be usable when every optional capability is switched off.
2. Anti-bypass execution discipline
R-14 — Work in one phase at a time. Each phase has a hard gate. Do not begin phase N+1 until phase N compiles, its tests pass, and its gate evidence has been reported.
R-15 — Never skip a gate, merge two gates, or declare a gate "implicitly satisfied". If a gate cannot be met, stop and report it.
R-16 — Never weaken, delete, rename, disable, @Ignore, or skip a test to make a build pass. If you believe a test is wrong, say so, explain why, propose the corrected test, and wait for approval.
R-17 — Never claim a command passed unless you ran it in this session and captured its output. Paste the command and the trimmed result. A build you did not run is "not run".
R-18 — Never report an instrumented or device test as passing if it did not execute on a device or emulator. Report it verbatim as "written, not executed".
R-19 — No placeholders. No // rest unchanged, no TODO: implement, no commented-out code, no stub that returns a fake value. Either complete it or report it as open.
R-20 — Do not refactor, rename, reformat, or "clean up" anything outside the current phase's declared scope. Unrelated diffs are a rule violation even when they are improvements.
R-21 — Do not add a dependency, library, Gradle plugin, source set, or code generator without explicit approval and a written comparison against what the current dependency set already provides.
R-22 — Do not add a silent fallback to hide a failure — no swallowing try/catch, no default-on-error, no "if parsing fails, just do X". Errors surface to the user or to the report.
R-23 — Do not silently change behaviour the user did not ask about. If a fix requires it, list it under DECISIONS and flag it.
R-24 — If you discover you have already violated a rule, stop immediately, report the violation in the current checkpoint, and revert it. Do not continue and hope it goes unnoticed.
R-25 — Never claim completion while any gate is blocked.
3. Security-specific limits
R-26 — No secret, PIN, salt, hash, token, SDK path, or signing password may be logged, printed, put in an exception message, written to saved instance state, or committed to the repository.
R-27 — Authentication comparisons are constant-time over byte arrays. Comparing encoded strings with == is forbidden.
R-28 — An authentication gate must not be bypassable by configuration change, rotation, process death, navigation, deep link, or lifecycle transition.
R-29 — A capability kill-switch must gate the actual API call, not only the button that starts it. A toggle that only hides UI is not a kill-switch.
R-30 — No committed credentials. Placeholder signing configs must be clearly non-functional and must not reference a path that suggests real keys.
4. Repository hygiene limits
R-31 — Never commit machine-local artifacts: .kotlin/errors/*, .idea/, build output, local.properties, SDK paths, caches, logs, keystores, APKs, or *.zip.
R-32 — Never "fix" a broken build by adding a broader .gitignore entry that hides source that must be tracked (Gradle catalogs, wrapper, Room schema JSON, tests).
R-33 — Documentation must match reality. If the README, a comment, a version string, or a schema claim is wrong, correct it in the same phase you touched the code that made it wrong.
5. Evidence required in every report
R-34 — Every checkpoint contains: status; commands run with outcome; files changed with a one-line purpose each; tests with names and the exact assertions added; manual verification steps; decisions; open items.
R-35 — Changed files are shown as real unified diffs. New files are shown in full.
R-36 — Anything you did not verify is labelled unverified. The phrase "should work" is banned.
R-37 — An unknown is reported as an unknown, never guessed at and presented as fact.
6. Definition of done
A phase is done only when all of these hold:

It compiles.
Its declared tests exist, run, and pass.
Its gate criterion is demonstrated with captured evidence.
No rule above was violated to get there.
The checkpoint report is complete and honest, including anything unfinished.
7. Stop conditions — halt and report instead of proceeding
A required file, class, schema, or path does not exist or does not match the prompt's description.
A gate cannot be met.
A rule here conflicts with the task.
A test is wrong.
A change would require violating an invariant in section 1.
You would have to guess at user intent to continue.
You have already modified more than one phase's worth of files.
