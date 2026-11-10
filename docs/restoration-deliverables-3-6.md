# Restoration layer: deliverables 3 to 6

Builds on `restoration-deliverables-1-2.md`. Scheduling is delegated to the Anki engine (FSRS where enabled). The app chooses which cards to show and records Again or Good.

---

## 3. Review state machine

### Concepts

- **New**: a card with no review history. Shown in Learn.
- **Learning**: a card the engine is still stepping through short intervals. Shown in Learn when due.
- **Review**: a graduated card the engine schedules with FSRS. Shown in Review when due.

Learn and Review are views over the same collection, not separate stores. The engine decides each card's state after a grade.

### Session flow

```
SESSION(mode, scope):
    queue  = BUILD_QUEUE(mode, scope)      # see below
    stats  = {seen: 0, again: 0, good: 0}
    while queue not empty AND not END_CONDITION(stats):
        card = queue.next()
        SHOW_FRONT(card, mode.template)
        if mode.needs_input:
            answer = WAIT_FOR_INPUT(card)  # typed or chosen
            shown_correct = CHECK(answer, card)  # §2a/§2b normalization
        else:
            WAIT_FOR_REVEAL(card)
        SHOW_BACK(card)                    # includes feedback and mem if missed
        grade = WAIT_FOR_GRADE()           # AGAIN or GOOD only
        ENGINE.answer(card, grade)         # Anki scheduler + FSRS
        if grade == AGAIN:
            SET_FLAG(card.note, "missed")  # triggers mem on back next time
            stats.again += 1
            if mode == LEARN: queue.requeue(card, position = +3)  # see a miss again soon
        else:
            stats.good += 1
        stats.seen += 1
    SHOW_SUMMARY(stats)
```

### Queue construction

```
BUILD_QUEUE(mode, scope):
    match mode:
        LEARN:   query = "{scope} is:new OR is:learn"   # new + learning cards
        REVIEW:  query = "{scope} is:due is:review"     # graduated, due only
        SPEED:   query = "{scope} is:due is:review"     # same as review, speed template
        AUDIO:   query = "{scope} is:due OR is:new" + " card:Hear"
    cards = ENGINE.search(query, order = "due, random")
    limit = mode.session_limit  # e.g. 20 new + unlimited review
    return take(cards, limit)
```

Mode choice:

| Mode | Queue | Template | Grading |
|---|---|---|---|
| Learn | new + learning | Recognize (MC) or Recall (type-in) | Again/Good |
| Review | due, graduated | Recall (type-in) | Again/Good |
| Speed | due, graduated | Recognize, timer on | Again/Good |
| Audio | new + due | Hear | Again/Good |

### Grading

- Two buttons only: **Again** and **Good**.
- Map to FSRS: `Again → Again`, `Good → Good`. Hard and Easy are never offered.
- Speed mode: if the timer expires before a tap, it sends **Again** automatically.

### Session end conditions

Stop when any is true:
1. Queue is empty.
2. `stats.seen >= session_limit` (default 20 new, 50 review; configurable).
3. User leaves the screen (session is saved, unfinished cards stay due).
4. Speed mode: 60 seconds of total session time.

Interrupted sessions lose nothing. Each grade is written when made, so restarting the app resumes at the next due card.

### Requeue rule

A missed Learn card reappears after 3 other cards, so it is seen again in the same session. Review and Speed cards do not requeue; the engine schedules them for later.

---

## 4. Course and level grouping

Anki has no native course → level hierarchy for this, so we use tags, with deck names as the human-readable view.

### Tagging scheme

| Tag | Example | Meaning |
|---|---|---|
| `course::<slug>` | `course::spanish-basics` | Which course the item belongs to |
| `level::<n>` | `level::3` | Position within the course (1 = first) |
| `pos::<kind>` | `pos::verb` | Part of speech (optional, for filtering) |

- Slugs are lowercase, ASCII, hyphen-separated. The course's display name is kept in the course metadata (see §5), not in the tag.
- Levels are integers starting at 1, written without padding (`level::3`, not `level::03`). Numeric order comes from the integer value in the import, not from string sorting.

### Filtering by course and level

Search strings the queue builder uses:

```
tag:course::spanish-basics tag:level::1          # one level
(tag:level::1 OR tag:level::2 OR tag:level::3)   # a level range, expanded to OR
tag:course::spanish-basics is:due                # whole course, due only
```

Implementation rule: the app expands level ranges into an explicit OR list before calling the search, because Anki's search syntax does not support numeric ranges in tags.

### Level unlocking (the "course → level" feel)

Old Memrise required finishing one level before the next. Replicate this in the app, not in the engine:

```
LEVEL_UNLOCKED(course, n):
    return n == 1 OR ALL_GRADUATED(course, level = n - 1)

ALL_GRADUATED(course, level):
    # every card in the level has a review history and is not New
    return count(tag:course::C tag:level::L is:new) == 0
```

- Unlock is checked when the user opens a level. The engine does not block review of already unlocked levels.
- A level stays unlocked once reached, even if later cards lapse.

### Deck view (optional)

Create one deck per course for the list view and keep tags as the source of truth:

```
Spanish basics
  Level 1
  Level 2
```

Decks are display only. Moving a card between decks does not change its course or level; the tag does.

---

## 5. Import format

