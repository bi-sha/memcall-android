"""Build the old-Memrise restoration note type and an .apkg from a §5 course JSON file.

Usage:
    py -3 build_note_type.py <course.json> <out.apkg> [--media-dir DIR] [--rebuild-distractors]

Requires: pip install genanki

Distractors: each level has a signature (hash of its sorted translations) stored in
<out.apkg>.levels.json. When a level's signature changes, every distractor in that
level is regenerated from the level's own pool. --rebuild-distractors forces that for
all levels. Distractor choice is seeded by the signature, so rebuilding an unchanged
level always gives the same result.

Only the §5 JSON format is read. Other sources need an adapter that writes §5 JSON.
"""
import argparse
import hashlib
import json
import random
from pathlib import Path

import genanki

# Stable IDs. Do not change once decks exist, or Anki treats the model as new.
MODEL_ID = 1907341001
DECK_ID = 1907341002

FIELDS = [
    "term", "definition", "audio", "mem", "example",
    "course", "level", "pos",
    "distractors",  # beyond the spec's field list: MC templates cannot query other notes
]

# ---------------------------------------------------------------------------
# Shared CSS (dark blue, minimal chrome)
# ---------------------------------------------------------------------------

SHARED_CSS = """
:root{--bg:#0b1020;--panel:#151d33;--text:#e6edf8;--muted:#8fa3c4;--accent:#4da3ff;
      --ok:#43d18b;--bad:#ff6b6b;--border:#2a3758}
.card{background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,sans-serif;
      font-size:20px;text-align:center;padding:20px 14px;}
.pos{color:var(--muted);font-size:15px;margin-bottom:6px}
.prompt{font-size:30px;font-weight:600;margin:14px 0;line-height:1.3}
.answer{width:100%;max-width:420px;padding:14px;font-size:20px;background:var(--panel);
        color:var(--text);border:2px solid var(--border);border-radius:12px;box-sizing:border-box}
.answer:focus{outline:none;border-color:var(--accent)}
.check{margin-top:12px;padding:12px 28px;font-size:18px;background:var(--accent);color:#06101f;
       border:none;border-radius:10px}
.opt{display:block;width:100%;max-width:420px;margin:10px auto;padding:14px;background:var(--panel);
     color:var(--text);border:2px solid var(--border);border-radius:12px;font-size:20px}
.opt.right{border-color:var(--ok);color:var(--ok)}
.opt.wrong{border-color:var(--bad);color:var(--bad)}
.feedback{margin-top:14px;font-size:18px}
.ok{color:var(--ok)} .bad{color:var(--bad)}
.muted{color:var(--muted)}
.hint{color:var(--muted);font-size:15px;margin-top:6px}
.example{margin-top:12px;color:var(--muted);font-size:17px}
.example b{color:var(--accent);font-weight:600}
.mem{margin:16px auto 0;max-width:460px;padding:14px;background:var(--panel);border-radius:12px;
     color:var(--muted);font-size:17px}
hr{border:0;border-top:1px solid var(--border);margin:18px 0}
.speed .prompt{font-size:44px;margin-top:40px}
.reveal{font-size:26px;color:var(--ok);margin-top:24px}
"""

# ---------------------------------------------------------------------------
# Shared JS. Plain HTML/JS only; no Anki-specific API.
# ---------------------------------------------------------------------------

SHARED_JS = r"""
function memNorm(s){return (s||'').normalize('NFD').replace(/[̀-ͯ]/g,'')
  .toLowerCase().replace(/\s+/g,' ').trim();}
function memLev(a,b){var m=a.length,n=b.length,p=[],c,i,j;
  for(j=0;j<=n;j++)p[j]=j;
  for(i=1;i<=m;i++){c=[i];for(j=1;j<=n;j++){
    c[j]=Math.min(p[j]+1,c[j-1]+1,p[j-1]+(a[i-1]===b[j-1]?0:1));}p=c;}
  return p[n];}
// Forgiving match. First character must match. Threshold: 1 for targets under 5 characters, 2 otherwise.
function memMatch(typed,target){
  var t=memNorm(typed),g=memNorm(target);
  if(!t.length||!g.length)return false;
  if(t.charAt(0)!==g.charAt(0))return false;
  var thr=g.length<5?1:2;
  return t===g||memLev(t,g)<=thr;}
function memShuffle(a,seed){var s=seed>>>0,i,j,t;for(i=a.length-1;i>0;i--){
  s=(s*1664525+1013904223)>>>0;j=s%(i+1);t=a[i];a[i]=a[j];a[j]=t;}return a;}
function memStoreGet(k){try{return localStorage.getItem(k);}catch(e){return null;}}
function memStoreSet(k,v){try{localStorage.setItem(k,v);}catch(e){}}
"""


