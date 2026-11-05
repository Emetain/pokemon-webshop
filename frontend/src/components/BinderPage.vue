<script setup>
import BallMark from './BallMark.vue'
import { t } from '../utils/i18n'

// One page of the Pokédex: a slot per Pokémon, filled once you own a card of it.
const slots = [
  { number: '#001', filled: true },
  { number: '#002', filled: false },
  { number: '#003', filled: true },
  { number: '#004', filled: true },
  { number: '#005', filled: true },
  { number: '#006', filled: false },
  { number: '#007', filled: false },
  { number: '#008', filled: true },
  { number: '#009', filled: true },
]
</script>

<template>
  <figure class="binder">
    <div class="sheet" aria-hidden="true">
      <span v-for="hole in 3" :key="hole" class="hole"></span>
      <div class="pockets">
        <span
          v-for="(slot, index) in slots"
          :key="slot.number"
          class="pocket"
          :class="{ filled: slot.filled }"
          :style="{ '--order': index }"
        >
          <span v-if="slot.filled" class="art"><BallMark class="art-ball" /></span>
          <span class="number">{{ slot.number }}</span>
        </span>
      </div>
    </div>
    <figcaption class="caption">{{ t('binder.caption') }}</figcaption>
  </figure>
</template>

<style scoped>
.binder {
  margin: 0;
}

.sheet {
  position: relative;
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-6);
  border-radius: var(--radius-md);
  background: var(--color-brand-deep);
}

.hole {
  position: absolute;
  left: var(--space-2);
  width: 0.75rem;
  aspect-ratio: 1;
  border-radius: var(--radius-round);
  background: var(--color-brand);
}

.hole:nth-of-type(1) {
  top: 16%;
}

.hole:nth-of-type(2) {
  top: calc(50% - 0.375rem);
}

.hole:nth-of-type(3) {
  bottom: 16%;
}

.pockets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
}

.pocket {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  aspect-ratio: 5 / 7;
  border: 1.5px dashed var(--color-on-brand-soft);
  border-radius: var(--radius-card);
  color: var(--color-on-brand-soft);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.pocket.filled {
  justify-content: space-between;
  padding: 9% 9% 6%;
  border: 0;
  background: var(--color-surface);
  color: var(--color-ink);
  animation: slot-in 420ms cubic-bezier(0.2, 0.8, 0.3, 1) backwards;
  animation-delay: calc(var(--order) * 70ms);
}

/* The art window of a card. */
.art {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-bottom: 8%;
  border-radius: var(--radius-xs);
  background: var(--color-tint-strong);
}

.art-ball {
  width: 58%;
}

@keyframes slot-in {
  from {
    opacity: 0;
    transform: translateY(-1.25rem) rotate(-2deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pocket.filled {
    animation: none;
  }
}

.caption {
  margin-top: var(--space-3);
  color: var(--color-on-brand-soft);
  font-size: var(--text-sm);
}
</style>
