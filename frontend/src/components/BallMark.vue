<script setup>
defineProps({
  outline: { type: Boolean, default: false },
})
</script>

<template>
  <svg class="ball" :class="{ outline }" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
    <path class="ball-top" d="M4 50a46 46 0 0 1 92 0z" />
    <path class="ball-bottom" d="M4 50a46 46 0 0 0 92 0z" />
    <circle class="ball-line" cx="50" cy="50" r="46" />
    <path class="ball-line" d="M4 50h30M66 50h30" />
    <circle class="ball-button" cx="50" cy="50" r="16" />
    <circle class="ball-core" cx="50" cy="50" r="7" />
  </svg>
</template>

<style scoped>
.ball {
  --ball-line: var(--color-ink);
  display: block;
}

.ball-top {
  fill: var(--color-brand);
}

.ball-bottom,
.ball-button {
  fill: var(--color-surface);
}

.ball-line,
.ball-button,
.ball-core {
  stroke: var(--ball-line);
  stroke-width: 6;
}

.ball-line {
  fill: none;
}

.ball-core {
  fill: var(--color-surface);
  stroke-width: 4;
}

.outline .ball-top,
.outline .ball-bottom,
.outline .ball-button,
.outline .ball-core {
  fill: none;
}

.outline .ball-top,
.outline .ball-bottom {
  stroke: none;
}

.outline .ball-line,
.outline .ball-button,
.outline .ball-core {
  stroke: currentColor;
  stroke-width: 1.4;
}
</style>
