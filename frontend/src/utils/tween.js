/**
 * A short tween on requestAnimationFrame, for values CSS can't transition, such as an SVG path with a different number
 * of points (phase 11: the value graph). A new call while one runs starts from wherever the caller's value is then.
 */

/** A CSS cubic-bezier as a function of time, solved for x with Newton's method and a bisection fallback. */
export function cubicBezier(x1, y1, x2, y2) {
  const ax = 3 * x1 - 3 * x2 + 1
  const bx = 3 * x2 - 6 * x1
  const cx = 3 * x1
  const ay = 3 * y1 - 3 * y2 + 1
  const by = 3 * y2 - 6 * y1
  const cy = 3 * y1
  const sampleX = (t) => ((ax * t + bx) * t + cx) * t
  const sampleY = (t) => ((ay * t + by) * t + cy) * t
  const slopeX = (t) => (3 * ax * t + 2 * bx) * t + cx

  function solve(x) {
    let t = x
    for (let i = 0; i < 8; i++) {
      const error = sampleX(t) - x
      if (Math.abs(error) < 1e-6) return t
      const slope = slopeX(t)
      if (Math.abs(slope) < 1e-6) break
      t -= error / slope
    }
    let low = 0
    let high = 1
    t = x
    while (high - low > 1e-6) {
      if (sampleX(t) < x) low = t
      else high = t
      t = (low + high) / 2
    }
    return t
  }

  return (x) => (x <= 0 ? 0 : x >= 1 ? 1 : sampleY(solve(x)))
}

/** A strong ease-out for UI: fast at the start, where the eye is, and a long soft landing. */
export const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1)

export function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

/**
 * Calls [onFrame] with the eased progress from 0 to 1 over [duration] ms, then [onDone]. Returns a function that
 * stops it where it is.
 */
export function tween({ duration, easing = EASE_OUT, onFrame, onDone }) {
  let frame = 0
  const start = performance.now()
  const step = (now) => {
    const progress = Math.min((now - start) / duration, 1)
    onFrame(easing(progress))
    if (progress < 1) frame = requestAnimationFrame(step)
    else onDone?.()
  }
  frame = requestAnimationFrame(step)
  return () => cancelAnimationFrame(frame)
}
