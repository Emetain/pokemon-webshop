/**
 * Follows a sideways swipe of a finger or pen on `element`. A mouse never swipes: on a computer, text stays
 * selectable and links stay clickable as they always were.
 *
 * Nothing happens until the finger has moved 10px. If it moved up or down first, the swipe lets go and the page
 * scrolls as usual. From then on `onMove` gets the distance in px from where the swipe was picked up, so the element
 * moves 1:1 with the finger without jumping. `onEnd` gets that distance and the speed of the finger in px/s when it
 * let go. A click that follows a swipe is swallowed, so letting go on a link doesn't open it.
 *
 * The element needs `touch-action: pan-y pinch-zoom` in its CSS, so the browser leaves sideways moves to this code
 * and still scrolls and zooms. A box that scrolls inside it needs the same: the browser asks the nearest scrolling box.
 *
 * Returns a function that removes the listeners.
 */
const SLOP = 10
// Speed is measured over the last moments of the swipe, not the whole of it.
const VELOCITY_WINDOW_MS = 100

export function trackSwipe(element, { canStart = () => true, onStart, onMove, onEnd }) {
  let pointerId = null
  let startX = 0
  let startY = 0
  let originX = 0
  let locked = false
  let history = []

  function reset() {
    pointerId = null
    locked = false
    history = []
  }

  function onPointerDown(event) {
    if (event.pointerType === 'mouse' || !event.isPrimary || pointerId !== null) return
    if (!canStart(event)) return
    pointerId = event.pointerId
    startX = event.clientX
    startY = event.clientY
    history = [{ x: event.clientX, t: event.timeStamp }]
  }

  function onPointerMove(event) {
    if (event.pointerId !== pointerId) return
    const x = event.clientX
    history.push({ x, t: event.timeStamp })
    while (history.length > 2 && event.timeStamp - history[0].t > VELOCITY_WINDOW_MS) history.shift()

    if (!locked) {
      const dx = Math.abs(x - startX)
      const dy = Math.abs(event.clientY - startY)
      if (dx < SLOP && dy < SLOP) return
      if (dy >= dx) {
        reset()
        return
      }
      locked = true
      originX = x
      element.setPointerCapture?.(pointerId)
      onStart?.()
    }
    onMove?.(x - originX)
  }

  function velocity() {
    const first = history[0]
    const last = history.at(-1)
    const elapsed = last.t - first.t
    return elapsed > 0 ? ((last.x - first.x) / elapsed) * 1000 : 0
  }

  function finish(event, cancelled) {
    if (event.pointerId !== pointerId) return
    if (locked) {
      if (!cancelled) swallowNextClick()
      onEnd?.({ dx: event.clientX - originX, velocity: cancelled ? 0 : velocity() })
    }
    reset()
  }

  const onPointerUp = (event) => finish(event, false)
  const onPointerCancel = (event) => finish(event, true)

  element.addEventListener('pointerdown', onPointerDown)
  element.addEventListener('pointermove', onPointerMove)
  element.addEventListener('pointerup', onPointerUp)
  element.addEventListener('pointercancel', onPointerCancel)

  return () => {
    element.removeEventListener('pointerdown', onPointerDown)
    element.removeEventListener('pointermove', onPointerMove)
    element.removeEventListener('pointerup', onPointerUp)
    element.removeEventListener('pointercancel', onPointerCancel)
  }
}

function swallowNextClick() {
  const swallow = (event) => {
    event.preventDefault()
    event.stopPropagation()
  }
  window.addEventListener('click', swallow, { capture: true, once: true })
  // Browsers mostly send no click after a swipe at all. Then the next real tap must not be swallowed.
  setTimeout(() => window.removeEventListener('click', swallow, { capture: true }), 300)
}
