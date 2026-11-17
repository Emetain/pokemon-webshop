import { onBeforeUnmount, onMounted } from 'vue'
import { createSpring, prefersReducedMotion, project, rubberband } from './spring'
import { trackSwipe } from './swipe'

/**
 * A panel that slides in from the side of the screen inside a modal <dialog>, over a scrim that darkens with it.
 * One spring moves both, from 0 (closed) to 1 (open), so:
 *
 * - opening and closing start from wherever the panel is: a drawer that is closing can be opened again halfway;
 * - a finger can swipe the panel back out towards its side. It follows the finger 1:1, resists when pulled further
 *   in, and on letting go it closes or settles back depending on where the flick would have carried it;
 * - for visitors who prefer less motion the panel fades instead of sliding.
 *
 * `side` is the edge the panel sits against. `onDismiss` runs when a swipe closes the drawer, so the owner can update
 * its own state; it then calls `close()` as usual.
 */
const SCRIM_OPACITY = 0.5
const OPEN = { damping: 1, response: 0.35 }
const CLOSE = { damping: 1, response: 0.3 }
// Back into place after a swipe that didn't close it: a small bounce, because the finger gave it momentum.
const SETTLE = { damping: 0.8, response: 0.3 }

export function useDrawer({ dialog, panel, scrim, side, onDismiss }) {
  // The way the panel moves when it closes: to the right for a drawer on the right.
  const outward = side === 'left' ? -1 : 1
  const spring = createSpring(0, render)

  function render(progress) {
    if (!panel.value || !scrim.value) return
    scrim.value.style.opacity = String(Math.min(Math.max(progress, 0), 1) * SCRIM_OPACITY)
    if (prefersReducedMotion()) {
      panel.value.style.transform = 'none'
      panel.value.style.opacity = String(Math.min(Math.max(progress, 0), 1))
    } else {
      panel.value.style.opacity = ''
      panel.value.style.transform = `translate3d(${(1 - progress) * 100 * outward}%, 0, 0)`
    }
  }

  function open() {
    if (!dialog.value.open) {
      spring.set(0)
      dialog.value.showModal()
    }
    spring.to(1, OPEN)
  }

  function close() {
    if (!dialog.value?.open) return
    spring.to(0, { ...CLOSE, onRest: () => dialog.value?.close() })
  }

  // Following a finger.
  let width = 0
  let startProgress = 0

  /** Where the panel is when the finger has moved `dx` px; pulled further in than open, it resists. */
  function progressAt(dx) {
    const progress = startProgress - (dx * outward) / width
    if (progress <= 1) return Math.max(progress, 0)
    return 1 + rubberband((progress - 1) * width, width) / width
  }

  let stopSwipe = null

  onMounted(() => {
    render(0)
    stopSwipe = trackSwipe(panel.value, {
      onStart() {
        width = panel.value.offsetWidth
        startProgress = spring.value
        spring.stop()
      },
      onMove(dx) {
        spring.set(progressAt(dx))
      },
      onEnd({ dx, velocity }) {
        const progressVelocity = (-velocity * outward) / width
        const landing = startProgress - ((dx + project(velocity)) * outward) / width
        if (landing < 0.5) {
          spring.to(0, { ...CLOSE, velocity: progressVelocity, onRest: () => dialog.value?.close() })
          onDismiss()
        } else {
          spring.to(1, { ...SETTLE, velocity: progressVelocity })
        }
      },
    })
  })

  onBeforeUnmount(() => {
    stopSwipe?.()
    spring.stop()
  })

  return { open, close }
}
