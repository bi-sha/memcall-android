# Restoration layer: deliverables 1 and 2

Scope: a UX layer for classic learning modes (type-in, multiple choice, audio, mems, speed review) on top of an Anki-format collection. Scheduling uses Anki's built-in scheduler (FSRS where enabled). No custom SRS. No bundled course content.

---

## 1. Note-type schema

One note type, `MemItem`, holds every item. Each note yields three cards so recognition and recall are tracked separately.

| # | Field | Type | Required | Purpose |
|---|---|---|---|---|
| 1 | `Term` | text | yes | The word or phrase being learned (target language) |
| 2 | `Translation` | text | yes | Meaning in the user's language |
| 3 | `Audio` | media (`[sound:...]`) | no | Local audio file, played on front/back |
| 4 | `Mem` | text (HTML allowed) | no | Mnemonic shown after a miss or on demand |
| 5 | `Example` | text (HTML allowed) | no | Example sentence, shown on the back |
| 6 | `PartOfSpeech` | text | no | noun, verb, phrase, etc. Display only |
| 7 | `Distractors` | text, `\|`-separated | no | Wrong answers for multiple choice. Filled at import, see §4 |
| 8 | `Course` | text | yes | Course name. Also written as a tag (see §4) |
| 9 | `Level` | text (number) | yes | Level within the course, e.g. `3`. Also a tag |
| 10 | `Accepted` | text, `\|`-separated | no | Extra accepted answers (synonyms, alternate spellings) |

Card templates:

| Ord | Card name | Front shows | Answer | Modes covered |
|---|---|---|---|---|
| 0 | `Recognize` | `Term` | `Translation` | Multiple choice (front shows options) |
| 1 | `Recall` | `Translation` | `Term` | Type-in |
| 2 | `Hear` | `Audio` only | `Term` + `Translation` | Audio-only |

Speed review is not a separate card. It reuses card 0 and 1 under a speed deck (see §4).

**Notes**
- `Mem` and `Example` are optional so a minimal import (Term + Translation) works.
- `Distractors` and `Accepted` stay as delimited text, not JSON, so the field renders and edits cleanly in Anki.

---

## 2. Card templates

Templates are plain HTML/CSS/JS. Each card type has a front and a back.

### Shared CSS (`styling`, used by all three card types)

```css
:root { --bg:#000; --fg:#EDEDED; --muted:#B8B8B8; --accent:#6EE7B7; --bad:#FF8A80; --panel:#141414; }
.card { background:var(--bg); color:var(--fg); font-family:system-ui,sans-serif;
        font-size:22px; text-align:center; padding:24px 16px; }
.term { font-size:32px; font-weight:600; margin:12px 0; }
.muted { color:var(--muted); font-size:16px; }
.input { width:100%; max-width:420px; padding:14px; font-size:20px; background:var(--panel);
         color:var(--fg); border:2px solid #333; border-radius:12px; box-sizing:border-box; }
.input:focus { outline:none; border-color:var(--accent); }
.opt { display:block; width:100%; max-width:420px; margin:10px auto; padding:14px;
       background:var(--panel); color:var(--fg); border:2px solid #333; border-radius:12px;
       font-size:20px; }
.opt.correct { border-color:var(--accent); }
.opt.wrong   { border-color:var(--bad); }
.mem { margin-top:20px; padding:14px; background:var(--panel); border-radius:12px; color:var(--muted); }
.hidden { display:none; }
.feedback { margin-top:12px; font-size:18px; }
.ok  { color:var(--accent); }
.bad { color:var(--bad); }
```

### 2a. Type-in mode (`Recall` card)

**Front**

```html
<div class="card">
  <div class="muted">{{PartOfSpeech}}</div>
  <div class="term">{{Translation}}</div>
  <input id="typed" class="input" autocomplete="off" autocapitalize="off" spellcheck="false"
         placeholder="Type the term">
  <div class="mem hidden" id="mem-front">{{Mem}}</div>
  <button class="opt" onclick="memReveal()">Show mem</button>
</div>
<script>
  const input = document.getElementById('typed');
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter') {
      // Store the attempt for the back side to read (AnkiDroid keeps localStorage per card).
      try { localStorage.setItem('memrise.typed', input.value); } catch (_) {}
      document.querySelector('.card').dispatchEvent(new CustomEvent('show-answer'));
    }
  });
  function memReveal() { document.getElementById('mem-front').classList.remove('hidden'); }
  input.focus();
</script>
```

