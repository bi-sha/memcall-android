"""Session-level behavior: level unlocks and the desired-retention setting.

Unlock rule (locked): level N unlocks when at least `unlock_threshold` of the cards in
level N-1 have FSRS stability >= `unlock_stability_days`. Unlocks are sticky: once
unlocked, a level stays unlocked.

This module reads FSRS state. It does not compute or change any scheduling.
"""
from __future__ import annotations

from collections import defaultdict
from typing import Any

from aqt import mw

from . import preferences as prefs

def _tag_value(note: Any, prefix: str) -> str | None:
    for tag in note.tags:
        if tag.lower().startswith(prefix):
            return tag[len(prefix):]
    return None


def _stability_days(card: Any) -> tuple[float, str]:
    """Stability in days for a card, and where it came from.

    Anki 23.10+ exposes FSRS memory state on the card. Older builds (or cards without
    FSRS state) fall back to the interval, and the result says so.
    """
    state = getattr(card, "memory_state", None)
    stability = getattr(state, "stability", None) if state is not None else None
    if stability is not None:
        return float(stability), "fsrs"
    return float(card.ivl), "interval"


def compute_unlocks() -> tuple[dict[str, dict[str, bool]], str]:
    cfg = prefs.config()
    threshold = cfg["unlock_threshold"]
    min_days = cfg["unlock_stability_days"]

    by_course: dict[str, dict[int, list[Any]]] = defaultdict(lambda: defaultdict(list))
    for cid in mw.col.find_cards("tag:course::*"):
        card = mw.col.get_card(cid)
        note = card.note()
        course = _tag_value(note, "course::")
        level = _tag_value(note, "level::")
        if course and level and level.isdigit():
            by_course[course][int(level)].append(card)

    previous = prefs.load_state()["unlocks"]
    result: dict[str, dict[str, bool]] = {}
    source = "fsrs"

    for course, levels in by_course.items():
        result[course] = {}
        for n in sorted(levels):
            if n == 1:
                unlocked = True
            else:
                prev_cards = levels.get(n - 1, [])
                if not prev_cards:
                    unlocked = True
                else:
                    passing = 0
                    for card in prev_cards:
                        days, src = _stability_days(card)
                        if src != "fsrs":
                            source = "interval"
                        if days >= min_days:
                            passing += 1
                    unlocked = passing / len(prev_cards) >= threshold
            # sticky: never re-lock a level the user already reached
            unlocked = unlocked or bool(previous.get(course, {}).get(str(n)))
            result[course][str(n)] = unlocked
    return result, source


def apply_desired_retention() -> None:
    """Write the configured desired retention to the deck options of decks that hold course cards.

    This writes a setting Anki's scheduler already owns. It does not compute intervals.
    Skipped when the value has not changed since the last write.
    """
    target = prefs.config()["desired_retention"]
    if prefs.get_applied_retention() == target:
        return
    deck_ids = {mw.col.get_card(cid).did for cid in mw.col.find_cards("tag:course::*")}
    for did in deck_ids:
        conf = mw.col.decks.config_dict_for_deck_id(did)
        if conf.get("desiredRetention") != target:
            conf["desiredRetention"] = target
            mw.col.decks.update_config(conf)
    prefs.set_applied_retention(target)


def refresh() -> None:
    unlocks, source = compute_unlocks()
    prefs.set_unlocks(unlocks, source)
    apply_desired_retention()


def on_overview(overview: Any, content: Any) -> None:
    """Runs whenever the overview screen (the step before a session) is shown."""
    try:
        refresh()
    except Exception as exc:  # never break the overview screen over an add-on problem
        print(f"recall_enhancement: refresh failed: {exc}")


def is_unlocked(course: str, level: int) -> bool:
    if level == 1:
        return True
    return bool(prefs.load_state()["unlocks"].get(course, {}).get(str(level)))
