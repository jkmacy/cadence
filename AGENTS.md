# Cadence — Lisp-first development

- The maintained app source is `src/cadence/{model,app}.cljs` (Squint dialect of ClojureScript).
- Write all business logic in `src/cadence/model.cljs`, separately from browser effects in `src/cadence/app.cljs`.
- Do NOT edit or commit generated JavaScript in `public/js/`.
- Small platform shims (`public/sw.js`) and HTML/CSS are permitted.
- Compile: `npm install && npm run build` (Node 22; Squint 0.14.210).
- Test: `npm test` (Node assertions against compiled Squint model).
- Test failures must exit nonzero. Browser check is required for UI changes.
- Repo remains public; user data never belongs in the repository. Never add personal calendar files, backups, or credentials.
- No telemetry, CDN-loaded runtime, external analytics, or server-side storage. Vendored runtime in built `public/js/squint-cljs/` is served from the same origin.
- No automatic syncing or reading the user's calendar unless added with clear authorization and appropriate security controls.
