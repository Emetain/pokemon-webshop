<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Lock, Minus, TrendingDown, TrendingUp } from 'lucide-vue-next'
import { formatMoney, formatPercent, formatShortDate, formatWholeMoney } from '../../utils/format'
import { t } from '../../utils/i18n'
import { prefersReducedMotion, tween } from '../../utils/tween'
import { openPremium } from '../../stores/premiumDialog'

/**
 * `key` is also the text on the button. Without a length it means everything there is. 1D compares yesterday's
 * value with today's live one: the history has one value per day, and card prices change once a day (phase 11).
 */
const RANGES = [
  { key: '1D', days: 1 },
  { key: '1W', days: 7 },
  { key: '1M', months: 1 },
  { key: '3M', months: 3 },
  { key: '6M', months: 6 },
  { key: '1J', months: 12 },
  { key: 'Max', premium: true },
]

// Switching periods morphs the line: both shapes are resampled to the same points and blended (the owner's wish).
const MORPH_SAMPLES = 64
const MORPH_MS = 300

const DAY_MS = 86400000
const TICK_COUNT = 3
const TOOLTIP_FLIP_PERCENT = 60

/** `points` are `{ date, cents }`, oldest first. */
const props = defineProps({
  points: { type: Array, required: true },
  currency: { type: String, required: true },
  /** "Max" is Premium. Without it the API sends at most a year, and the button shows a lock. */
  premium: { type: Boolean, default: false },
  /** Without Premium: there's older history that "Max" would show. */
  hasOlder: { type: Boolean, default: false },
  /** Where the texts are in nl.js: the value graph's own, or the price graph of a card (phase 12). */
  textKey: { type: String, default: 'collection.history' },
})

const tx = (key, values) => t(`${props.textKey}.${key}`, values)

const rangeKey = ref('1M')
const activeIndex = ref(null)
const plot = ref(null)

const range = computed(() => RANGES.find((item) => item.key === rangeKey.value))
const lastDate = computed(() => props.points.at(-1)?.date)
const hasLine = computed(() => props.points.length >= 2)

const timeOf = (date) => new Date(`${date}T00:00:00Z`).getTime()

function rangeStart(item, end) {
  if (!item.days && !item.months) return null
  const date = new Date(`${end}T00:00:00Z`)
  if (item.days) date.setUTCDate(date.getUTCDate() - item.days)
  else date.setUTCMonth(date.getUTCMonth() - item.months)
  return date.toISOString().slice(0, 10)
}

const start = computed(() => rangeStart(range.value, lastDate.value))

/** The points of the period, starting at the last known value on or before its first day. */
const visible = computed(() => {
  if (!start.value) return props.points
  const before = props.points.filter((point) => point.date <= start.value).at(-1)
  const within = props.points.filter((point) => point.date > start.value)
  return before ? [before, ...within] : within
})

/** A period can be longer than the history. The text then says since when, not "the last year". */
const coversPeriod = computed(() => Boolean(start.value) && visible.value[0]?.date <= start.value)

const change = computed(() => {
  const first = visible.value[0]
  const last = visible.value.at(-1)
  if (!first || !last) return null
  const cents = last.cents - first.cents
  return {
    cents,
    percent: first.cents > 0 ? (cents / first.cents) * 100 : null,
    period: coversPeriod.value
      ? tx(`periods.${rangeKey.value}`)
      : tx('periods.since', { date: formatShortDate(first.date) }),
  }
})

/** The change as shown: it counts from the old period's to the new one's while the line morphs. */
const shownChange = ref(null)

const changeText = computed(() => {
  if (!change.value) return ''
  const { cents: rawCents, percent: rawPercent } = shownChange.value ?? change.value
  const cents = Math.round(rawCents)
  const percent = rawPercent === null ? null : rawPercent
  if (cents === 0) return tx('noChange')
  const sign = cents > 0 ? '+' : '−'
  const money = `${sign}${formatMoney(Math.abs(cents), props.currency)}`
  return percent === null ? money : `${money} (${sign}${formatPercent(Math.abs(percent))})`
})

