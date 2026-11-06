<script setup>
import BallMark from '../BallMark.vue'

defineProps({
  accent: { type: String, default: undefined },
})
</script>

<template>
  <section class="band" :data-game="accent">
    <BallMark v-if="accent === 'pokemon'" class="band-ball" outline />
    <div class="container band-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.band {
  position: relative;
  overflow: hidden;
  background: var(--accent);
  color: var(--accent-ink);
}

.band :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.band-ball {
  position: absolute;
  top: -5rem;
  right: -6rem;
  width: 18rem;
  height: 18rem;
  color: var(--accent-ink);
  opacity: 0.16;
  transform: rotate(-18deg);
}

.band-body {
  position: relative;
  padding-block: var(--space-4) var(--space-5);
}
</style>
