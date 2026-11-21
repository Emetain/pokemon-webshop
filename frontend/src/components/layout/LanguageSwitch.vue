<script setup>
import { LANGUAGES, language, setLanguage } from '../../stores/language'

/**
 * Nederlands or English (2026-11-21). Each language is named in itself, so a visitor who doesn't read the current one
 * still finds theirs. Switching reloads the page in the other language.
 */
const NAMES = { nl: 'Nederlands', en: 'English' }
</script>

<template>
  <div class="language-switch" role="group" aria-label="Taal / Language">
    <button
      v-for="code in LANGUAGES"
      :key="code"
      class="code"
      type="button"
      :lang="code"
      :aria-label="NAMES[code]"
      :aria-pressed="code === language"
      @click="setLanguage(code)"
    >
      {{ code.toUpperCase() }}
    </button>
  </div>
</template>

<style scoped>
.language-switch {
  display: inline-flex;
  gap: var(--space-half);
}

.code {
  min-width: 2rem;
  min-height: 1.75rem;
  padding: 0 var(--space-1);
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  font-weight: 650;
  opacity: 0.75;
}

.code[aria-pressed='true'] {
  background: color-mix(in srgb, currentColor 22%, transparent);
  opacity: 1;
}

@media (hover: hover) {
  .code:hover {
    opacity: 1;
  }
}
</style>