const changeIcon = computed(() => {
  if (!change.value || change.value.cents === 0) return Minus
  return change.value.cents > 0 ? TrendingUp : TrendingDown
})

const changeClass = computed(() => {
  if (!change.value || change.value.cents === 0) return ''
  return change.value.cents > 0 ? 'up' : 'down'
})

/** Round steps, about three of them, so the numbers along the chart are easy to read. */
function niceStep(span) {
  const rough = span / TICK_COUNT
  const power = 10 ** Math.floor(Math.log10(rough))
  return [1, 2, 5, 10].map((factor) => factor * power).find((step) => step >= rough)
}

/**
 * Like a stock chart the scale follows the values instead of starting at zero, because a collection of €1.000
 * that moves €10 would otherwise be a flat line. The numbers along the side show how far it really moved.
 */
const scale = computed(() => {
  const values = visible.value.map((point) => point.cents)
  const low = Math.min(...values)
  const high = Math.max(...values)
  const margin = high > low ? (high - low) * 0.15 : Math.max(high * 0.05, 100)
  const min = Math.max(low - margin, 0)
  const max = high + margin
  const step = Math.max(niceStep(max - min), 1)
  const ticks = []
  for (let tick = Math.ceil(min / step) * step; tick <= max; tick += step) ticks.push(tick)
  return { min, max, step, ticks }
})

const xOf = (point) => {
  const first = timeOf(visible.value[0].date)
  const span = Math.max(timeOf(visible.value.at(-1).date) - first, DAY_MS)
  return ((timeOf(point.date) - first) / span) * 100
}

const yOf = (cents) => (1 - (cents - scale.value.min) / (scale.value.max - scale.value.min)) * 100

// The plot stretches to any width, so the line and area are drawn in a 100 by 100 box.
const coordinates = computed(() => visible.value.map((point) => [xOf(point), yOf(point.cents)]))

/** While the line morphs, the blended samples; at rest null, and the real points are drawn. */
const morph = ref(null)
let stopMorph = null

const drawn = computed(() => morph.value ?? coordinates.value)

const linePath = computed(() => drawn.value.map(([x, y], index) => `${index ? 'L' : 'M'}${x} ${y}`).join(' '))

const areaPath = computed(() => {
  const points = drawn.value
  if (!points.length) return ''
  return `${linePath.value} L${points.at(-1)[0]} 100 L${points[0][0]} 100 Z`
})

/** The line at [count] evenly spaced x positions, so two periods with different numbers of days can be blended. */
function resample(points, count = MORPH_SAMPLES) {
  if (!points.length) return []
  if (points.length === 1) return Array.from({ length: count }, (_, index) => [(index / (count - 1)) * 100, points[0][1]])
  const samples = []
  let segment = 0
  for (let index = 0; index < count; index++) {
    const x = (index / (count - 1)) * 100
    while (segment < points.length - 2 && points[segment + 1][0] < x) segment++
    const [x0, y0] = points[segment]
    const [x1, y1] = points[segment + 1]
    const share = x1 > x0 ? Math.min(Math.max((x - x0) / (x1 - x0), 0), 1) : 1
    samples.push([x, y0 + (y1 - y0) * share])
  }
  return samples
}

/**
 * A new period morphs from the line as it's drawn now, also halfway through another morph, and the change counts
 * along. With reduced motion it switches at once.
 */
// The change of the period shown last, where counting along starts.
let settledChange = null

watch(coordinates, (next, previous) => {
  const target = change.value
  const fromChange = shownChange.value ?? settledChange ?? target
  settledChange = target
  stopMorph?.()
  stopMorph = null
  if (!previous?.length || !next.length || prefersReducedMotion()) {
    morph.value = null
    shownChange.value = null
    return
  }
  const from = resample(morph.value ?? previous)
  const to = resample(next)
  stopMorph = tween({
    duration: MORPH_MS,
    onFrame(progress) {
      morph.value = from.map(([x, y], index) => [x, y + (to[index][1] - y) * progress])
      if (fromChange && target) {
        const percent =
          fromChange.percent === null || target.percent === null
            ? target.percent
            : fromChange.percent + (target.percent - fromChange.percent) * progress
        shownChange.value = { cents: fromChange.cents + (target.cents - fromChange.cents) * progress, percent }
      }
    },
    onDone() {
      morph.value = null
      shownChange.value = null
      stopMorph = null
    },
  })
}, { immediate: true })

