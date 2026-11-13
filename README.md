# Cadence

**Cadence 2.0 foundation: a personal computing home centered on time.** The long-term domain language is ANSI Common Lisp; the current Pixel/web shell is a transitional Squint PWA. These are not yet a single integrated runtime. See [architecture](docs/ARCHITECTURE.md).

Cadence provides fixed commitments alongside flexible activities and deliberate unstructured time. It is a tool, not a scorecard.

## What is available now

- Mobile-first Today, Calendar, Plan and Apps screens
- One-line capture of an untimed flexible activity for today
- Immediate links to Solar Clock and Daily Ordo
- A dependency-free Common Lisp core with runnable SBCL tests (currently **not used by the browser UI**)
- Still planned: Google Calendar synchronization, native packaging, secure sync, Common Lisp-driven browser experience

- Day view and week view; move between dates
- Editable one-off and weekly recurring activities
- Fixed commitments (work, church, school) and flexible sessions (writing, Lisp, reading, rest)
- Mark completed or skip on a particular date without changing a repeating activity
- Warn about overlapping scheduled activities
- Adjustable sleep and wake targets
- Private local browser storage; works offline after initial installation
- Export/import readable JSON or passphrase-encrypted AES-256-GCM backups
- Copy a plain-text weekly summary for **deliberate** discussion with an AI

There is no account, analytics, automatic synchronization, or backend. The public repository contains **no personal schedule**; when you first open the app, the schedule is empty. Entries are saved only in that browser's `localStorage`.

### Privacy limitations

- **Local storage isn't encryption at rest.** Anyone with access to your unlocked browser profile may be able to read your schedule.
- Pixel and Mac hold separate schedules. To move data, download an encrypted backup and import it on the other device. **Import replaces** the receiving device's data; export a copy first.
- Browser data can be lost by clearing site storage or uninstalling the app. Make backups.
- The public app code runs in your browser and thus can read the browser's local data. Review code changes before updating if privacy matters.
- A weekly summary is copied locally; sending it to an AI is an explicit user decision, not an automated operation.

## Technology

The present browser bridge is written in **Squint ClojureScript** (`src/cadence/*.cljs`), with a separate pure model and browser UI. Squint generates ECMAScript modules to serve on GitHub Pages. `public/index.html`, `public/styles.css`, and the minimal `public/sw.js` are web platform files. The Squint runtime is vendored at build time; the deployed site does not fetch it from a third-party CDN.

Generated JavaScript is a deployment artifact. New domain logic should favor ANSI Common Lisp and a thin, replaceable platform boundary.

## Common Lisp study and maintenance

```sh
sbcl --noinform --disable-debugger --script cl/tests/core-test.lisp
```

The CL tests require only an installed Common Lisp implementation (tested in CI
with SBCL). The PWA build still requires Node.js, npm, and Squint.

To study Cadence without AI, start with `cl/core.lisp`, then
`cl/tests/core-test.lisp`. The existing browser scheduling bridge remains in
`src/cadence/model.cljs`. The migration goal is to retire the duplicate
scheduling policy, not maintain two diverging implementations.

## Development

Requires Node.js 22 and npm.

```sh
npm install
npm test
npm run build
python3 -m http.server 8000 -d public
```

Open `http://localhost:8000/`. On production, HTTPS enables PWA installation and the browser's encryption APIs.

The test runner compiles `src` and `test` in a temporary directory, runs Node assertions against the model, and exits with a failure status when assertions fail.

## Publish to GitHub Pages

1. Create a **public** repository named `cadence` under your GitHub account.
2. Upload the contents of this package at the repository root, including `.github/workflows/pages.yml`. Do not upload any personal schedule exports.
3. In **Settings → Pages → Build and deployment**, choose **GitHub Actions** as the source.
4. The workflow on `main` or `master` installs Squint, tests, builds the app and deploys `public/`.
5. The resulting URL is typically `https://YOUR-USERNAME.github.io/cadence/` once GitHub reports a successful deployment.
6. On Pixel: open that URL in Chrome and choose **Install app**. On Mac: open it in a compatible browser; installation depends on browser support.

No automatic personal data migration is performed. The app begins empty so GitHub never receives your schedule.

## Working with ChatGPT or Codex

The public repository can be inspected or edited as source code. Since your schedule lives locally, the assistant cannot silently retrieve it from GitHub. Use **Copy week summary** for discussions, or explicitly supply an export if desired. Never commit a readable backup to a public repository.

## License

MIT; see `LICENSE`.
