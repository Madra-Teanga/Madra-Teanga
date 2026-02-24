# Madra Teanga

**Madra Teanga** is the open-source successor to [sionnach.app](https://sionnach.app).
Visit the official site: [madrateanga.com](https://madrateanga.com/)

## Architecture

This project uses a **Feature-Slice** architecture built with ClojureDart and Flutter. State is managed with plain Clojure atoms and controller functions, with dedicated layers for routing and theming.

See [Architecture Overview](documentation/architecture.md) for full details.

## Getting Started

See [Setup Guide](documentation/setup.md) for detailed setup instructions.

### Quick Start
1.  Install [Clojure](https://clojure.org/guides/install_clojure) and [Flutter](https://docs.flutter.dev/get-started/install).
2.  `flutter pub get`
3.  `clj -M:cljd flutter run`

## Lesson Creation

You can modify the lessons by editing `assets/deck.json` and adding audio files to `assets/audio/`.
See the [Lesson Creation Guide](documentation/lesson_creation.md) for details.

## Documentation
- [Architecture Overview](documentation/architecture.md)
- [Audio System](documentation/audio_system.md)
- [Lesson Creation Guide](documentation/lesson_creation.md)
- [Setup Guide](documentation/setup.md)