**Back**

```html
<div class="card">
  <div class="muted">{{PartOfSpeech}}</div>
  <div class="term">{{Term}}</div>
  <div id="verdict" class="feedback"></div>
  <div class="feedback muted" id="you-wrote"></div>
  <div class="muted">{{Translation}}</div>
  <div class="muted">{{Example}}</div>
  <div class="mem hidden" id="mem-back">{{Mem}}</div>
  <button class="opt" onclick="document.getElementById('mem-back').classList.remove('hidden')">Show mem</button>
</div>
<script>
  // Normalization shared with the front: lowercase, strip accents and punctuation spacing,
  // trim, collapse whitespace.
  function norm(s) {
    return (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '')
      .toLowerCase().replace(/\s+/g, ' ').trim();
  }
  // Levenshtein distance (two-row DP).
  function lev(a, b) {
    const m = a.length, n = b.length;
    let prev = Array.from({length: n + 1}, (_, j) => j);
    for (let i = 1; i <= m; i++) {
      const cur = [i];
      for (let j = 1; j <= n; j++) {
        cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      }
      prev = cur;
    }
    return prev[n];
  }
  const TYPO_LIMIT = 2;
  const accepted = [ '{{Term}}', ...('{{Accepted}}'.split('|').filter(Boolean)) ];
  let typed = '';
  try { typed = localStorage.getItem('memrise.typed') || ''; } catch (_) {}
  const t = norm(typed);
  const hit = accepted.some(a => {
    const target = norm(a);
    if (t === target) return true;
    return target.length > 0 && lev(t, target) <= (target.length >= 5 ? TYPO_LIMIT : 1);
  });
  const verdict = document.getElementById('verdict');
  verdict.textContent = hit ? 'Correct' : 'Not quite';
  verdict.className = 'feedback ' + (hit ? 'ok' : 'bad');
  document.getElementById('you-wrote').textContent = typed ? 'You wrote: ' + typed : 'No answer entered';
</script>
```

Notes:
- The typo limit is 2 for terms of 5+ characters and 1 for shorter ones, so short words are not accepted by accident.
- The back does not decide the rating. Again/Good is a separate button row in the reviewer; this template only shows feedback.
- The front only stores the typed value. This relies on per-card `localStorage` in AnkiDroid's WebView. Verify on device before relying on it.

### 2b. Multiple-choice mode (`Recognize` card)

**Front**

```html
<div class="card">
  <div class="term">{{Term}}</div>
  <div id="opts"></div>
</div>
<script>
  const correct = '{{Translation}}';
  const distractors = '{{Distractors}}'.split('|').filter(Boolean);
  const pool = [correct, ...distractors.slice(0, 3)];
  // Deterministic shuffle keyed by the card so the order doesn't change on re-render.
  let seed = 0; for (const c of correct) seed = (seed * 31 + c.charCodeAt(0)) >>> 0;
  function rnd() { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; }
  pool.sort(() => rnd() - 0.5);
  const box = document.getElementById('opts');
  pool.forEach(text => {
    const b = document.createElement('button');
    b.className = 'opt'; b.textContent = text;
    b.onclick = () => {
      b.classList.add(text === correct ? 'correct' : 'wrong');
      document.querySelector('.card').dispatchEvent(new CustomEvent('show-answer'));
    };
    box.appendChild(b);
  });
</script>
```

**Back**

```html
<div class="card">
  <div class="term">{{Term}}</div>
  <div class="muted">{{Translation}}</div>
  <div class="muted">{{Example}}</div>
  <div class="mem hidden" id="mem-back">{{Mem}}</div>
  <button class="opt" onclick="document.getElementById('mem-back').classList.remove('hidden')">Show mem</button>
</div>
```

