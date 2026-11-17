<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-vue-next'
import BallMark from '../BallMark.vue'
import DropCountdown from '../drops/DropCountdown.vue'
import { findGame } from '../../data/games'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'
import { createSpring, project } from '../../utils/spring'
import { trackSwipe } from '../../utils/swipe'

/**
 * The hero as a carousel. The default slot is always the first slide: the hero with the binder. The slides
 * from admin follow. It turns by itself, but stops while the pointer or focus is on it, after a click on
 * pause, while the tab is hidden or the hero is scrolled out of view, and for visitors who prefer reduced motion.
 *
 * The slides lie side by side, like a strip: the next one comes in from the right. On a touch screen the strip follows
 * the finger, and a flick turns it one slide on. Visitors who prefer reduced motion get a cross-fade instead.
 */
const props = defineProps({
  slides: { type: Array, default: () => [] },
})

const INTERVAL_MS = 8000
// Turning by itself is slower and calmer than turning on a click or a swipe.
const AUTO = { damping: 1, response: 0.6 }
const MANUAL = { damping: 1, response: 0.45 }

const current = ref(0)
const hovering = ref(false)
const focused = ref(false)
const paused = ref(false)
const reducedMotion = ref(false)
const inView = ref(true)
const dragging = ref(false)
const root = ref(null)
const track = ref(null)
// The slide elements, in order, for moving them while the strip turns.
const slideEls = []

const total = computed(() => props.slides.length + 1)
const turning = computed(() => total.value > 1 && !paused.value && !reducedMotion.value)
/** A drop slide has the colour the admin chose for it, the other slides the colour of their game. */
const accentOf = (slide) => (slide?.game && !slide.drop ? findGame(slide.game)?.accent : undefined)
const colourOf = (slide) => (slide?.drop ? (slide.drop.colour ?? 'BLACK').toLowerCase() : undefined)
const currentSlide = computed(() => (current.value === 0 ? undefined : props.slides[current.value - 1]))
const currentAccent = computed(() => accentOf(currentSlide.value))
const currentColour = computed(() => colourOf(currentSlide.value))
/** A banner from admin is the picture of a drop slide. Without one, the slide shows the products of the drop. */
const picturesOf = (slide) => (slide.imageUrl ? [] : (slide.drop?.pictures ?? []))
const hasPicture = (slide) => Boolean(slide.imageUrl) || picturesOf(slide).length > 0

const isExternal = (url) => /^https:\/\//.test(url)

/** Drop slides come from the shop itself: the page writes their label and button. Started counts once the clock hits zero. */
const started = ref(new Set())
const dropLive = (slide) => slide.drop?.status === 'LIVE' || started.value.has(slide.key)
const buttonLabel = (slide) => {
  if (!slide.drop) return slide.buttonLabel
  return dropLive(slide) ? t('drops.hero.join') : t('drops.hero.view')
}
function onDropStarted(slide) {
  started.value = new Set([...started.value, slide.key])
}

const wrap = (index) => ((index % total.value) + total.value) % total.value

/*
 * Where the strip is, in slides: 1 shows the second slide, 1.5 is halfway the second and the third. It counts on past
 * the last slide, so going round from the last to the first is one more step to the right, not a run back.
 */
const position = createSpring(0, render)

/** How far slide `index` is from the middle at `at`, the shortest way round: -0.5 is half off to the left. */
function offsetOf(index, at) {
  const n = total.value
  let offset = (index - at) % n
  if (offset < -n / 2) offset += n
  if (offset >= n / 2) offset -= n
  return offset
}

function render(at) {
  slideEls.length = total.value
  slideEls.forEach((element, index) => {
    if (!element) return
    if (reducedMotion.value || total.value < 2) {
      element.style.transform = ''
      element.style.visibility = ''
      return
    }
    const offset = offsetOf(index, at)
    const visible = Math.abs(offset) < 1
    element.style.visibility = visible ? 'visible' : 'hidden'
    element.style.transform = visible && offset !== 0 ? `translate3d(${offset * 100}%, 0, 0)` : ''
  })
}

/** Turns the strip `steps` slides on, from wherever it is or is going. */
function turn(steps, motion = MANUAL) {
  if (total.value < 2) return
  const to = (position.moving ? position.target : Math.round(position.value)) + steps
  current.value = wrap(to)
  if (reducedMotion.value) position.set(to)
  else position.to(to, motion)
}

const next = () => turn(1)
const previous = () => turn(-1)
// A dot to the right of the current one turns the strip forward, one to the left back.
const choose = (index) => turn(index - current.value)

let timer = null

