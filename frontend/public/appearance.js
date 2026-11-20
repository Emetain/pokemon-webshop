// Puts the customer's colours on the page before it is drawn, so a dark page doesn't flash white first (phase 12).
// stores/appearance.js saves the choice under this key and takes over once the app runs. A separate file because the
// Content-Security-Policy only allows scripts from the site itself, not inline ones.
;(function () {
  try {
    var saved = JSON.parse(window.localStorage.getItem('appearance') || 'null')
    if (!saved) return
    var root = document.documentElement
    var dark =
      saved.mode === 'DARK' ||
      (saved.mode === 'SYSTEM' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
    if (dark) root.setAttribute('data-mode', 'dark')
    if (saved.theme && saved.theme !== 'POKETOPIA') root.setAttribute('data-theme', String(saved.theme).toLowerCase())
  } catch (error) {
    // Without storage the page starts light, and the app sets the colours once it knows who is logged in.
  }
})()
