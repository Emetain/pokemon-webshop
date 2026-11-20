import { maxGradeByCentering } from './centering'

/**
 * One score with a decimal for the pre-grade check, such as 9.7 out of 10 (the owner's wish, 2026-11-20). An
 * indication, never a grade: no grading company is behind it. Built like the subgrades some graders publish: centering,
 * corners, edges and surface each get a score, and the total is their average, held down by the weakest one and never
 * above what the centering allows on the common 10-point scales ([maxGradeByCentering]), so it agrees with the
 * centering line below it.
 */

/** The larger share of the worst axis → the centering score, in a straight line between the points. */
const FRONT_POINTS = [
  [50, 10],
  [55, 9.5],
  [60, 9],
  [65, 8],
  [70, 7],
  [80, 6],
  [85, 5],
  [90, 3],
  [100, 1],
]
const BACK_POINTS = [
  [55, 10],
  [75, 9.5],
  [90, 8],
  [100, 1],
]

/** What one spot of wear costs. Whitening on a corner or an edge counts per spot, the surface per side. */
const WEAR_COST = {
  corners: { light: 0.5, clear: 1.5 },
  edges: { light: 0.5, clear: 1.5 },
  surface: { light: 1, clear: 2.5 },
}

/** The total is at most this far above the weakest part: one bad corner pulls a card down, like at a grader. */
const ABOVE_WEAKEST = 0.5

function along(points, share) {
  if (share <= points[0][0]) return points[0][1]
  for (let i = 1; i < points.length; i++) {
    const [x1, y1] = points[i - 1]
    const [x2, y2] = points[i]
    if (share <= x2) return y1 + ((share - x1) / (x2 - x1)) * (y2 - y1)
  }
  return points.at(-1)[1]
}

const bounded = (score) => Math.min(10, Math.max(1, score))

function wearScore(sides, part) {
  const items = sides.flatMap((side) => {
    if (part === 'surface') return side.surface ? [side.surface] : []
    return side[part] ?? []
  })
  const cost = items.reduce((total, item) => total + (WEAR_COST[part][item.condition] ?? 0), 0)
  return bounded(10 - cost)
}

/**
 * `frontWorst` and `backWorst`: the larger share of the worst axis of each side, from [centering]. `sides`: the check's
 * sides with their corners, edges and surface. Null until both sides are measured.
 */
export function pregradeScore({ frontWorst, backWorst, sides }) {
  if (frontWorst == null || backWorst == null) return null
  const parts = {
    centering: bounded(Math.min(along(FRONT_POINTS, frontWorst), along(BACK_POINTS, backWorst))),
    corners: wearScore(sides, 'corners'),
    edges: wearScore(sides, 'edges'),
    surface: wearScore(sides, 'surface'),
  }
  const values = Object.values(parts)
  const average = values.reduce((total, value) => total + value, 0) / values.length
  const total = Math.min(average, Math.min(...values) + ABOVE_WEAKEST, maxGradeByCentering(frontWorst, backWorst))
  return {
    score: Math.round(bounded(total) * 10) / 10,
    parts: Object.fromEntries(Object.entries(parts).map(([key, value]) => [key, Math.round(value * 10) / 10])),
  }
}

/** "9.7", and "10" for a perfect score. A point, as grades are always written. */
export const formatScore = (score) => (score >= 10 ? '10' : score.toFixed(1))

/** Red for a low score, green for a high one: the hue of the score's colour, 0 (red) to 130 (green). Most cards score 5 or more. */
export const scoreHue = (score) => Math.round(Math.min(1, Math.max(0, (score - 5) / 5)) * 130)