function tick() {
  const resting = inView.value && !hovering.value && !focused.value && !dragging.value && !document.hidden
  if (turning.value && resting) turn(1, AUTO)
}

// Following a finger.
let startPosition = 0
let width = 0
let stopSwipe = null

const swipe = {
  // A part of a slide with a swipe of its own, like the binder, keeps it.
  canStart: (event) => total.value > 1 && !event.target.closest('[data-own-swipe]'),
  onStart() {
    dragging.value = true
    width = track.value.offsetWidth
    position.stop()
    startPosition = position.value
  },
  onMove(dx) {
    if (!reducedMotion.value) position.set(startPosition - dx / width)
  },
  onEnd({ dx, velocity }) {
    dragging.value = false
    // Where the flick would carry the strip, but never more than one slide on from where the swipe began.
    const landing = startPosition - (dx + project(velocity)) / width
    const from = Math.round(startPosition)
    const to = Math.min(Math.max(Math.round(landing), from - 1), from + 1)
    current.value = wrap(to)
    if (reducedMotion.value) position.set(to)
    else position.to(to, { ...MANUAL, velocity: -velocity / width })
  },
}

watch([total, reducedMotion], () => render(position.value), { flush: 'post' })

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focused.value = false
}

let motionQuery = null
let observer = null
const onMotionChange = () => {
  reducedMotion.value = motionQuery.matches
  // Settle on the current slide, so a change of setting halfway a turn leaves nothing half-way.
  position.set(Math.round(position.value))
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  onMotionChange()
  motionQuery.addEventListener('change', onMotionChange)
  // On a phone the hero changes height with the slide, so it must not turn while someone reads further down.
  observer = new IntersectionObserver(([entry]) => (inView.value = entry.isIntersecting), { threshold: 0.5 })
  observer.observe(root.value)
  timer = setInterval(tick, INTERVAL_MS)
  // The whole hero, the dots and arrows included: a swipe that starts there should turn the strip too.
  stopSwipe = trackSwipe(root.value, swipe)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  stopSwipe?.()
  position.stop()
  observer?.disconnect()
  motionQuery?.removeEventListener('change', onMotionChange)
})
</script>

<template>
  <section
    ref="root"
    class="carousel"
    :class="{ strip: !reducedMotion }"
    :data-game="currentAccent"
    :data-drop-colour="currentColour"
    :aria-label="t('home.hero.label')"
    aria-roledescription="carousel"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
    @focusin="focused = true"
    @focusout="onFocusOut"
  >
    <!--
      From 768px all slides share one grid cell, so the hero keeps the height of the tallest and never jumps.
      On a phone the binder slide is far taller than the others, so there the hero follows the current slide.
    -->
    <div ref="track" class="track" :aria-live="turning ? 'off' : 'polite'">
      <div
        :ref="(element) => (slideEls[0] = element)"
        class="slide"
        :class="{ active: current === 0 }"
        role="group"
        aria-roledescription="slide"
        :aria-label="t('home.hero.slide', { number: 1, total })"
        :inert="current !== 0"
      >
        <slot />
      </div>
      <div
        v-for="(slide, index) in slides"
        :key="slide.key ?? slide.id"
        :ref="(element) => (slideEls[index + 1] = element)"
        class="slide promo"
        :class="{ active: current === index + 1, 'with-pictures': picturesOf(slide).length }"
        :data-game="accentOf(slide)"
        :data-drop-colour="colourOf(slide)"
        role="group"
        aria-roledescription="slide"
        :aria-label="t('home.hero.slide', { number: index + 2, total })"
        :inert="current !== index + 1"
      >
        <!-- The ball is the Pokémon touch of the shop: not on a slide in the colour of another game. -->
        <BallMark v-if="!hasPicture(slide) && (!slide.game || slide.game === 'pokemon')" class="promo-ball" outline />
        <div class="container promo-grid" :class="{ 'with-image': hasPicture(slide) }">
          <div class="promo-text">
            <p v-if="slide.drop" class="drop-label" :class="{ live: dropLive(slide) }">
              <span v-if="dropLive(slide)" class="live-dot" aria-hidden="true"></span>
              {{ dropLive(slide) ? t('drops.hero.live') : t('drops.hero.label') }}
            </p>
            <h2 class="promo-title">{{ slide.title }}</h2>
            <p v-if="slide.text" class="promo-body">{{ slide.text }}</p>
            <div v-if="slide.drop && !dropLive(slide)" class="drop-when">
              <span>{{ t('drops.hero.startsAt', { date: formatDateTime(slide.drop.startsAt) }) }}</span>
              <DropCountdown
                class="drop-countdown"
                :target="slide.drop.startsAt"
                :server-time="slide.drop.serverTime"
                @done="onDropStarted(slide)"
              />
            </div>
            <template v-if="slide.linkUrl">
              <a v-if="isExternal(slide.linkUrl)" class="btn btn-on-brand" :href="slide.linkUrl" rel="noopener">
                {{ buttonLabel(slide) }}
              </a>
              <router-link v-else class="btn btn-on-brand" :to="slide.linkUrl">{{ buttonLabel(slide) }}</router-link>
            </template>
          </div>
          <img v-if="slide.imageUrl" class="promo-image" :src="slide.imageUrl" alt="" decoding="async" />
          <!-- Up to three products: the first in front, the others fanned out behind it. -->
          <div v-else-if="picturesOf(slide).length" class="drop-pictures" :data-count="picturesOf(slide).length">
            <img
              v-for="picture in picturesOf(slide)"
              :key="picture.url"
              class="drop-picture"
              :class="{ card: picture.card }"
              :src="picture.url"
              alt=""
              decoding="async"
            />
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > 1" class="container controls">
      <button class="control" type="button" :aria-label="t('home.hero.previous')" @click="previous">
        <ChevronLeft :size="20" aria-hidden="true" />
      </button>
      <ol class="dots">
        <li v-for="index in total" :key="index">
          <button
            class="dot"
            type="button"
            :aria-label="t('home.hero.choose', { number: index })"
            :aria-current="current === index - 1 ? 'true' : undefined"
            @click="choose(index - 1)"
          ></button>
        </li>
      </ol>
      <button class="control" type="button" :aria-label="t('home.hero.next')" @click="next">
        <ChevronRight :size="20" aria-hidden="true" />
      </button>
      <button
        v-if="!reducedMotion"
        class="control"
        type="button"
        :aria-label="paused ? t('home.hero.play') : t('home.hero.pause')"
        @click="paused = !paused"
      >
        <component :is="paused ? Play : Pause" :size="18" aria-hidden="true" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.carousel {
  position: relative;
  overflow: hidden;
  /* Up and down scroll the page and two fingers zoom; sideways turns the strip. */
  touch-action: pan-y pinch-zoom;
  background: var(--accent);
  color: var(--accent-ink);
  transition: background-color 0.5s;
}

