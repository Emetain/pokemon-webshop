<script setup>
import { t } from '../utils/i18n'

// One binder page: filled pockets are owned cards, empty pockets are still missing.
const pockets = ['pokemon', null, 'lorcana', 'mtg', 'pokemon', null, null, 'one-piece', 'pokemon']
</script>

<template>
  <figure class="binder">
    <div class="sheet" aria-hidden="true">
      <span v-for="hole in 3" :key="hole" class="hole"></span>
      <div class="pockets">
        <span
          v-for="(game, index) in pockets"
          :key="index"
          class="pocket"
          :class="{ filled: game }"
          :data-game="game"
          :style="{ '--order': index }"
        ></span>
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
  background: var(--color-binder-raised);
}

.hole {
  position: absolute;
  left: var(--space-2);
  width: 0.75rem;
  aspect-ratio: 1;
  border-radius: var(--radius-round);
  background: var(--color-binder-deep);
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
  position: relative;
  aspect-ratio: 5 / 7;
  border: 1.5px dashed var(--color-on-binder-soft);
  border-radius: var(--radius-card);
}

.pocket.filled {
  border: 0;
  background: var(--accent);
  animation: slot-in 420ms cubic-bezier(0.2, 0.8, 0.3, 1) backwards;
  animation-delay: calc(var(--order) * 70ms);
}

/* The art window of a card. */
.pocket.filled::before {
  content: '';
  position: absolute;
  inset: 11% 9% 42%;
  border-radius: var(--radius-xs);
  background: var(--accent-ink);
  opacity: 0.22;
}

/* Its text box. */
.pocket.filled::after {
  content: '';
  position: absolute;
  inset: 64% 9% 9%;
  border-radius: var(--radius-xs);
  background: var(--accent-ink);
  opacity: 0.12;
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
  color: var(--color-on-binder-soft);
  font-size: var(--text-sm);
}
</style>
