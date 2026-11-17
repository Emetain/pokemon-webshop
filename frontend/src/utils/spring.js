/**
 * A spring, described the way Apple describes them: a damping ratio and a response.
 *
 * - damping: 1 settles without overshooting. Lower overshoots and swings: 0.8 is a small bounce. Only use a bounce
 *   when the motion follows a flick, so the element carries the momentum of the finger.
 * - response: roughly how many seconds it takes to get there. Lower is snappier. It is not a duration: the spring
 *   stops when it has come to rest.
 *
 * The spring keeps its own value and velocity. A new target starts from where the element is now and keeps the speed
 * it has, so a moving drawer or slide can be caught and sent back at any moment without a jump.
 */

// Small enough that a value from 0 to 1 (a drawer, a slide) has come to rest without a visible last step.
const REST_DELTA = 0.0005
const REST_VELOCITY = 0.005
const STEP = 1 / 500

export function createSpring(initial, onUpdate) {
  let value = initial
  let velocity = 0
  let target = initial
  let stiffness = 0
  let friction = 0
  let frame = 0
  let last = 0
  let onRest = null

  function step(now) {
    // After a hidden tab or a long frame, take at most a few frames' worth of time instead of jumping.
    let remaining = Math.min((now - last) / 1000, 0.064)
    last = now
    while (remaining > 0) {
      const dt = Math.min(remaining, STEP)
      const force = -stiffness * (value - target) - friction * velocity
      velocity += force * dt
      value += velocity * dt
      remaining -= dt
    }
    if (Math.abs(value - target) < REST_DELTA && Math.abs(velocity) < REST_VELOCITY) {
      value = target
      velocity = 0
      frame = 0
      onUpdate(value)
      const done = onRest
      onRest = null
      done?.()
      return
    }
    onUpdate(value)
    frame = requestAnimationFrame(step)
  }

  return {
    get value() {
      return value
    },
    get target() {
      return target
    },
    get moving() {
      return frame !== 0
    },

    /**
     * Moves to `next`. Without `velocity` the spring keeps the speed it has, so a change of target never stalls.
     * `velocity` is in units of the value per second, so for a value from 0 to 1 a finger speed in px/s divided by
     * the distance in px. `onRest` runs once it has settled there, and not when it is sent elsewhere first.
     */
    to(next, { damping = 1, response = 0.4, velocity: start, onRest: done } = {}) {
      target = next
      if (start !== undefined) velocity = start
      stiffness = ((2 * Math.PI) / response) ** 2
      friction = (4 * Math.PI * damping) / response
      onRest = done ?? null
      if (!frame) {
        last = performance.now()
        frame = requestAnimationFrame(step)
      }
    },

    /** Jumps to `next` and stops there: for following a finger, which needs no spring. */
    set(next) {
      this.stop()
      value = next
      target = next
      velocity = 0
      onUpdate(value)
    },

    /** Stops where it is, for instance when a finger catches it. */
    stop() {
      cancelAnimationFrame(frame)
      frame = 0
      onRest = null
      velocity = 0
    },
  }
}

/**
 * How far a flick carries on after the finger lets go, in px: the deceleration of a scroll view. Choosing where to
 * land from this point instead of from where the finger let go makes a short fast flick count as much as a long drag.
 */
export function project(velocity, decelerationRate = 0.998) {
  return ((velocity / 1000) * decelerationRate) / (1 - decelerationRate)
}

/** Resistance past an edge: the further past it, the less the element follows the finger. */
export function rubberband(overshoot, dimension, constant = 0.55) {
  if (!dimension) return 0
  return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot))
}

const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

/** True when the visitor asked for less motion. Read at the moment of use, so a change applies straight away. */
export function prefersReducedMotion() {
  return reducedMotionQuery.matches
}