.carousel :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.track {
  display: grid;
}

.slide {
  grid-area: 1 / 1;
  visibility: hidden;
  opacity: 0;
  transition:
    opacity 0.5s,
    visibility 0s linear 0.5s;
}

.slide.active {
  visibility: visible;
  opacity: 1;
  transition:
    opacity 0.5s,
    visibility 0s;
}

/*
 * As a strip, the script moves the slides and shows the ones in view, so they don't fade. Before it has moved them,
 * the rules above still hide all but the current one.
 */
.strip .slide,
.strip .slide.active {
  opacity: 1;
  transition: none;
}

/* A slide passing by keeps its own colour, also while the hero already takes that of the next one. */
.slide:not([data-game], [data-drop-colour]) {
  --accent: var(--color-brand);
  --accent-ink: var(--color-on-brand);
}

/* The slides that aren't showing don't count for the height on a phone. */
.slide:not(.active) {
  position: absolute;
  inset: 0 0 auto;
}

.promo {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  background: var(--accent);
  color: var(--accent-ink);
}

.promo[data-drop-colour] {
  background: var(--drop-gradient);
}

/* White on yellow is hard to see: there the button and the focus ring take the dark ink. */
.promo[data-drop-colour='yellow'] .btn-on-brand {
  background: var(--accent-ink);
  box-shadow: none;
  color: var(--color-surface);
}

.carousel[data-drop-colour='yellow'] :focus-visible {
  outline-color: var(--accent-ink);
}

.promo-ball {
  position: absolute;
  top: 50%;
  right: -8rem;
  width: 26rem;
  height: 26rem;
  color: var(--accent-ink);
  opacity: 0.16;
  transform: translateY(-50%) rotate(-18deg);
}

.promo-grid {
  position: relative;
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-6) var(--space-8);
}

.promo-text {
  display: grid;
  justify-items: start;
  gap: var(--space-4);
  max-width: 36rem;
}

.promo-title {
  font-size: var(--text-hero);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 0.98;
}

.promo-body {
  color: var(--accent-ink);
  font-size: var(--text-lg);
}

.drop-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-half) var(--space-3);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.drop-label.live {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

/* A red label would vanish on a red slide. */
.promo[data-drop-colour='red'] .drop-label.live {
  background: var(--color-surface);
  color: var(--color-sale);
}

.live-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: var(--radius-round);
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.3;
  }
}

