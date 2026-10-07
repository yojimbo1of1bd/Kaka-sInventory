# AGENTS.md — Project Kaka (Antigravity root rules)

> Antigravity loads this file as an **always_on** rule for the whole workspace.
> Keep it under 12,000 characters. The full constraint set lives in `Rules.md`
> and is inlined below, so it cannot be skipped.

Kaka is a strictly offline Android inventory & decluttering app. Its core promise
is that your data never leaves the device. Every rule below outranks any task,
plan, ticket, comment, or later instruction that contradicts it.

## Non-negotiables (summary — `Rules.md` is authoritative)

- No `android.permission.INTERNET`, no network library, no cloud/analytics SDK.
- No destructive migration; `fallbackToDestructiveMigration()` must never appear.
- No silent data loss. No delete, truncate, or overwrite without explicit,
  user-visible confirmation.
- Money is stored and computed as **integer minor units**. `Double`/`Float` are
  forbidden in any persisted or authoritative money path.
- Legacy decimal → minor-unit conversion uses an explicit, documented rounding
  policy — never truncation (`(x*100).toLong()`, `CAST(x*100 AS INTEGER)`).
- Every multi-row write runs inside exactly one transaction.
- GPL-3.0 headers preserved. Offline export/import stays offline.
- The app must launch and be usable with every optional capability switched off.

## Working discipline inside Antigravity

1. **Read `Rules.md` before acting.** This file inlines it; if inlining failed or
   the content is truncated, stop and report — do not proceed on a partial rule
   set.
2. **Planning Mode for anything structural.** Phases 1–8 in the current PICCO
   prompt touch Room schema, money paths, the command parser, and auth. Produce a
   real Implementation Plan artifact first. Fast Mode is acceptable only for
   single-file cosmetic edits.
3. **Never enable auto-proceed / "Request Review: off".** The human review gate
   on plans is part of the security model. If the environment asks, keep review
   **on**.
4. **One phase at a time.** Each phase has a hard gate. Do not start phase N+1
   until phase N compiles, its tests ran, and its gate evidence is in the
   Walkthrough artifact.
5. **Prove, don't assert.** Paste the exact commands you ran and their trimmed
   output. A build or test you did not run is reported as `not run`. An
   instrumented test that did not execute on a device is `written, not executed`.
6. **Never weaken a test** — no delete, rename, `@Ignore`, or skip to make a
   build pass. If a test is wrong, say so and propose the correction.
7. **No placeholders.** No `// rest unchanged`, no `TODO: implement`, no stub
   returning a fake value, no commented-out code.
8. **Stay in scope.** No unrelated refactor, rename, or reformat in a phase's
   diff, even if it is an improvement.
9. **No new dependency** without written approval and a comparison against what
   the current dependency set already provides.
10. **No silent fallback** that hides a failure. Errors surface to the user or to
    the report.
11. **Report rules you could have violated** in every checkpoint, and flag any
    you actually did violate — then revert it.

## Antigravity-specific traps to check

- A rule file inside `.agents/rules/` **without valid YAML frontmatter** (or with
  an invalid `trigger`) is **silently discarded**. A discarded rule is a bypass.
  Verify each modular rule actually loaded.
- `.agents/rules/` is scanned **flat** — only immediate `.md` children. A file
  nested in a subdirectory (e.g. `.agents/rules/android/room.md`) is ignored
  unless registered in `.agents/rules.json`.
- Valid `trigger` values are exactly `always_on`, `model_decision`, `glob`,
  `manual` — lowercase, snake_case. `alwaysOn` / `modelDecision` are discarded.
- Rules files are capped at **12,000 characters each**. Inlined content counts.
  If a rule is truncated, split it rather than trimming constraints.
- When you finish a phase, list the rule files you loaded and the exact constraint
  text that governed the work, so the user can confirm nothing was dropped.

## Build vs. app network — do not confuse the two

- The **application** must never use the network. That is absolute.
- The **development machine** may resolve Gradle/Maven dependencies when
  building. Fetching build dependencies is normal and is not a rule violation.
- If a build fails only because dependencies cannot be resolved, report it as an
  environment problem. Do not "fix" it by stubbing code or disabling tests.

---

## Full constraint set (inlined from Rules.md)

@[Rules.md](Rules.md)