onBeforeUnmount(() => stopMorph?.())

const tickLabel = (cents) =>
  scale.value.step >= 100 ? formatWholeMoney(cents, props.currency) : formatMoney(cents, props.currency)

const active = computed(() => {
  if (activeIndex.value === null) return null
  const point = visible.value[activeIndex.value]
  return point ? { point, x: xOf(point), y: yOf(point.cents) } : null
})

const lastMark = computed(() => {
  if (morph.value) {
    const [x, y] = morph.value.at(-1)
    return { x, y }
  }
  const point = visible.value.at(-1)
  return point ? { x: xOf(point), y: yOf(point.cents) } : null
})

const summary = computed(() => {
  const first = visible.value[0]
  const last = visible.value.at(-1)
  if (!first || !last) return ''
  return tx('summary', {
    start: formatShortDate(first.date),
    end: formatShortDate(last.date),
    from: formatMoney(first.cents, props.currency),
    to: formatMoney(last.cents, props.currency),
  })
})

const tableRows = computed(() => [...visible.value].reverse())

const locked = (item) => item.premium && !props.premium

function chooseRange(item) {
  if (locked(item)) {
    openPremium('history', props.hasOlder ? tx('maxLockedOlder') : '')
    return
  }
  rangeKey.value = item.key
  activeIndex.value = null
}

/** Readers aim at a date and not at a thin line, so this finds the nearest day. */
function onPointerMove(event) {
  const box = plot.value.getBoundingClientRect()
  const percent = ((event.clientX - box.left) / box.width) * 100
  let nearest = 0
  coordinates.value.forEach(([x], index) => {
    if (Math.abs(x - percent) < Math.abs(coordinates.value[nearest][0] - percent)) nearest = index
  })
  activeIndex.value = nearest
}

function clearActive() {
  activeIndex.value = null
}

function onKeydown(event) {
  const last = visible.value.length - 1
  const current = activeIndex.value ?? last
  const next = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: last }[event.key]
  if (event.key === 'Escape') clearActive()
  if (next === undefined) return
  event.preventDefault()
  activeIndex.value = Math.min(Math.max(next, 0), last)
}
</script>

