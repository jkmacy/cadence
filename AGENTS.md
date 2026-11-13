# Cadence — Common Lisp-first development contract

The owner's long-term objective is **Common Lisp as the primary and
authoritative implementation language**, with small platform adapters only
when necessary. Cadence starts as a time-centered planner and expands
incrementally into a personal computing home.

## Current state (Cadence 2 foundation)

- `cl/core.lisp` contains dependency-free ANSI Common Lisp scheduling
  primitives. Run `sbcl --script cl/tests/core-test.lisp` to check them.
- `src/cadence/model.cljs` and `src/cadence/app.cljs` form the **existing**
  Squint/JavaScript browser implementation. This is temporary and continues
  serving the PWA; the new CL library does not yet drive it.
- For new scheduling logic, design an understandable CL API and tests first.
  Until runtime integration is demonstrated, don't pretend the CL core controls
  browser behavior. Preserve tests of the old bridge.
- Keep platform and UI effects separate from CL business policy. Don't add
  JavaScript/Java/Clojure dependencies where standard CL suffices.
- Do not edit generated code in `public/js/`.

## Build / tests

- CL: `sbcl --noinform --disable-debugger --script cl/tests/core-test.lisp`
- Current PWA: `npm install && npm test && npm run build`
- Browser smoke: see `test/browser-smoke.mjs` and GitHub verification workflow.
- Fail on broken tests or UI smoke checks. Keep migrations reviewable.

## Portability and privacy

- This is a public source repository, not a data store. Never commit personal
  schedules, calendar exports, credentials, backup files, or access tokens.
- Preserve existing `cadence-local-private-v1` browser data and readable
  JSON/encrypted backup compatibility until a verified migration exists.
- No telemetry, remote analytics, third-party runtimes, surprise syncing,
  or unapproved access to connected accounts.
- Favor portable data and reproducible build instructions. The current web
  build still needs a first-run npm installation.
- Cadence should *reduce* the attention required to manage a schedule.
  Avoid dashboards, notifications, or tracking that merely increase work.
- Learning matters: write clear, documented functions so the owner can read,
  test, modify, and recover the system without AI.
