<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-vue-next'
import BallMark from '../BallMark.vue'
import { findGame } from '../../data/games'
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
const accentOf = (slide) => (slide?.game ? findGame(slide.game)?.accent : undefined)
const currentAccent = computed(() => (current.value === 0 ? undefined : accentOf(props.slides[current.value - 1])))

const isExternal = (url) => /^https:\/\//.test(url)

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
        :key="slide.id"
        class="slide promo"
        :class="{ active: current === index + 1 }"
        :data-game="accentOf(slide)"
        role="group"
        aria-roledescription="slide"
        :aria-label="t('home.hero.slide', { number: index + 2, total })"
        :inert="current !== index + 1"
      >
        <!-- The ball is the Pokémon touch of the shop: not on a slide in the colour of another game. -->
        <BallMark v-if="!slide.imageUrl && (!slide.game || slide.game === 'pokemon')" class="promo-ball" outline />
        <div class="container promo-grid" :class="{ 'with-image': slide.imageUrl }">
          <div class="promo-text">
            <h2 class="promo-title">{{ slide.title }}</h2>
            <p v-if="slide.text" class="promo-body">{{ slide.text }}</p>
            <template v-if="slide.linkUrl">
              <a v-if="isExternal(slide.linkUrl)" class="btn btn-on-brand" :href="slide.linkUrl" rel="noopener">
                {{ slide.buttonLabel }}
              </a>
              <router-link v-else class="btn btn-on-brand" :to="slide.linkUrl">{{ slide.buttonLabel }}</router-link>
            </template>
          </div>
          <img v-if="slide.imageUrl" class="promo-image" :src="slide.imageUrl" alt="" decoding="async" />
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

.promo-image {
  justify-self: center;
  width: 100%;
  max-width: 22rem;
  max-height: 18rem;
  object-fit: contain;
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
}

@media (prefers-reduced-motion: reduce) {
  .carousel,
  .slide,
  .slide.active,
  .dot::after {
    transition: none;
  }
}
</style>
