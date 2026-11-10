<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-vue-next'
import BallMark from '../BallMark.vue'
import DropCountdown from '../drops/DropCountdown.vue'
import { findGame } from '../../data/games'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The hero as a carousel. The default slot is always the first slide: the hero with the binder. The slides
 * from admin follow. It turns by itself, but stops while the pointer or focus is on it, after a click on
 * pause, while the tab is hidden or the hero is scrolled out of view, and for visitors who prefer reduced motion.
 */
const props = defineProps({
  slides: { type: Array, default: () => [] },
})

const INTERVAL_MS = 8000

const current = ref(0)
const hovering = ref(false)
const focused = ref(false)
const paused = ref(false)
const reducedMotion = ref(false)
const inView = ref(true)
const root = ref(null)

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

function go(index) {
  current.value = (index + total.value) % total.value
}

let timer = null

function tick() {
  if (turning.value && inView.value && !hovering.value && !focused.value && !document.hidden) go(current.value + 1)
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focused.value = false
}

let motionQuery = null
let observer = null
const onMotionChange = () => (reducedMotion.value = motionQuery.matches)

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  onMotionChange()
  motionQuery.addEventListener('change', onMotionChange)
  // On a phone the hero changes height with the slide, so it must not turn while someone reads further down.
  observer = new IntersectionObserver(([entry]) => (inView.value = entry.isIntersecting), { threshold: 0.5 })
  observer.observe(root.value)
  timer = setInterval(tick, INTERVAL_MS)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  observer?.disconnect()
  motionQuery?.removeEventListener('change', onMotionChange)
})
</script>

<template>
  <section
    ref="root"
    class="carousel"
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
    <div class="track" :aria-live="turning ? 'off' : 'polite'">
      <div
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
      <button class="control" type="button" :aria-label="t('home.hero.previous')" @click="go(current - 1)">
        <ChevronLeft :size="20" aria-hidden="true" />
      </button>
      <ol class="dots">
        <li v-for="index in total" :key="index">
          <button
            class="dot"
            type="button"
            :aria-label="t('home.hero.choose', { number: index })"
            :aria-current="current === index - 1 ? 'true' : undefined"
            @click="go(index - 1)"
          ></button>
        </li>
      </ol>
      <button class="control" type="button" :aria-label="t('home.hero.next')" @click="go(current + 1)">
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
}

.control:hover {
  background: rgb(255 255 255 / 16%);
}

.carousel[data-drop-colour='yellow'] .control:hover {
  background: rgb(0 0 0 / 8%);
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
  transition: width 0.3s;
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

@media (prefers-reduced-motion: reduce) {
  .carousel,
  .slide,
  .slide.active,
  .dot::after {
    transition: none;
  }

  .live-dot {
    animation: none;
  }
}
</style>