def _mem_js(anchor_id: str) -> str:
    """Show #mem when the host has set localStorage 'recall_mem.<term>' = 'show'."""
    return (
        "(function(){var t=document.getElementById('" + anchor_id + "');if(!t)return;"
        "var k='recall_mem.'+memNorm(t.textContent);"
        "if(memStoreGet(k)==='show'){var m=document.getElementById('mem');if(m)m.hidden=false;}"
        "})();"
    )


# Inline grading block, rendered hidden and shown on a back side that has no stored answer.
def _inline_block(target_id: str) -> str:
    return (
        '<div id="inline" hidden>'
        '<input id="typed2" class="answer" autocomplete="off" autocapitalize="off" spellcheck="false" '
        'placeholder="Type the term">'
        '<br><button class="check" onclick="memInline()">Check</button>'
        '<div id="verdict2" class="feedback"></div></div>'
        "<script>"
        + SHARED_JS
        + "function memInline(){var v=document.getElementById('typed2').value;"
        "memStoreSet('recall_typed',v);"
        "var ok=memMatch(v,document.getElementById('" + target_id + "').textContent);"
        "var o=document.getElementById('verdict2');o.className='feedback '+(ok?'ok':'bad');"
        "o.textContent=ok?'Correct':'Not quite. You wrote: '+(v||'nothing');}"
        "document.getElementById('typed2').addEventListener('keydown',function(e){"
        "if(e.key==='Enter'){e.preventDefault();memInline();}});"
        "</script>"
    )


# ---------------------------------------------------------------------------
# Card templates. Anki syntax: {{field}}, {{FrontSide}}, {{#field}}...{{/field}}.
# ---------------------------------------------------------------------------

TYPEIN_FRONT = """
<div class="card">
  <div class="pos">{{pos}}</div>
  <div class="prompt">{{definition}}</div>
  <input id="typed" class="answer" autocomplete="off" autocapitalize="off"
         spellcheck="false" placeholder="Type the term">
  <br><button class="check" onclick="memCheck()">Check</button>
  <div id="verdict" class="feedback"></div>
  <span id="term-src" hidden>{{term}}</span>
</div>
<script>
""" + SHARED_JS + """
function memCheck(){
  var v=document.getElementById('typed').value;
  memStoreSet('recall_typed',v);
  var ok=memMatch(v,document.getElementById('term-src').textContent);
  var out=document.getElementById('verdict');
  out.className='feedback '+(ok?'ok':'bad');
  out.textContent=ok?'Correct':'Not quite. You wrote: '+(v||'nothing');
}
document.getElementById('typed').addEventListener('keydown',function(e){
  if(e.key==='Enter'){e.preventDefault();memCheck();}});
document.getElementById('typed').focus();
</script>
"""

TYPEIN_BACK = (
    """
{{FrontSide}}
<hr>
<div class="prompt">{{term}}</div>
<div class="muted" id="wrote"></div>
<div class="example">{{example}}</div>
<div class="mem" id="mem" hidden>{{mem}}</div>
<span id="mem-src" hidden>{{term}}</span>
<span id="term-src" hidden>{{term}}</span>
"""
    + _inline_block("term-src")
    + """
<script>
"""
    + SHARED_JS
    + """
(function(){
  var typed=memStoreGet('recall_typed');
  var t=document.getElementById('term-src').textContent;
  var el=document.getElementById('wrote');
  if(typed!==null){
    if(typed){el.textContent=memMatch(typed,t)?'You got it':'You wrote: '+typed;}
    return;
  }
  // No stored answer (flipped without typing, or storage unavailable): grade inline here.
  document.getElementById('inline').hidden=false;
})();
"""
    + _mem_js("mem-src")
    + """
</script>
"""
)

