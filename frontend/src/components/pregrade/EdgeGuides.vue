<script setup>
import { computed, ref } from 'vue'
import { t } from '../../utils/i18n'

/**
 * A photo with the card's outer edge (blue) and its inner frame (amber) as lines the customer drags onto the exact
 * edges (phase 11, pre-grade check). Arrow keys move a focused line by one step, with Shift by ten. The two boxes are
 * v-models, in thousandths of the photo.
 */
const props = defineProps({
  src: { type: String, required: true },
  label: { type: String, required: true },
})
const outer = defineModel('outer', { type: Object, required: true })
const inner = defineModel('inner', { type: Object, required: true })

const frame = ref(null)
const dragging = ref(null)

const LINES = [
  { box: 'outer', side: 'left' },
  { box: 'outer', side: 'right' },
  { box: 'outer', side: 'top' },
  { box: 'outer', side: 'bottom' },
  { box: 'inner', side: 'left' },
  { box: 'inner', side: 'right' },
  { box: 'inner', side: 'top' },
  { box: 'inner', side: 'bottom' },
]

const vertical = (line) => line.side === 'left' || line.side === 'right'
const model = (line) => (line.box === 'outer' ? outer : inner)

/** Keeps the lines in order: outer left < inner left < inner right < outer right, and the same top to bottom. */
function limits(line) {
  const o = outer.value
  const i = inner.value
  const gap = 4
  const bounds = {
    'outer-left': [0, i.left - gap],
    'inner-left': [o.left + gap, i.right - gap],
    'inner-right': [i.left + gap, o.right - gap],
    'outer-right': [i.right + gap, 1000],
    'outer-top': [0, i.top - gap],
    'inner-top': [o.top + gap, i.bottom - gap],
    'inner-bottom': [i.top + gap, o.bottom - gap],
    'outer-bottom': [i.bottom + gap, 1000],
  }
  return bounds[`${line.box}-${line.side}`]
}

function set(line, value) {
  const [low, high] = limits(line)
  const box = model(line)
  box.value = { ...box.value, [line.side]: Math.min(Math.max(value, low), high) }
}

function onPointerDown(line, event) {
  dragging.value = line
  event.target.setPointerCapture(event.pointerId)
  onPointerMove(event)
}

function onPointerMove(event) {
  const line = dragging.value
  if (!line || !frame.value) return
  const rect = frame.value.getBoundingClientRect()
  const value = vertical(line) ? ((event.clientX - rect.left) / rect.width) * 1000 : ((event.clientY - rect.top) / rect.height) * 1000
  set(line, value)
}

function onPointerUp() {
  dragging.value = null
}

function onKeydown(line, event) {
  const step = event.shiftKey ? 10 : 1
  const keys = vertical(line) ? { ArrowLeft: -step, ArrowRight: step } : { ArrowUp: -step, ArrowDown: step }
  const delta = keys[event.key]
  if (delta === undefined) return
  event.preventDefault()
  set(line, model(line).value[line.side] + delta)
}

const style = (line) => {
  const value = `${model(line).value[line.side] / 10}%`
  return vertical(line) ? { left: value } : { top: value }
}

const lineLabel = (line) => t(`pregrade.lines.${line.box}.${line.side}`)
const boxes = computed(() => ({ outer: outer.value, inner: inner.value }))
</script>

<template>
  <figure class="guides">
    <div ref="frame" class="frame" @pointermove="onPointerMove" @pointerup="onPointerUp" @pointercancel="onPointerUp">
      <img :src="src" :alt="label" draggable="false" />
      <button
        v-for="line in LINES"
        :key="`${line.box}-${line.side}`"
        type="button"
        class="line"
        :class="[line.box, vertical(line) ? 'vertical' : 'horizontal', { active: dragging === line }]"
        :style="style(line)"
        :aria-label="lineLabel(line)"
        :aria-valuenow="Math.round(boxes[line.box][line.side])"
        role="slider"
        aria-valuemin="0"
        aria-valuemax="1000"
        @pointerdown.prevent="onPointerDown(line, $event)"
        @keydown="onKeydown(line, $event)"
      ></button>
    </div>
    <figcaption>{{ label }}</figcaption>
  </figure>
</template>

<style scoped>
.guides {
  margin: 0;
}

.frame {
  position: relative;
  touch-action: none;
  user-select: none;
}

.frame img {
  display: block;
  width: 100%;
  border-radius: var(--radius-sm);
}

/* A thin visible line inside a wide invisible grip, so a finger can catch it. */
.line {
  position: absolute;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: grab;
}

.line::after {
  position: absolute;
  content: '';
}

.line.vertical {
  top: 0;
  bottom: 0;
  width: 24px;
  transform: translateX(-50%);
  cursor: ew-resize;
}

.line.vertical::after {
  top: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
}

.line.horizontal {
  right: 0;
  left: 0;
  height: 24px;
  transform: translateY(-50%);
  cursor: ns-resize;
}

.line.horizontal::after {
  top: 50%;
  right: 0;
  left: 0;
  height: 2px;
  transform: translateY(-50%);
}

.line.outer::after {
  background: var(--color-brand);
  box-shadow: 0 0 0 1px rgb(255 255 255 / 70%);
}

.line.inner::after {
  background: var(--color-warning);
  box-shadow: 0 0 0 1px rgb(255 255 255 / 70%);
}

.line:focus-visible::after,
.line.active::after {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
}

figcaption {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  text-align: center;
}
</style>
