<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Cookie } from 'lucide-vue-next'
import { useAds } from '../../stores/ads'
import { t } from '../../utils/i18n'

/**
 * Tells visitors once which cookies the shop uses. The shop's own are all functional, so nothing waits for consent and
 * there is nothing to choose. The "seen" mark lives in localStorage, not in another cookie. With ads on, the text
 * says so: Google asks for consent itself, on the pages that have an ad.
 */
const ads = useAds()
onMounted(() => ads.load())

const STORAGE_KEY = 'poketopia.cookie-notice'

function seen() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

const visible = ref(!seen())
const box = ref(null)

/**
 * The room the notice takes at the bottom of the screen, as `--cookie-notice-space` on the page, so the chat button
 * can sit above it instead of under it.
 */
let observer = null
const GAP = 8

function publish(space) {
  document.documentElement.style.setProperty('--cookie-notice-space', `${space}px`)
}

watch(box, (element) => {
  observer?.disconnect()
  observer = null
  if (!element) {
    publish(0)
    return
  }
  if (typeof ResizeObserver === 'undefined') return publish(element.offsetHeight + GAP)
  observer = new ResizeObserver(() => publish(element.offsetHeight + GAP))
  observer.observe(element)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  publish(0)
})

function close() {
  visible.value = false
  try {
    window.localStorage.setItem(STORAGE_KEY, '1')
  } catch {
    // Private mode: the notice comes back on the next visit, which is fine.
  }
}
</script>

<template>
  <section v-if="visible" ref="box" class="cookie-notice" :aria-label="t('cookies.label')">
    <Cookie class="icon" :size="22" aria-hidden="true" />
    <p class="text">
      {{ ads.state.enabled ? t('cookies.textWithAds') : t('cookies.text') }}
      <router-link to="/info/privacybeleid">{{ t('cookies.more') }}</router-link>
    </p>
    <button class="btn btn-primary btn-small" type="button" @click="close">{{ t('cookies.ok') }}</button>
  </section>
</template>

<style scoped>
.cookie-notice {
  position: fixed;
  right: var(--gutter);
  bottom: var(--gutter);
  left: var(--gutter);
  z-index: var(--header-layer);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2) var(--space-3);
  align-items: center;
  max-width: 34rem;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
  font-size: var(--text-sm);
}

.icon {
  color: var(--color-brand);
}

.btn {
  grid-column: 1 / -1;
  justify-self: end;
}

@media (min-width: 560px) {
  .cookie-notice {
    right: auto;
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .btn {
    grid-column: auto;
  }
}
</style>
