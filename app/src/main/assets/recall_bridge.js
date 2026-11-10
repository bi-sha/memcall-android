// Loaded by every card page. Exposes window.Recall on top of the native RecallNative bridge.
(function () {
  if (window.Recall) return;

  function native() {
    return window.RecallNative || null;
  }

  window.Recall = {
    // which: 'again' or 'good'
    grade: function (which) {
      var n = native();
      if (n) n.grade(String(which));
    },
    // Text the card's type-in box stored before the page changed.
    getTyped: function () {
      try {
        return localStorage.getItem('recall_typed') || '';
      } catch (e) {
        return '';
      }
    },
    playAudio: function (name) {
      var n = native();
      if (n) n.playAudio(String(name));
    }
  };
})();
