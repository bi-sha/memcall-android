"""Reviewer behavior: speed-mode timer with auto-Again, and mem reveal after a miss.

Hooks used (Anki 2.1.50+):
  gui_hooks.reviewer_did_show_question(card)
  gui_hooks.reviewer_did_answer_card(reviewer, card, ease)
  gui_hooks.card_will_show(text, card, kind)

Private API: reviewer._showAnswer() is called on speed timeout. It is the only
underscore method used here, because the reviewer has no public "show answer" call.
"""
from __future__ import annotations

from typing import Any, Optional

from aqt import gui_hooks, mw
from aqt.qt import QTimer

from . import preferences as prefs

SPEED_TAG = "recall_speed"
AGAIN = 1  # reviewer ease values: 1 Again, 2 Hard, 3 Good, 4 Easy

_timers: list[QTimer] = []


def _cancel_timers() -> None:
    for t in _timers:
        t.stop()
    _timers.clear()


def _after(ms: int, fn: Any) -> None:
    t = QTimer(mw)
    t.setSingleShot(True)
    t.timeout.connect(fn)
    t.start(ms)
    _timers.append(t)


def _note_field(note: Any, name: str) -> str:
    try:
        return note[name]
    except KeyError:
        return ""


def _is_speed(card: Any) -> bool:
    return any(t.lower() == SPEED_TAG for t in card.note().tags)


def _has_mem(card: Any) -> bool:
    return bool(_note_field(card.note(), "mem").strip())


# ---------------------------------------------------------------------------
# Speed mode
# ---------------------------------------------------------------------------

def _current_card_is(cid: int) -> bool:
    rev = mw.reviewer
    return rev is not None and rev.card is not None and rev.card.id == cid


def _on_speed_expired(cid: int) -> None:
    rev = mw.reviewer
    if not _current_card_is(cid) or getattr(rev, "state", None) != "question":
        return  # user already revealed or moved on: their choice stands
    rev._showAnswer()  # private call, see module docstring
    _after(prefs.config()["speed_hold_ms"], lambda: _auto_again(cid))


def _auto_again(cid: int) -> None:
    if _current_card_is(cid):
        mw.reviewer.answerCard(AGAIN)


def on_show_question(card: Any) -> None:
    _cancel_timers()
    if card is None or not _is_speed(card):
        return
    seconds = prefs.config()["speed_timer_seconds"]
    _after(int(seconds * 1000), lambda cid=card.id: _on_speed_expired(cid))


# ---------------------------------------------------------------------------
# Grading and mem reveal
# ---------------------------------------------------------------------------

def on_answer(reviewer: Any, card: Any, ease: int) -> None:
    _cancel_timers()  # any grade (manual or auto) ends the speed timer
    cfg = prefs.config()
    if ease == AGAIN and cfg["mem_reveal_mode"] == "on_fail" and _has_mem(card):
        prefs.add_mem_flag(card.id)


def on_card_will_show(text: str, card: Any, kind: str) -> str:
    if kind not in ("reviewQuestion", "reviewAnswer") or _is_speed(card) or not _has_mem(card):
        return text
    mem_html = _note_field(card.note(), "mem")
    mode = prefs.config()["mem_reveal_mode"]

    if mode == "on_fail" and prefs.has_mem_flag(card.id):
        # Shown on every presentation after a miss, not just the next one.
        return text + f'<div class="mem">{mem_html}</div>'

    if mode == "on_demand" and kind == "reviewQuestion":
        return (
            text
            + '<button class="check" onclick="this.nextElementSibling.hidden=false;this.remove()">'
            + "Show mem</button>"
            + f'<div class="mem" hidden>{mem_html}</div>'
        )
    return text
