# Cadence 2 architecture and transition to Common Lisp

Cadence is intended to be a **personal computing home centered on time**. Its future
domain model is Common Lisp. It presently has two **separate** implementations:

1. **Common Lisp foundation**, `cl/core.lisp`. This small ANSI Common Lisp
   library has its own executable checks in `cl/tests/core-test.lisp`.
   It covers date/time parsing, recurrence, daily ordering, conflicts, daily
   summaries, and subtracting fixed commitments from a time window.
2. **Existing Squint browser bridge**, `src/cadence/model.cljs` and
   `src/cadence/app.cljs`, deployed as JavaScript through GitHub Pages.
   This still drives Cadence 2's current PWA. It preserves the v1 local
   storage data model and manual JSON/encrypted export and import.

**Important:** The CL library is not yet the authoritative implementation used
by the browser. No synchronization, native app, Google Calendar import,
external account access, or fully offline development environment is claimed.

## Platform-neutral model first

Keep scheduling policy, rules, recurring events, conflict checks, and
coordination logic in Common Lisp. Treat persistence, calendars, notifications,
device APIs, and graphic toolkits as independent adapters.

Future options include a native service or packaged CL process for macOS/Linux,
and ECL embedding for Android. A browser-only host cannot execute ordinary
SBCL bytecode or machine code, so the web experience will need an explicitly
documented boundary, not an assertion that a static HTML page runs CL.

The goal is **one well-tested, authoritative CL model**, with thin platform
integrations. Before moving the PWA to that architecture, establish shared
serialization fixtures and cross-implementation tests. Then migrate one
capability at a time, not all scheduling behavior simultaneously.

## Workstation setup

On macOS: install SBCL through Homebrew (or another package manager).
On Linux: install `sbcl` through your distribution's repository.

From the repository root:

```sh
sbcl --noinform --disable-debugger --script cl/tests/core-test.lisp
```

No Quicklisp, Java, JavaScript, or network access is needed for **these CL tests**.
For the current PWA delivery, Node and its pinned Squint dependency are still
needed:

```sh
npm install
npm test
npm run build
```

The package is still online-dependent on first installation; do not claim
whole-project offline reproducibility yet.

## Your learning starting point

1. Read `cl/core.lisp` and run the tests locally.
2. At the SBCL REPL, load the library and try:
   `(cadence.core:minute-of-day "09:30")`
3. Inspect `daily-entries`. Trace how a weekday and start time are derived.
4. Write a small test first, then change one behavior deliberately.
5. Keep changes small, commit them, and make sure existing browser and CL tests
   continue to pass.

Use AI assistance as an explanatory reviewer when learning. A changed function
should be understandable and maintainable by the owner without AI.

## Browser interface and privacy boundary

The initial Cadence 2 redesign comprises Today, Calendar, Plan, Apps,
and one-line quick capture. These are delivered by a Squint temporary bridge.

The original `cadence-local-private-v1` key and data format remain in place,
so an existing installed copy does **not** require an automatic migration.

All schedule items remain in origin-bound browser `localStorage`. This is not
encrypted at rest and is not synchronized across devices. Back up before
clearing browser data or uninstalling the application. A file import replaces
the destination schedule rather than merging it.

Google Calendar is currently an external link, **not a connected calendar
feed**. No credentials or events are sent to the hosting repository.

## Long-term modules

- Core domain (Common Lisp)
- Portable serialization and migration formats (Common Lisp)
- Local storage adapters (Common Lisp on supported platforms)
- Google Calendar or CalDAV adapters with explicit authorization
- Phone and desktop UI shells
- Optional encrypted cross-device synchronization
- Personal app hub, independent of each app's build system

Do not merge business rules into UI adapters. Do not introduce an obligatory
external service just to add local functionality.
