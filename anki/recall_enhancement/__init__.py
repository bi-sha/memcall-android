"""Recall enhancement: speed-mode timer, mem reveal after a miss, and level unlocks.

Registers its hooks once, when the main window is ready, so re-importing the
package cannot add duplicate handlers.
"""
from aqt import gui_hooks

from . import preferences, reviewer_hook, session

_registered = False


def _register() -> None:
    global _registered
    if _registered:
        return
    _registered = True
    gui_hooks.reviewer_did_show_question.append(reviewer_hook.on_show_question)
    gui_hooks.reviewer_did_answer_card.append(reviewer_hook.on_answer)
    gui_hooks.card_will_show.append(reviewer_hook.on_card_will_show)
    gui_hooks.overview_will_render_content.append(session.on_overview)
    from aqt import mw
    mw.addonManager.setConfigAction(preferences.ADDON, preferences.open_panel)


gui_hooks.main_window_did_init.append(_register)
