---
trigger: always_on
description: "Project Kaka non-negotiable product and data invariants. Always active."
---

# Kaka — Core invariants (always active)

These are the product invariants from `Rules.md`, restated for Antigravity's
always-on rule scope. `Rules.md` remains authoritative.

## Offline

- Never add `android.permission.INTERNET`, a network library, an HTTP client, or
  a cloud/analytics SDK. The app is strictly offline by design.
- The development machine may resolve Gradle dependencies at build time. That is
  not a violation.

## Data safety

- No destructive migration. `fallbackToDestructiveMigration()` must never appear.
- An unmigrated version bump must fail loudly, never wipe the user's data.
- Never delete, truncate, overwrite, or deprioritise user rows, images,
  preferences, or history without explicit, user-visible confirmation.

## Money

- Money is stored and computed as integer minor units. `Double` and `Float` are
  forbidden in any persisted or authoritative money path.
- Display formatting may divide for rendering; arithmetic may not.
- Legacy decimal → minor-unit conversion uses an explicit, documented rounding
  policy. Truncation is forbidden.
- Invariant: `balance_minor = opening_balance + credits - debits`, always.

## Writes

- Every multi-row write runs inside exactly one transaction.
- All affected account balances are recalculated before the transaction commits.
- A success message is only emitted after the write has committed and the
  invariant has been re-checked.

## Security

- No secret, PIN, salt, verifier, SDK path, or signing password is logged, printed
  in an exception, written to saved instance state, or committed.
- Authentication comparisons are constant-time over byte arrays.
- The lock cannot be bypassed by rotation, configuration change, recreation,
  navigation, or process death.
- A capability kill-switch gates the actual API call, not just the button.

## Hygiene

- Never commit machine-local artifacts (`.kotlin/errors/*`, `.idea/`, build
  output, `local.properties`, keystores, APKs, `*.zip`).
- Documentation must match the code. Fix wrong version or schema claims in the
  same phase you touched the reason they became wrong.
