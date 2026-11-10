"""Configuration and persisted state for the Recall enhancement add-on.

Config (config.json, edited through the panel below) holds user settings.
State (user_files/state.json) holds mem flags and unlock results. Neither contains
scheduling logic: the add-on only reads FSRS state and writes the desired-retention value.
"""
from __future__ import annotations

import json
import os
from typing import Any

from aqt import mw
from aqt.qt import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QDoubleSpinBox,
    QFormLayout,
    QSpinBox,
    QVBoxLayout,
)

# Package name, e.g. "recall_enhancement". __name__ here would be "...preferences".
ADDON = __name__.split(".")[0]

DEFAULTS: dict[str, Any] = {
    "desired_retention": 0.9,
    "speed_timer_seconds": 3,
    "speed_hold_ms": 1200,
    "mem_reveal_mode": "on_fail",  # or "on_demand"
    "unlock_threshold": 0.8,
    "unlock_stability_days": 7,
}

_state_cache: dict[str, Any] | None = None


def config() -> dict[str, Any]:
    """Return config merged over defaults, with values clamped to safe ranges."""
    stored = mw.addonManager.getConfig(ADDON) or {}
    cfg = {**DEFAULTS, **stored}
    cfg["desired_retention"] = min(0.99, max(0.70, float(cfg["desired_retention"])))
    cfg["speed_timer_seconds"] = min(10, max(1, float(cfg["speed_timer_seconds"])))
    cfg["speed_hold_ms"] = min(5000, max(0, int(cfg["speed_hold_ms"])))
    if cfg["mem_reveal_mode"] not in ("on_fail", "on_demand"):
        cfg["mem_reveal_mode"] = DEFAULTS["mem_reveal_mode"]
    cfg["unlock_threshold"] = min(1.0, max(0.0, float(cfg["unlock_threshold"])))
    cfg["unlock_stability_days"] = max(0.0, float(cfg["unlock_stability_days"]))
    return cfg


# ---------------------------------------------------------------------------
# State file
# ---------------------------------------------------------------------------

def _state_path() -> str:
    folder = os.path.join(mw.addonManager.addonsFolder(ADDON), "user_files")
    os.makedirs(folder, exist_ok=True)
    return os.path.join(folder, "state.json")


def _empty_state() -> dict[str, Any]:
    return {"mem_flags": [], "unlocks": {}, "applied_retention": None, "unlock_source": "fsrs"}


def load_state() -> dict[str, Any]:
    global _state_cache
    if _state_cache is None:
        try:
            with open(_state_path(), encoding="utf-8") as f:
                loaded = json.load(f)
        except (OSError, ValueError):
            loaded = {}
        _state_cache = {**_empty_state(), **loaded}
    return _state_cache


def save_state() -> None:
    """Write atomically so a crash cannot leave a half-written file."""
    if _state_cache is None:
        return
    path = _state_path()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(_state_cache, f, indent=2)
    os.replace(tmp, path)


def add_mem_flag(card_id: int) -> None:
    st = load_state()
    if card_id not in st["mem_flags"]:
        st["mem_flags"].append(card_id)
        save_state()


def has_mem_flag(card_id: int) -> bool:
    return card_id in load_state()["mem_flags"]


def set_unlocks(unlocks: dict[str, dict[str, bool]], source: str) -> None:
    st = load_state()
    st["unlocks"] = unlocks
    st["unlock_source"] = source
    save_state()


def get_applied_retention() -> float | None:
    return load_state().get("applied_retention")


def set_applied_retention(value: float) -> None:
    load_state()["applied_retention"] = value
    save_state()


# ---------------------------------------------------------------------------
# Config panel (Tools > Add-ons > Recall enhancement > Config)
# ---------------------------------------------------------------------------

class _Panel(QDialog):
    def __init__(self) -> None:
        super().__init__(mw)
        self.setWindowTitle("Recall enhancement")
        cfg = config()

        self.retention = QDoubleSpinBox()
        self.retention.setRange(0.70, 0.99)
        self.retention.setSingleStep(0.01)
        self.retention.setDecimals(2)
        self.retention.setValue(cfg["desired_retention"])
        self.retention.setToolTip("Written to the deck option 'Desired retention' for decks with course cards.")

        self.speed = QSpinBox()
        self.speed.setRange(1, 10)
        self.speed.setSuffix(" s")
        self.speed.setValue(int(cfg["speed_timer_seconds"]))

        self.mem = QComboBox()
        self.mem.addItem("Show after a miss (on_fail)", "on_fail")
        self.mem.addItem("Show on demand (on_demand)", "on_demand")
        self.mem.setCurrentIndex(0 if cfg["mem_reveal_mode"] == "on_fail" else 1)

        form = QFormLayout()
        form.addRow("Desired retention", self.retention)
        form.addRow("Speed timer", self.speed)
        form.addRow("Mem reveal", self.mem)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._save)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(buttons)

    def _save(self) -> None:
        cfg = config()
        cfg["desired_retention"] = round(self.retention.value(), 2)
        cfg["speed_timer_seconds"] = self.speed.value()
        cfg["mem_reveal_mode"] = self.mem.currentData()
        mw.addonManager.writeConfig(ADDON, cfg)
        self.accept()


def open_panel() -> None:
    _Panel().exec()