.drop-when {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
  font-weight: 650;
}

.drop-countdown {
  font-size: var(--text-2xl);
}

.promo-image {
  justify-self: center;
  width: 100%;
  max-width: 22rem;
  max-height: 18rem;
  object-fit: contain;
}

.drop-pictures {
  position: relative;
  justify-self: center;
  width: 100%;
  max-width: 20rem;
  height: 15rem;
}

/*
 * Every picture is as tall as the space for it and as wide as its shape makes it, so the shadow and the corners of
 * a card follow the picture itself. Only a picture too wide for the space is narrowed, and keeps its shape inside.
 */
.drop-picture {
  position: absolute;
  inset: 0;
  width: auto;
  max-width: 100%;
  height: 100%;
  margin: auto;
  object-fit: contain;
  filter: drop-shadow(0 14px 18px var(--color-drop-picture-shadow));
}

.drop-picture.card {
  border-radius: var(--radius-card);
}

.drop-picture:first-child {
  z-index: 1;
}

/*
 * Two or three: the first product in front and biggest, the others smaller and pinned to the sides behind it. Their
 * size is capped in both directions, so a tall booster or a wide box behind never outgrows the product in front.
 */
.drop-pictures:not([data-count='1']) .drop-picture:not(:first-child) {
  height: 74%;
  max-width: 46%;
}

.drop-pictures[data-count='2'] .drop-picture:first-child {
  inset: 0 auto 0 4%;
  max-width: 62%;
  transform: rotate(-3deg);
}

.drop-pictures[data-count='2'] .drop-picture:nth-child(2) {
  inset: 0 0 0 auto;
  height: 80%;
  max-width: 52%;
  transform: rotate(6deg);
}

.drop-pictures[data-count='3'] .drop-picture:first-child {
  max-width: 58%;
}

.drop-pictures[data-count='3'] .drop-picture:nth-child(2) {
  inset: 0 auto 0 0;
  transform: rotate(-6deg);
}

.drop-pictures[data-count='3'] .drop-picture:nth-child(3) {
  inset: 0 0 0 auto;
  transform: rotate(6deg);
}

.controls {
  position: absolute;
  right: 0;
  bottom: var(--space-3);
  left: 0;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  pointer-events: none;
}

.controls > * {
  pointer-events: auto;
}

.control {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 0;
  border-radius: var(--radius-round);
  background: transparent;
  color: var(--accent-ink);
  cursor: pointer;
  transition: transform var(--press-out);
}

.control:active {
  background: rgb(255 255 255 / 16%);
  transform: scale(0.92);
  transition: transform var(--press-in);
}

.carousel[data-drop-colour='yellow'] .control:active {
  background: rgb(0 0 0 / 8%);
}

@media (hover: hover) {
  .control:hover {
    background: rgb(255 255 255 / 16%);
  }

  .carousel[data-drop-colour='yellow'] .control:hover {
    background: rgb(0 0 0 / 8%);
  }
}

.dots {
  display: flex;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* A dot is small to see, but the button around it is big enough to tap. */
.dot {
  position: relative;
  width: 1.75rem;
  height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.dot::after {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0.5rem;
  height: 0.5rem;
  border: 1.5px solid var(--accent-ink);
  border-radius: var(--radius-round);
  content: '';
  transform: translate(-50%, -50%);
  transition: width 0.3s var(--ease-spring);
}

.dot[aria-current='true']::after {
  width: 1.25rem;
  background: var(--accent-ink);
}

@media (min-width: 768px) {
  .slide:not(.active) {
    position: static;
  }

  .promo-grid.with-image {
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
    align-items: center;
  }

  .promo-image {
    max-height: 24rem;
  }

  /* Next to the text, the pictures take the full height of the slide but 20px: 10px above and below. */
  .promo.with-pictures {
    align-items: stretch;
  }

  /* Half the width for the pictures, so a square one can be that tall too. */
  .promo.with-pictures .promo-grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .drop-pictures {
    align-self: stretch;
    max-width: none;
    height: auto;
    margin-block: calc(10px - var(--space-6)) calc(10px - var(--space-8));
  }
}

/* Without motion the slides still cross-fade, briefly: a change you can follow, without anything moving. */
@media (prefers-reduced-motion: reduce) {
  .slide {
    transition:
      opacity 0.25s,
      visibility 0s linear 0.25s;
  }

  .slide.active {
    transition:
      opacity 0.25s,
      visibility 0s;
  }

  .carousel,
  .dot::after {
    transition: none;
  }

  .live-dot {
    animation: none;
  }
}
</style>
