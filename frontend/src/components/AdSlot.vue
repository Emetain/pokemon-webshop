<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useAds } from '../stores/ads'
import { t } from '../utils/i18n'

/**
 * One ad from Google AdSense, for the free card and news pages only: never on shop, cart, checkout, drop or account
 * pages. Shows nothing while ads are off or this place has no ad unit. The space is kept before the ad arrives, so
 * the page doesn't jump, and it says "Advertentie" above it.
 */
const props = defineProps({
  /** Which ad unit: `catalog` (card lists and card pages) or `content` (news). */
  place: { type: String, required: true },
})

const ads = useAds()
const unit = ref(null)
const slot = computed(() => (ads.state.enabled ? ads.state.slots[props.place] : null))
const development = import.meta.env.DEV
let requested = false

async function request() {
  if (!slot.value || requested || development) return
  await nextTick()
  if (!unit.value) return
  requested = true
  ads.ensureScript()
  try {
    // Google's script picks this up, also when it loads later.
    ;(window.adsbygoogle = window.adsbygoogle || []).push({})
  } catch {
    // An ad that doesn't load leaves its space empty.
  }
}

onMounted(async () => {
  await ads.load()
  request()
})
watch(slot, request)
</script>

<template>
  <aside v-if="slot" class="ad" :aria-label="t('ads.label')">
    <p class="ad-label">{{ t('ads.label') }}</p>
    <div v-if="development" class="ad-placeholder">{{ t('ads.placeholder', { place }) }}</div>
    <ins
      v-else
      ref="unit"
      class="adsbygoogle ad-unit"
      :data-ad-client="ads.state.client"
      :data-ad-slot="slot"
      data-ad-format="auto"
      data-full-width-responsive="true"
    ></ins>
  </aside>
</template>

<style scoped>
.ad {
  margin-block: var(--space-6) var(--space-4);
}

.ad-label {
  margin-bottom: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.ad-unit,
.ad-placeholder {
  display: block;
  min-height: 250px;
}

.ad-placeholder {
  display: grid;
  place-items: center;
  border: 1px dashed var(--color-line-strong);
  border-radius: var(--radius-sm);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
