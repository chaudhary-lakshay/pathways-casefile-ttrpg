# Pathways — web demo

A browser version of the Pathways Android app: the same characters, rules and
screens, running entirely in the page. On a desktop the app sits inside an
Android phone frame; on a phone it fills the screen; on a tablet it keeps the
app's 600 px column.

**This is an unofficial fan project.** It is not affiliated with or endorsed by
Steve Jackson Games, Cuttlefish That Loves Diving, or their publishers.

## Credits

| | |
|---|---|
| Game system | GURPS 4th Edition, by Steve Jackson Games. GURPS is a trademark of Steve Jackson Games. |
| Source material | *Lord of the Mysteries*, by Cuttlefish That Loves Diving |
| Rulebook (*Powered by GURPS: Pathways — Lord of the Mysteries*) | Earvin Salonoy, with the scribes of Mr.Worm's LOM TTRPG Discord Server |
| App | chaudhary-lakshay |
| Fonts | Fraunces and IBM Plex Sans / Mono, SIL Open Font License 1.1 |
| Icons | Material Icons, by Google, Apache License 2.0 |

Personal, non-commercial use only, as the rulebook asks.

## What it does not do

No backend, no account, no analytics, no external requests. Progress is kept in
the browser's `localStorage`; the gallery's saves panel exports it to a file
and imports it back. A save another tab changed, or one this browser cannot
read, is never overwritten: the page says so and keeps it aside.

## How it is built

React 19, TypeScript and Vite. The rules are the Android app's, ported module
by module, and the data is the app's own:

| Web | Comes from |
|---|---|
| `src/rules/` | `core/rules` (Kotlin), one TypeScript module per Kotlin object |
| `src/app/session/` | `app/` session code: `BeyonderSession`, `SessionStore`, `EditedCharacter` |
| `src/app/*.ts` | the pure parts of the screens (`MainActivity.kt`, `SheetDialogs.kt`, ...) |
| `src/ui/` | the Compose screens and dialogs, as React components |
| rule content | `core/rules/src/main/resources/content/*.json`, imported in place (`@content`) |
| character sheets | `fixtures/*.json` (`@fixtures`) |
| colours | generated from `app/.../ThemeSchemes.kt` at build time |
| fonts, art, icon | `app/src/main/res` (`@res`); icons from `@material-design-icons/svg` |

Nothing is copied, so the web build cannot drift from the app's data.

### Parity with Android

`parity/` is a small Gradle program that runs the Kotlin engine over fixed
inputs and writes the expected outputs to `src/test/vectors/`. The vitest suite
runs the TypeScript port over the same inputs and compares.

```bash
npm ci
npm test            # vitest: parity vectors and unit tests
npm run parity      # regenerate the vectors from Kotlin (JDK 17; about a minute)
npm run e2e         # Playwright, against the production build
npm run dev         # local server
npm run build       # dist/, for the base /lotm-ttrpg/ (PAGES_BASE overrides)
```

The e2e suite checks the owner's list: the layout at 390 px, the frame on a
desktop, a change that survives a reload, a double tap on Roll that throws and
charges once, and no request to any other origin.

## Deploying

`.github/workflows/pages.yml` builds and publishes to GitHub Pages. It runs only
when started by hand (`workflow_dispatch`), and Pages must be enabled for the
repository first.
