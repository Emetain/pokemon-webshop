<script setup>
import { computed, ref } from 'vue'
import { Minus, TrendingDown, TrendingUp } from 'lucide-vue-next'
import { formatMoney, formatPercent, formatShortDate, formatWholeMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** `key` is also the text on the button. Without a length it means everything there is. */
const RANGES = [
  { key: '7D', days: 7 },
  { key: '1M', months: 1 },
  { key: '3M', months: 3 },
  { key: '6M', months: 6 },
  { key: '1J', months: 12 },
  { key: 'all' },
]

const DAY_MS = 86400000
const TICK_COUNT = 3
const TOOLTIP_FLIP_PERCENT = 60

/** `points` are `{ date, cents }`, oldest first. */
const props = defineProps({
  points: { type: Array, required: true },
  currency: { type: String, required: true },
})

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
      ? t(`collection.history.periods.${rangeKey.value}`)
      : t('collection.history.periods.since', { date: formatShortDate(first.date) }),
  }
})

const changeText = computed(() => {
  if (!change.value) return ''
  const { cents, percent } = change.value
  if (cents === 0) return t('collection.history.noChange')
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

const linePath = computed(() => coordinates.value.map(([x, y], index) => `${index ? 'L' : 'M'}${x} ${y}`).join(' '))

const areaPath = computed(() => {
  const points = coordinates.value
  if (!points.length) return ''
  return `${linePath.value} L${points.at(-1)[0]} 100 L${points[0][0]} 100 Z`
})

const tickLabel = (cents) =>
  scale.value.step >= 100 ? formatWholeMoney(cents, props.currency) : formatMoney(cents, props.currency)

const active = computed(() => {
  if (activeIndex.value === null) return null
  const point = visible.value[activeIndex.value]
  return point ? { point, x: xOf(point), y: yOf(point.cents) } : null
})

const lastMark = computed(() => {
  const point = visible.value.at(-1)
  return point ? { x: xOf(point), y: yOf(point.cents) } : null
})

const summary = computed(() => {
  const first = visible.value[0]
  const last = visible.value.at(-1)
  if (!first || !last) return ''
  return t('collection.history.summary', {
    start: formatShortDate(first.date),
    end: formatShortDate(last.date),
    from: formatMoney(first.cents, props.currency),
    to: formatMoney(last.cents, props.currency),
  })
})

const tableRows = computed(() => [...visible.value].reverse())

const rangeLabel = (item) => (item.key === 'all' ? t('collection.history.all') : item.key)

function chooseRange(item) {
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
  <section class="history" :aria-label="t('collection.history.label')">
    <p v-if="!hasLine" class="empty">{{ t('collection.history.empty') }}</p>

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

      <div class="ranges" role="group" :aria-label="t('collection.history.ranges')">
        <button
          v-for="item in RANGES"
          :key="item.key"
          class="range"
          type="button"
          :aria-pressed="item.key === rangeKey"
          @click="chooseRange(item)"
        >
          {{ rangeLabel(item) }}
        </button>
      </div>

      <p class="note">{{ t('collection.history.note') }}</p>

      <details class="table-view">
        <summary>{{ t('collection.history.table') }}</summary>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">{{ t('collection.history.date') }}</th>
                <th class="amount" scope="col">{{ t('collection.history.value') }}</th>
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

.range:hover {
  background: var(--color-tint);
}

.range[aria-pressed='true'] {
  background: var(--color-brand);
  color: var(--color-on-brand);
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
