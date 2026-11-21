<script setup>
import { useNavigation } from '../../stores/navigation'

/** A thin bar along the top while the next page is on its way, on shop and admin pages alike. */
const navigation = useNavigation()
</script>

<template>
  <div v-if="navigation.slow" class="progress" aria-hidden="true"><span class="fill"></span></div>
</template>

<style scoped>
.progress {
  position: fixed;
  inset: 0 0 auto;
  z-index: calc(var(--header-layer) + 10);
  height: 3px;
  pointer-events: none;
}

/* Fast at first, then slower and slower: it never claims to be done before the page is there. */
.fill {
  display: block;
  height: 100%;
  background: var(--color-brand);
  transform: scaleX(0);
  transform-origin: left;
  animation: grow 10s cubic-bezier(0.1, 0.7, 0.1, 1) forwards;
}

@keyframes grow {
  to {
    transform: scaleX(0.94);
  }
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    opacity: 0.6;
    transform: scaleX(1);
    animation: none;
  }
}
</style>