### Input JSON schema (JSON Schema draft 2020-12, abbreviated)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "RestorationCourseImport",
  "type": "object",
  "required": ["format_version", "courses"],
  "properties": {
    "format_version": { "const": 1 },
    "courses": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["slug", "name", "levels"],
        "properties": {
          "slug":  { "type": "string", "pattern": "^[a-z0-9-]+$" },
          "name":  { "type": "string", "minLength": 1 },
          "levels": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["level", "items"],
              "properties": {
                "level": { "type": "integer", "minimum": 1 },
                "items": {
                  "type": "array",
                  "items": { "$ref": "#/$defs/item" }
                }
              }
            }
          }
        }
      }
    }
  },
  "$defs": {
    "item": {
      "type": "object",
      "required": ["term", "translation"],
      "properties": {
        "term":        { "type": "string", "minLength": 1 },
        "translation": { "type": "string", "minLength": 1 },
        "audio":       { "type": "string" },
        "mem":         { "type": "string" },
        "example":     { "type": "string" },
        "part_of_speech": { "type": "string" },
        "distractors": { "type": "array", "items": { "type": "string" }, "maxItems": 3 },
        "accepted":    { "type": "array", "items": { "type": "string" } }
      },
      "additionalProperties": false
    }
  }
}
```

### Example document (placeholder content only)

```json
{
  "format_version": 1,
  "courses": [
    {
      "slug": "example-course",
      "name": "Example Course",
      "levels": [
        {
          "level": 1,
          "items": [
            {
              "term": "alpha",
              "translation": "first letter",
              "audio": "media/alpha.mp3",
              "mem": "Alpha sounds like 'alpha male', the first one.",
              "example": "Alpha is the first letter of the Greek alphabet.",
              "part_of_speech": "noun",
              "distractors": ["beta", "gamma", "delta"],
              "accepted": ["first letter"]
            }
          ]
        }
      ]
    }
  ]
}
```

### Field mapping to the note type (deliverable 1)

| JSON field | Note field | Transform |
|---|---|---|
| `term` | `Term` | trim |
| `translation` | `Translation` | trim |
| `audio` | `Audio` | copy file into `collection.media`, write `[sound:<name>]` |
| `mem` | `Mem` | keep HTML as-is, escape `<script>` |
| `example` | `Example` | keep HTML as-is, escape `<script>` |
| `part_of_speech` | `PartOfSpeech` | trim |
| `distractors` | `Distractors` | join with `\|` |
| `accepted` | `Accepted` | join with `\|` |
| course `slug` | `Course` field and tag `course::<slug>` | |
| level `level` | `Level` field and tag `level::<n>` | |

### Validation rules (run before any write)

1. Schema must validate.
2. `term` must be unique within a course. Duplicates are reported, not skipped silently.
3. `distractors` must not contain the `translation`. Drop any that do and warn.
4. Multiple choice needs at least one distractor. If fewer than 1, the item skips Recognize and gets a warning.
5. `audio` file must exist in the import folder. Missing files are a warning, and the Hear card falls back to type-in on `Translation`.
6. Level numbers within a course must be consecutive from 1. Gaps are an error.

### Converting from other exports

This spec does not assume an old Memrise export layout. For any other source, write a small converter that outputs this JSON. Keep the converter outside the app so the app only ever reads format version 1.

---

## 6. Mobile review UI wireframe (text)

Portrait, single column. Tap targets at least 48 dp. Works one-handed.

### Review screen (type-in, Recall card)

```
+--------------------------------------+
| <  Spanish basics · Level 2   3/20   |   top bar: back, scope, progress
|--------------------------------------|
|                                      |
|           verb  ·  to run            |   part of speech, translation
|                                      |
|        [ type the term        ]      |   input, auto-focus, keyboard open
|                                      |
|      Show mem                        |   text button, hidden mem below
|--------------------------------------|
|  [ Check ]                           |   primary button, Enter also works
+--------------------------------------+
```

After **Check** (back of card):

```
+--------------------------------------+
| <  Spanish basics · Level 2   3/20   |
|--------------------------------------|
|          correcorrer                 |   term, large
|          Not quite  ·  you wrote:     |   feedback, color-coded
|          corer                       |
|          "I run every morning."      |   example
|      ▸ Show mem                      |   expands in place
|--------------------------------------|
|  [   Again   ]      [    Good    ]   |   two grade buttons, full width halves
+--------------------------------------+
```

### Review screen (multiple choice, Recognize card)

```
+--------------------------------------+
|          alpha                       |   term
|                                      |
|   [ first letter               ]     |   option, 48 dp tall
|   [ second letter              ]     |
|   [ third letter               ]     |
|   [ fourth letter              ]     |
+--------------------------------------+
```

Tap an option: it turns green or red, and the back appears after 600 ms. Grade buttons appear only after the back is shown.

### Audio mode (Hear card)

```
+--------------------------------------+
|        ( ▶  play )                   |   large play button, autoplay on open
|                                      |
|        [ type what you heard  ]      |
|--------------------------------------|
|  [ Check ]                           |
+--------------------------------------+
```

### Gestures

| Gesture | Action | Notes |
|---|---|---|
| Tap **Check** / press Enter | Reveal back | Primary path |
| Swipe left on back | Again | Only after back is shown |
| Swipe right on back | Good | Only after back is shown |
| Long-press term | Replay audio | Hear card only |
| Swipe down | Pause session | Saves position, no grade |

Swipe grades are a shortcut. The two buttons stay visible for accessibility, and swipes never fire on the front, so a stray swipe while typing does nothing.

### States

- **Empty queue**: "All caught up. Next due: tomorrow 09:00." with a button to open the next level.
- **Level locked**: a dimmed card with "Finish Level 1 to unlock". Tap shows the required count.
- **Session end**: summary card with seen/Again/Good counts and a Continue button for another batch.

---

## Open items

1. Confirm `is:learn` and `is:review` search terms behave as assumed in the AnkiDroid version targeted. If not, use `is:due` and `-is:new` as the fallback.
2. The level unlock check reads card state on every level open. Cache per course if it is slow on large collections.
3. Gesture thresholds (swipe distance, speed) need tuning on device. The values here are starting points.