Notes:
- Options come from `Distractors` (filled at import). The template cannot query other notes, so the importer must pick distractors from the same course and level.
- Pool size is 4 at most: the correct answer plus up to three distractors. Fewer are fine.
- Feedback colors are set on the button tapped. The back shows the correct answer.

### 2c. Audio-only mode (`Hear` card)

**Front**

```html
<div class="card">
  <div class="muted">Listen, then type the term</div>
  {{Audio}}
  <input id="typed" class="input" autocomplete="off" autocapitalize="off" spellcheck="false">
  <div class="mem hidden" id="mem-front">{{Mem}}</div>
  <button class="opt" onclick="document.getElementById('mem-front').classList.remove('hidden')">Show mem</button>
</div>
<script>
  // Auto-play once on open. AnkiDroid's audio tag plays when the card is shown.
  const input = document.getElementById('typed');
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter') {
      try { localStorage.setItem('memrise.typed', input.value); } catch (_) {}
      document.querySelector('.card').dispatchEvent(new CustomEvent('show-answer'));
    }
  });
  input.focus();
</script>
```

**Back**

```html
<div class="card">
  {{Audio}}
  <div class="term">{{Term}}</div>
  <div class="muted">{{Translation}}</div>
  <div class="muted">{{Example}}</div>
</div>
```

Notes:
- `Audio` may be empty on some notes. In that case the front falls back to type-in on `Translation`. The importer should flag these.
- Audio-only uses the same normalization and typo rule as type-in. Reuse the `norm` and `lev` functions from §2a.

### 2d. Mem reveal (shared pattern)

Mems appear in three places: type-in front, multiple-choice back, audio front. The rule is:

1. Hidden by default.
2. Shown on demand via "Show mem".
3. Shown automatically on the back of a card the user got wrong. Set this with a flag in `localStorage` (`memrise.missed.<noteId>`) when the reviewer records Again.

Implementation note: the reviewer, not the template, sets the flag. Add `memrise.missed.<id>` on Again, and the back checks it to show `#mem-back` without a tap.

### 2e. Speed review mode (`Recognize` and `Recall` under a speed deck)

Speed mode reuses card 0 and card 1 in a filtered deck with a fixed time budget per card.

**Front (add to either template)**

```html
<div id="timer" class="muted"></div>
<script>
  const LIMIT_MS = 4000;
  const start = Date.now();
  const timer = document.getElementById('timer');
  const tick = setInterval(() => {
    const left = Math.max(0, LIMIT_MS - (Date.now() - start));
    timer.textContent = (left / 1000).toFixed(1) + 's';
    if (left === 0) {
      clearInterval(tick);
      document.querySelector('.card').dispatchEvent(new CustomEvent('show-answer'));
    }
  }, 100);
</script>
```

Notes:
- Speed mode auto-reveals when the timer runs out, and the reviewer then shows Again/Good.
- The time limit (`LIMIT_MS`) is a template constant. Change it per deck by moving it into a field if needed.

---

## 3. Notes on the Again/Good buttons

Keep the grade binary. Map the reviewer's Again and Good buttons to FSRS ratings: Again → `Again`, Good → `Good`. Hard and Easy are hidden in every mode. Anki's FSRS keeps difficulty and stability internally, so the UI doesn't need to show them.

---

## 4. Carry-over to deliverables 3 to 6

These deliverables depend on the decisions above:

- **Course and level tags** (deliverable 4): write `Course` and `Level` both as fields and as tags, for example `course::spanish-basics` and `level::3`. Learn/review filters use the tags.
- **Import JSON** (deliverable 5): must populate `Distractors` (3 wrong answers from the same course and level), `Accepted`, and `Audio` as local file paths.
- **Mode selection** (deliverable 3): Learn = new cards in a learning deck. Review = due cards in the review deck. Speed = a filtered deck containing card 0 and card 1 with the timer template.

---

## Open items to verify on a device

1. Per-card `localStorage` persists between the front and back in AnkiDroid's WebView.
2. `{{Audio}}` auto-plays on the front in AnkiDroid. If not, add an explicit play control.
3. Whether AnkiDroid enables FSRS in the scheduler version you target. If not, Anki's default scheduler is the fallback.
