/**
 * Centering from a card's outer edge and its inner frame (phase 11, pre-grade check). Boxes are in thousandths of the
 * photo's width and height; the photo's own aspect ratio cancels out, because each ratio compares two borders on the
 * same axis.
 */

/** Commonly published centering limits per grade on the 10-point scale: the larger share on the front and back. */
export const CENTERING_SCALE = [
  { grade: 10, front: 55, back: 75 },
  { grade: 9, front: 60, back: 90 },
  { grade: 8, front: 65, back: 90 },
  { grade: 7, front: 70, back: 90 },
  { grade: 6, front: 80, back: 90 },
  { grade: 5, front: 85, back: 90 },
  { grade: 3, front: 90, back: 90 },
]

/** The strictest scales want a front this even, and a back like [PERFECT_BACK], for their top grade. */
export const PERFECT_FRONT = 50.5
export const PERFECT_BACK = 55

/** Two borders as a split such as [55, 45], rounded to whole percents. */
function split(first, second) {
  const total = first + second
  if (total <= 0) return null
  const share = Math.round((first / total) * 100)
  return [share, 100 - share]
}

export function centering(outer, inner) {
  if (!outer || !inner) return null
  const leftRight = split(inner.left - outer.left, outer.right - inner.right)
  const topBottom = split(inner.top - outer.top, outer.bottom - inner.bottom)
  if (!leftRight || !topBottom) return null
  return { leftRight, topBottom, worst: Math.max(...leftRight, ...topBottom) }
}

/** The highest grade the centering alone still allows, from the worst axis of each side. */
export function maxGradeByCentering(frontWorst, backWorst) {
  const front = frontWorst ?? 50
  const back = backWorst ?? 50
  return CENTERING_SCALE.find((step) => front <= step.front && back <= step.back)?.grade ?? 2
}

/** A sensible start when the check found no frame: an even border of five percent. */
export function guessInner(outer) {
  const insetX = (outer.right - outer.left) * 0.05
  const insetY = (outer.bottom - outer.top) * 0.05
  return { left: outer.left + insetX, top: outer.top + insetY, right: outer.right - insetX, bottom: outer.bottom - insetY }
}

export const DEFAULT_OUTER = { left: 80, top: 60, right: 920, bottom: 940 }