MC_FRONT = """
<div class="card">
  <div class="prompt">{{term}}</div>
  <div id="opts"></div>
  <span id="correct" hidden>{{definition}}</span>
  <span id="dis" hidden>{{distractors}}</span>
</div>
<script>
""" + SHARED_JS + """
(function(){
  var correct=document.getElementById('correct').textContent.trim();
  var dis=document.getElementById('dis').textContent.split('|').map(function(s){return s.trim();})
           .filter(function(s){return s && s!==correct;}).slice(0,3);
  var pool=memShuffle([correct].concat(dis), correct.length*7+dis.length);
  var box=document.getElementById('opts');
  pool.forEach(function(text){
    var b=document.createElement('button');
    b.className='opt'; b.textContent=text;
    b.onclick=function(){
      if(box.dataset.done)return; box.dataset.done='1';
      Array.prototype.forEach.call(box.children,function(c){
        if(c.textContent===correct)c.classList.add('right');});
      if(text!==correct)b.classList.add('wrong');
      memStoreSet('recall_mc',text===correct?'right':'wrong');
    };
    box.appendChild(b);
  });
})();
</script>
"""

MC_BACK = """
{{FrontSide}}
<hr>
<div class="prompt" style="font-size:24px">{{definition}}</div>
<div class="example">{{example}}</div>
<div class="mem" id="mem" hidden>{{mem}}</div>
<span id="mem-src" hidden>{{term}}</span>
<script>
""" + SHARED_JS + _mem_js("mem-src") + """
</script>
"""

# Audio autoplay guard: the wrapper starts at data-recall-ready="false". The first tap on
# the card marks it ready and plays. Anki renders [sound:] itself, so the play element is
# found inside the wrapper rather than set directly.
AUDIO_FRONT = """
{{#audio}}
<div class="card">
  <div class="muted">Listen, then type the term</div>
  <div id="audio-wrap" data-recall-ready="false">{{audio}}</div>
  <div class="hint" id="hint">Tap to hear</div>
  <input id="typed" class="answer" autocomplete="off" autocapitalize="off"
         spellcheck="false" placeholder="Type what you heard">
  <br><button class="check" onclick="memCheck()">Check</button>
  <div id="verdict" class="feedback"></div>
  <span id="term-src" hidden>{{term}}</span>
</div>
<script>
""" + SHARED_JS + """
(function(){
  var wrap=document.getElementById('audio-wrap'), hint=document.getElementById('hint');
  function play(){
    var el=wrap.querySelector('audio, .replay-button, a');
    if(!el)return;
    if(el.tagName==='AUDIO'){el.play();} else {el.click();}
  }
  document.addEventListener('click',function ready(){
    if(wrap.getAttribute('data-recall-ready')==='true')return;
    wrap.setAttribute('data-recall-ready','true');
    hint.hidden=true;
    play();
  },false);
})();
function memCheck(){
  var v=document.getElementById('typed').value;
  memStoreSet('recall_typed',v);
  var ok=memMatch(v,document.getElementById('term-src').textContent);
  var out=document.getElementById('verdict');
  out.className='feedback '+(ok?'ok':'bad');
  out.textContent=ok?'Correct':'Not quite. You wrote: '+(v||'nothing');
}
document.getElementById('typed').addEventListener('keydown',function(e){
  if(e.key==='Enter'){e.preventDefault();memCheck();}});
document.getElementById('typed').focus();
</script>
{{/audio}}
"""

AUDIO_BACK = (
    """
{{FrontSide}}
<hr>
<div class="prompt">{{term}}</div>
<div class="muted">{{definition}}</div>
<div class="example">{{example}}</div>
<span id="term-src" hidden>{{term}}</span>
"""
    + _inline_block("term-src")
    + """
<script>
"""
    + SHARED_JS
    + """
(function(){
  var typed=memStoreGet('recall_typed');
  var t=document.getElementById('term-src').textContent;
  if(typed!==null){
    var el=document.createElement('div');
    el.className='feedback muted';
    el.textContent=typed?(memMatch(typed,t)?'You got it':'You wrote: '+typed):'';
    document.querySelector('.card').appendChild(el);
    return;
  }
  document.getElementById('inline').hidden=false;
})();
</script>
"""
)

# Speed mode: the template is static. Timer, auto-Again and the 1.2 s hold are host-app work.
SPEED_FRONT = """
<!-- Speed mode requires host app. Template outputs term + definition only. -->
<div class="card speed">
  <div class="prompt">{{term}}</div>
</div>
"""

SPEED_BACK = """
{{FrontSide}}
<div class="reveal">{{definition}}</div>
"""

# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------

MODEL = genanki.Model(
    MODEL_ID,
    "Restoration Item",
    fields=[{"name": f} for f in FIELDS],
    templates=[
        {"name": "TypeIn", "qfmt": TYPEIN_FRONT, "afmt": TYPEIN_BACK},
        {"name": "MultipleChoice", "qfmt": MC_FRONT, "afmt": MC_BACK},
        {"name": "Audio", "qfmt": AUDIO_FRONT, "afmt": AUDIO_BACK},
        {"name": "Speed", "qfmt": SPEED_FRONT, "afmt": SPEED_BACK},
    ],
    css=SHARED_CSS,
)


