# Recall

An offline flashcard app for Android. No ads, no analytics, and no INTERNET permission.

## Status

Pre-release. Not ready for a v1 tag. What's real today:

- **App UI:** create decks and cards, study due cards, grade Again or Good. Data is stored with Room. Scheduling uses SM-2 (`scheduler/Sm2.kt`).
- **Tested:** 51 JVM unit tests pass. 4 instrumented database tests pass on a Pixel 4 emulator. The create, add, study and grade flow was run end to end on the emulator.

What is built but **not wired into the app UI**:

- **FSRS-6 scheduler** (`fsrs/`): ported from fsrs-rs and checked against py-fsrs 6.3.2 reference outputs. Covered by unit tests.
- **Collection layer** (`collection/`): reads an existing `collection.anki2` and writes grades in one transaction. Covered by instrumented tests against a genanki-built collection.
- **Session and rendering** (`session/`, `render/`, `assets/recall_bridge.js`): queue handling, speed timer, mem reveal and card templates. No screens use them yet.
- **Level unlocks** (`session/LevelUnlock.kt`): implemented, not called from the UI.

Known gaps before v1:

1. The UI still uses SM-2 and Room. The FSRS engine and collection layer need to replace them, or the README should say SM-2 is the v1 scheduler.
2. `.apkg` media is not renamed back to filenames, so audio won't play from imported collections.
3. The FSRS formulas match fsrs-rs as ported, but no run has been checked against fsrs-rs itself or a real Anki collection.
4. The Anki add-on in `anki/recall_enhancement/` has not been run inside Anki.
5. No license file yet.

## Repository layout

| Path | What it is |
|---|---|
| `app/src/main/java/app/recall/` | Android app: UI, Room, SM-2, FSRS, collection layer, session, rendering |
| `app/src/test/` | JVM unit tests |
| `app/src/androidTest/` | Instrumented tests (run on a device or emulator) |
| `app/src/main/assets/recall_bridge.js` | JS bridge loaded by card pages |
| `anki/build_note_type.py` | Builds a genanki `.apkg` from course JSON (format in `docs/`) |
| `anki/recall_enhancement/` | Desktop Anki add-on (untested in Anki) |
| `anki/sample_course.json` | Placeholder course for the builder |
| `docs/` | Design notes for the restoration layer |

## Build and test

Requirements: JDK 17, Android SDK 34.

```
./gradlew test                    # JVM unit tests
./gradlew assembleDebug           # debug APK
./gradlew connectedDebugAndroidTest   # needs a device or emulator
```

On Windows, use `gradlew.bat`. `local.properties` (your SDK path) is created by Android Studio and is not committed.

Builder for the Anki package:

```
pip install genanki
python anki/build_note_type.py anki/sample_course.json sample.apkg
```
