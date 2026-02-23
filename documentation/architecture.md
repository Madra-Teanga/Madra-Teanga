# Madra Teanga – Architecture

This project uses a **Feature-Slice** architecture with plain Clojure atoms and controller functions.

## Core Principles

State is managed with **plain Clojure atoms**. Controller namespaces hold functions that mutate those atoms directly, replacing the old event/subscription/effect dispatch loop.

UI components observe atoms using `cljd.flutter`'s `:watch` binding, which re-renders the widget whenever the atom changes.

## Directory Structure

```
src/
├── main.cljd                   # App entry point
├── routing/
│   ├── state.cljd              # Routing atom (current-view, history, params)
│   ├── routes.cljd             # Route-map: keyword → screen function
│   └── root.cljd               # Root MaterialApp widget
├── theme/
│   ├── state.cljd              # Theme atom (:mode → :light | :dark)
│   ├── tokens.cljd             # Design tokens (brand colours)
│   └── controller.cljd         # ThemeData builder + persist to storage
├── features/
│   └── lessons/
│       ├── state.cljd          # Lessons atom + initial-state
│       ├── controller.cljd     # All lesson mutations (load, advance, quiz…)
│       └── ui/
│           ├── screens/
│           │   └── lessons_screen.cljd
│           └── widgets/
│               └── components.cljd
└── services/
    ├── audio.cljd              # Thin wrapper around audioplayers
    └── local_storage.cljd      # Thin wrapper around shared_preferences
```

## App Startup (`main.cljd`)

```clojure
(defn main []
  (m/WidgetsFlutterBinding.ensureInitialized)
  (theme/load-mode!)        ; load persisted theme, start watcher
  (lessons-ctrl/init!)      ; reset lessons atom to initial-state
  (lessons-ctrl/load-deck!) ; load deck.json async
  (m/runApp (router/view))) ; launch app
```

## Routing

Routing is a plain atom holding `:current-view`, `:history`, and `:params`.

**`routing/state.cljd`** – the atom:
```clojure
(def state (atom {:current-view :route/home
                  :history []
                  :params {}}))
```

**`routing/routes.cljd`** – maps route keywords to screen functions:
```clojure
(def route-map
  {:route/home lessons/lessons-screen})
```

**`routing/root.cljd`** – the root widget watches both routing and theme atoms:
```clojure
(f/widget
 :watch [router-state routing.state/state
         theme-state  theme.state/state]
 (m/MaterialApp
  .theme (theme-ctrl/mode-setting (:mode theme-state))
  .home  ((get routes/route-map (:current-view router-state)))))
```

To navigate, swap the routing atom:
```clojure
(swap! routing.state/state assoc :current-view :route/some-screen)
```

## Theme

The theme system supports `:light` and `:dark` modes. The active mode is persisted to local storage automatically via a `add-watch` watcher.

**`theme/tokens.cljd`** – design tokens:
```clojure
(def colors
  {:brand-primary   m.Colors/indigo
   :brand-secondary m.Colors/pinkAccent
   :surface-light   m.Colors/white
   :surface-dark    (m/Color 0xFF111111)})
```

**`theme/controller.cljd`** – key functions:

| Function | Description |
|---|---|
| `(mode-setting mode)` | Builds a `ThemeData` for the given mode |
| `(toggle-mode!)` | Toggles between `:light` and `:dark` |
| `(load-mode!)` | Loads persisted mode from storage on startup |

## Features

Each feature is a self-contained slice with its own `state`, `controller`, and `ui` sub-namespaces.

### `features/lessons`

**`state.cljd`** – the atom shape:

| Key | Type | Description |
|---|---|---|
| `:deck` | vector | All lesson cards loaded from `deck.json` |
| `:sentence-idx` | int | Index of the current sentence |
| `:word-idx` | int | Index of the current word within the sentence |
| `:typed-text` | string | Current value in the hidden text field |
| `:mode` | keyword | `:loading`, `:building`, or `:quiz` |
| `:show-hints` | bool | Whether fada hints are shown |
| `:quiz-wrong-guesses` | set | Options the user has incorrectly chosen |
| `:quiz-solved?` | bool | Whether the current quiz round is solved |

**`controller.cljd`** – key functions:

| Function | Description |
|---|---|
| `(init!)` | Resets atom to `initial-state` |
| `(load-deck!)` | Loads `deck.json` from assets async |
| `(advance-word!)` | Moves to the next word; transitions to `:quiz` at end |
| `(next-sentence!)` | Moves to the next sentence card |
| `(toggle-hint!)` | Toggles fada hints |
| `(update-typed-text! text)` | Syncs the hidden text field value |
| `(quiz-guess! opt)` | Evaluates a multiple-choice answer |

## Services

Thin wrappers around Flutter packages. Controllers call services directly — there is no effect registration layer.

| Namespace | Package | Key function |
|---|---|---|
| `services.audio` | `audioplayers` | `(play! asset-path)` |
| `services.local-storage` | `shared_preferences` | `(get-value key)` / `(set-value! key val)` |
