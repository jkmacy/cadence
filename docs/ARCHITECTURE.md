# Cadence architecture, explained

**Core idea:** A small scheduling application can be data and functions rather than a framework.

## `src/cadence/model.cljs` — testable logic

An activity is a map: `{:title "Study" :kind "flexible" :repeat "weekly" :days [1 3 4]}`.

- `clean-item` checks incoming activity data and prevents invalid dates and times.
- `occurrences` asks which activities belong to a particular day and sorts them.
- `overlaps?` compares two time intervals.
- `status-change` changes a specific date's completion status without modifying a repeating activity.
- `day-summary` counts activities for the day.

This file never touches the browser; it can be tested from Node after compiling Squint.

## `src/cadence/app.cljs` — browser boundary

The app keeps temporary state in an `atom`. It renders HTML elements and responds to input events. Whenever the schedule changes, it saves the new data into that browser's `localStorage`. All text entered by the user is inserted as text content, not raw HTML.

Backups use a JSON representation that can be explicitly transferred between devices. For encrypted exports, browser Web Crypto derives an AES-GCM key from a passphrase via PBKDF2. The passphrase itself is never saved by Cadence.

## Why Squint and not Common Lisp?

GitHub Pages serves static assets and does not run a backend. Browsers execute JavaScript. Squint lets us maintain application logic in a ClojureScript-style Lisp, compiling it to the browser's required JavaScript. Its small footprint is suitable for a small PWA. Your independent Common Lisp learning can remain separate from this delivery constraint.

Common Lisp-to-WASM/JavaScript implementations may be explored later, but would add build and maintenance complexity to an otherwise simple personal tool.

## Privacy model

**Public:** static application code, source, icons, styles, build files.

**Private on each device:** activities, notes, completion, bed/wake settings.

No automatic synchronization exists. This is deliberate: it avoids handing private schedule data to GitHub Pages or a hosted database. Browser storage is readable to code served from the same origin and to parties with access to the unlocked browser profile. Review app updates before deploying them.

## Extending Cadence

1. Add a small pure function to `model.cljs` for the new domain behavior.
2. Add an assertion in `test/cadence/model_test.cljs`.
3. Run `npm test`.
4. Add UI behavior in `app.cljs` and test in a browser.
5. Run `npm run build`, then commit source changes.

The GitHub Actions workflow compiles and publishes the app automatically. It never needs your local data.
