# Audio System

The audio system is a thin wrapper around the `audioplayers` Flutter package, located in `services/audio.cljd`. Controllers call it directly — there is no effect registration layer.

## Implementation

**`src/services/audio.cljd`**:

```clojure
(defn play! [asset-path]
  (doto (audio/AudioPlayer)
    (.play (audio/AssetSource asset-path))))
```

This creates a new `AudioPlayer` instance and plays the given asset path immediately.

## Usage

Call `services.audio/play!` directly from a controller function:

```clojure
(ns features.lessons.controller
  (:require [services.audio :as audio]))

(defn advance-word! []
  ;; ... state update logic ...
  (audio/play! (:audio-asset card)))
```

> [!NOTE]
> Audio is currently triggered at the end of the word-building phase when all words in a sentence have been typed, before the quiz view is shown.

## Audio Assets

Audio files are `.mp3` files placed in `assets/audio/`. They are referenced in `assets/deck.json` via the `audio-asset` field.

See the [Lesson Creation Guide](lesson_creation.md) for details on adding new audio files.