<template>
  <section class="history" :aria-label="tx('label')">
    <p v-if="!hasLine" class="empty">{{ tx('empty') }}</p>

    <template v-else>
      <p class="change" :class="changeClass">
        <component :is="changeIcon" :size="18" aria-hidden="true" />
        <strong>{{ changeText }}</strong>
        <span class="period">{{ change.period }}</span>
      </p>

      <div
        ref="plot"
        class="plot"
        role="group"
        tabindex="0"
        :aria-label="summary"
        @pointermove="onPointerMove"
        @pointerdown="onPointerMove"
        @pointerleave="clearActive"
        @blur="clearActive"
        @keydown="onKeydown"
      >
        <div v-for="tick in scale.ticks" :key="tick" class="grid-line" :style="{ top: `${yOf(tick)}%` }">
          <span class="tick">{{ tickLabel(tick) }}</span>
        </div>

        <svg class="lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <path class="area" :d="areaPath" />
          <path class="line" :d="linePath" vector-effect="non-scaling-stroke" />
        </svg>

        <span
          v-if="lastMark && !active"
          class="dot"
          :style="{ left: `${lastMark.x}%`, top: `${lastMark.y}%` }"
          aria-hidden="true"
        ></span>

        <template v-if="active">
          <span class="crosshair" :style="{ left: `${active.x}%` }" aria-hidden="true"></span>
          <span class="dot" :style="{ left: `${active.x}%`, top: `${active.y}%` }" aria-hidden="true"></span>
          <div
            class="tooltip"
            :class="{ flipped: active.x > TOOLTIP_FLIP_PERCENT }"
            :style="{ left: `${active.x}%` }"
            aria-live="polite"
          >
            <strong class="tooltip-value">{{ formatMoney(active.point.cents, currency) }}</strong>
            <span class="tooltip-date">{{ formatShortDate(active.point.date) }}</span>
          </div>
        </template>
      </div>

      <p class="dates" aria-hidden="true">
        <span>{{ formatShortDate(visible[0].date) }}</span>
        <span>{{ formatShortDate(visible.at(-1).date) }}</span>
      </p>

      <div class="ranges" role="group" :aria-label="tx('ranges')">
        <button
          v-for="item in RANGES"
          :key="item.key"
          class="range"
          type="button"
          :class="{ locked: locked(item) }"
          :aria-pressed="locked(item) ? undefined : item.key === rangeKey"
          :aria-haspopup="locked(item) ? 'dialog' : undefined"
          :aria-label="locked(item) ? tx('maxLockedLabel') : undefined"
          @click="chooseRange(item)"
        >
          <Lock v-if="locked(item)" :size="13" aria-hidden="true" />
          {{ item.key }}
        </button>
      </div>

      <p class="note">{{ tx('note') }}</p>

      <details class="table-view">
        <summary>{{ tx('table') }}</summary>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">{{ tx('date') }}</th>
                <th class="amount" scope="col">{{ tx('value') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="point in tableRows" :key="point.date">
                <th scope="row">{{ formatShortDate(point.date) }}</th>
                <td class="amount">{{ formatMoney(point.cents, currency) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </section>
</template>

<style scoped>
.history {
  margin-top: var(--space-2);
}

.empty {
  max-width: 60ch;
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

/* Up or down is never colour alone: there is an arrow and a sign as well. */
.change {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  font-size: var(--text-md);
}

.change.up {
  color: var(--color-success);
}

.change.down {
  color: var(--color-danger);
}

.period {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.plot {
  position: relative;
  height: 14rem;
  margin-top: var(--space-4);
  cursor: crosshair;
  touch-action: pan-y;
}

.grid-line {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 1;
  height: 1px;
  background: var(--color-line);
}

/* A backing of its own, because the line can run right through this corner. */
.tick {
  position: absolute;
  bottom: 2px;
  left: 0;
  padding-right: var(--space-1);
  background: var(--color-surface);
  color: var(--color-ink-soft);
  font-size: var(--text-2xs);
  line-height: 1.2;
}

.lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.area {
  fill: var(--color-brand);
  opacity: 0.1;
}

.line {
  fill: none;
  stroke: var(--color-brand);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2px;
}

.dot {
  position: absolute;
  width: 0.625rem;
  height: 0.625rem;
  border: 2px solid var(--color-surface);
  border-radius: var(--radius-round);
  background: var(--color-brand);
  box-sizing: content-box;
  transform: translate(-50%, -50%);
}

.crosshair {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--color-line-strong);
}

.tooltip {
  position: absolute;
  top: 0;
  z-index: 2;
  display: grid;
  gap: var(--space-half);
  margin-left: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
  pointer-events: none;
  white-space: nowrap;
}

.tooltip.flipped {
  margin-left: calc(var(--space-2) * -1);
  transform: translateX(-100%);
}

.tooltip-value {
  font-size: var(--text-sm);
}

.tooltip-date {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.dates {
  display: flex;
  justify-content: space-between;
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.ranges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-3);
}

.range {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 0;
  border-radius: var(--radius-round);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

@media (hover: hover) {
  .range:hover {
    background: var(--color-tint);
  }
}

.range[aria-pressed='true'] {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.range.locked {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-ink-soft);
}

.note {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.table-view {
  margin-top: var(--space-1);
  font-size: var(--text-sm);
}

.table-view summary {
  min-height: var(--touch-target);
  color: var(--color-link);
  cursor: pointer;
  font-weight: 600;
  line-height: var(--touch-target);
}

.table-scroll {
  max-height: 16rem;
  overflow-y: auto;
}

.table-view table {
  width: 100%;
  max-width: 24rem;
  border-collapse: collapse;
}

.table-view th,
.table-view td {
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-weight: 400;
  text-align: left;
}

.table-view thead th {
  font-weight: 650;
}

.table-view .amount {
  text-align: right;
}

@media (min-width: 768px) {
  .plot {
    height: 18rem;
  }
}
</style>