# ---------------------------------------------------------------------------
# Distractors
# ---------------------------------------------------------------------------

def _tag(s: str) -> str:
    return s.strip().lower().replace(" ", "-")


def level_signature(translations: list[str]) -> str:
    return hashlib.sha256("\n".join(sorted(translations)).encode("utf-8")).hexdigest()


def level_distractors(items: list[dict], changed: bool, seed_key: str) -> list[list[str]]:
    """Return up to 3 distractors per item, indexed like `items`.

    changed=True: regenerate from the level pool only (explicit lists may be stale).
    changed=False: keep explicit distractors that are still valid translations in this
    level, then fill from the pool. The fill is seeded by seed_key, so it is stable.
    """
    translations = [it["translation"] for it in items]
    out: list[list[str]] = []
    for i, it in enumerate(items):
        t = it["translation"]
        preferred: list[str] = []
        if not changed:
            preferred = [d for d in it.get("distractors", []) if d != t and d in translations]
        preferred = list(dict.fromkeys(preferred))[:3]
        cands = [x for x in dict.fromkeys(translations) if x != t and x not in preferred]
        rng = random.Random(f"{seed_key}:{i}:{t}")
        rng.shuffle(cands)
        out.append((preferred + cands)[:3])
    return out


def _load_manifest(path: Path) -> dict:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8-sig"))
    return {}


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

def build_package(json_path: Path, out_path: Path, media_dir: Path | None = None,
                  rebuild_distractors: bool = False) -> int:
    data = json.loads(json_path.read_text(encoding="utf-8-sig"))  # tolerates a BOM from Windows editors
    if data.get("format_version") != 1:
        raise SystemExit("Only format_version 1 (§5) is supported.")
    base = media_dir or json_path.parent
    manifest_path = Path(str(out_path) + ".levels.json")
    manifest = _load_manifest(manifest_path)
    new_manifest: dict = {}

    deck = genanki.Deck(DECK_ID, "Restoration Courses")
    media: list[str] = []
    count = 0
    for course in data["courses"]:
        slug = _tag(course["slug"])
        new_manifest[slug] = {}
        for level in course["levels"]:
            n = int(level["level"])
            items = level["items"]
            sig = level_signature([it["translation"] for it in items])
            new_manifest[slug][str(n)] = sig
            old = manifest.get(slug, {}).get(str(n))
            changed = rebuild_distractors or old != sig
            if changed:
                print(f"level {slug}:{n} changed, regenerating distractors for {len(items)} items")
            dis_lists = level_distractors(items, changed, f"{slug}:{n}:{sig}")

            for item, dis in zip(items, dis_lists):
                audio_field = ""
                if item.get("audio"):
                    name = Path(item["audio"]).name
                    audio_field = f"[sound:{name}]"
                    src = base / item["audio"]
                    if src.exists():
                        media.append(str(src))
                    else:
                        print(f"warning: missing audio {src}")
                tags = [f"course::{slug}", f"level::{n}"]
                if item.get("part_of_speech"):
                    tags.append(f"pos::{_tag(item['part_of_speech'])}")
                example = item.get("example", "").replace(
                    item["term"], f"<b>{item['term']}</b>")
                deck.add_note(genanki.Note(
                    model=MODEL,
                    fields=[
                        item["term"],
                        item["translation"],
                        audio_field,
                        item.get("mem", ""),
                        example,
                        course["name"],
                        str(n),
                        item.get("part_of_speech", ""),
                        "|".join(dis),
                    ],
                    tags=tags,
                ))
                count += 1

    pkg = genanki.Package(deck)
    pkg.media_files = list(dict.fromkeys(media))
    pkg.write_to_file(str(out_path))
    manifest_path.write_text(json.dumps(new_manifest, indent=2), encoding="utf-8")
    return count


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("course_json", type=Path)
    ap.add_argument("out_apkg", type=Path)
    ap.add_argument("--media-dir", type=Path, default=None)
    ap.add_argument("--rebuild-distractors", action="store_true",
                    help="regenerate distractors for every level, not only changed ones")
    args = ap.parse_args()
    n = build_package(args.course_json, args.out_apkg, args.media_dir, args.rebuild_distractors)
    print(f"wrote {n} notes to {args.out_apkg}")


if __name__ == "__main__":
    main()
