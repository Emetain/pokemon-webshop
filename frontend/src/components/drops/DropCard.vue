<script setup>
import { computed } from 'vue'
import { Zap } from 'lucide-vue-next'
import BallMark from '../BallMark.vue'
import DropCountdown from './DropCountdown.vue'
import { findGame } from '../../data/games'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** A drop in the list on /drops: its banner, name, when, and a countdown while it is upcoming. */
const props = defineProps({
  drop: { type: Object, required: true },
  serverTime: { type: String, default: null },
})

const emit = defineEmits(['started'])

const accent = computed(() => (props.drop.game ? findGame(props.drop.game)?.accent : undefined))
const isLive = computed(() => props.drop.status === 'LIVE')
const isPast = computed(() => props.drop.status === 'ENDED')

const products = computed(() =>
  props.drop.productCount === 1 ? t('drops.oneProduct') : t('drops.products', { count: props.drop.productCount }),
)
</script>

<template>
  <article class="drop-card" :class="{ live: isLive, past: isPast }">
    <router-link class="card-link" :to="`/drops/${drop.slug}`">
      <div class="banner" :data-game="accent">
        <img v-if="drop.bannerUrl" :src="drop.bannerUrl" alt="" loading="lazy" decoding="async" />
        <template v-else>
          <BallMark v-if="!accent || accent === 'pokemon'" class="banner-ball" outline />
          <Zap class="banner-icon" :size="40" aria-hidden="true" />
        </template>
        <span v-if="isLive" class="pill live-pill"><span class="dot" aria-hidden="true"></span>{{ t('drops.status.LIVE') }}</span>
        <span v-else-if="drop.soldOut" class="pill sold-pill">{{ t('drops.soldOut') }}</span>
        <span v-else-if="isPast" class="pill past-pill">{{ t('drops.status.ENDED') }}</span>
      </div>
      <div class="body">
        <h3 class="name">{{ drop.name }}</h3>
        <p class="when">
          <template v-if="isPast">{{ t('drops.endedOn', { date: formatDateTime(drop.endedAt ?? drop.startsAt) }) }}</template>
          <template v-else-if="isLive && drop.endsAt">{{ t('drops.liveUntil', { time: formatDateTime(drop.endsAt) }) }}</template>
          <template v-else-if="isLive">{{ t('drops.liveSince', { time: formatDateTime(drop.startsAt) }) }}</template>
          <template v-else>{{ t('drops.startsOn', { date: formatDateTime(drop.startsAt) }) }}</template>
        </p>
        <p class="meta">{{ products }}</p>
        <div v-if="drop.status === 'SCHEDULED'" class="countdown-row">
          <span class="countdown-label">{{ t('drops.countdown.label') }}</span>
          <DropCountdown :target="drop.startsAt" :server-time="serverTime" @done="emit('started')" />
        </div>
        <span v-if="!isPast" class="cta">{{ isLive ? t('drops.join') : t('drops.view') }}</span>
      </div>
    </router-link>
  </article>
</template>

<style scoped>
.drop-card {
  height: 100%;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.2s;
}

.drop-card:hover {
  box-shadow: var(--shadow-panel);
  transform: translateY(-2px);
}

.drop-card.live {
  border-color: var(--color-sale);
  box-shadow: 0 0 0 2px var(--color-sale-soft);
}

.card-link {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: var(--color-ink);
  text-decoration: none;
}

.banner {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--accent);
  color: var(--accent-ink);
}

.banner img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.past .banner img {
  filter: grayscale(0.6);
}

.banner-ball {
  position: absolute;
  right: -3rem;
  bottom: -4rem;
  width: 12rem;
  height: 12rem;
  color: var(--accent-ink);
  opacity: 0.18;
}

.banner-icon {
  position: relative;
}

.pill {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  font-size: var(--text-xs);
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.live-pill {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.sold-pill,
.past-pill {
  background: var(--color-surface);
  color: var(--color-ink);
}

.dot {
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

.body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-4);
}

.name {
  font-size: var(--text-lg);
  line-height: var(--leading-tight);
}

.card-link:hover .name {
  color: var(--color-brand);
  text-decoration: underline;
}

.when,
.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.countdown-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-size: var(--text-lg);
}

.countdown-label {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.cta {
  margin-top: auto;
  padding-top: var(--space-3);
  color: var(--color-brand);
  font-size: var(--text-sm);
  font-weight: 700;
}

.live .cta {
  color: var(--color-sale);
}

@media (prefers-reduced-motion: reduce) {
  .drop-card,
  .drop-card:hover {
    transform: none;
    transition: none;
  }

  .dot {
    animation: none;
  }
}
</style>
