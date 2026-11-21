<script setup>
import { computed, ref, watch } from 'vue'
import { ChartLine, Lock } from 'lucide-vue-next'
import ValueChart from '../collection/ValueChart.vue'
import { catalogApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { variantLabel } from '../../utils/catalog'
import { formatShortDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The price graph of a card (Premium, phase 12): per variant, with the periods and the morph of the value graph.
 * Without Premium a locked graph with a line about it; the button opens the Premium popup.
 */
const props = defineProps({
  card: { type: Object, required: true },
})

const session = useSession()
const premium = computed(() => session.isPremium.value)

const history = ref(null)
const loading = ref(false)
const error = ref('')
const variant = ref(null)

const variants = computed(() => history.value?.variants ?? [])
const shown = computed(() => variants.value.find((item) => item.variant === variant.value) ?? variants.value[0] ?? null)
const sourceNote = computed(() =>
  shown.value?.points.length
    ? t('priceHistory.source', {
        source: t(`catalog.priceSources.${shown.value.source}`),
        date: formatShortDate(shown.value.points[0].date),
      })
    : '',
)

let latest = 0

async function load() {
  history.value = null
  error.value = ''
  if (!premium.value) return
  const request = ++latest
  loading.value = true
  try {
    const result = await catalogApi.cardHistory(props.card.id)
    if (request !== latest) return
    history.value = result
    variant.value = result.variants[0]?.variant ?? null
  } catch (exception) {
    if (request === latest) error.value = exception.message
  } finally {
    if (request === latest) loading.value = false
  }
}

watch([() => props.card.id, premium], load, { immediate: true })
</script>

<template>
  <section class="price-history">
    <h2><ChartLine :size="20" aria-hidden="true" /> {{ t('priceHistory.title') }}</h2>

    <div v-if="!premium" class="locked">
      <svg class="sketch" viewBox="0 0 100 40" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 30 L12 27 L22 29 L34 20 L46 23 L58 14 L70 17 L82 9 L100 12" />
      </svg>
      <div class="locked-text">
        <p><Lock :size="16" aria-hidden="true" /> {{ t('priceHistory.locked.text') }}</p>
        <button class="btn btn-primary" type="button" aria-haspopup="dialog" @click="openPremium('cardHistory')">
          {{ t('priceHistory.locked.action') }}
        </button>
      </div>
    </div>

    <div v-else-if="loading" class="chart-skeleton" aria-busy="true">
      <p class="visually-hidden" role="status">{{ t('priceHistory.loading') }}</p>
      <span class="skeleton line" aria-hidden="true"></span>
      <span class="skeleton plot" aria-hidden="true"></span>
    </div>
    <p v-else-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="history && !variants.length" class="note">{{ t('priceHistory.none') }}</p>

    <template v-else-if="shown">
      <div v-if="variants.length > 1" class="variants" role="group" :aria-label="t('priceHistory.variant')">
        <button
          v-for="item in variants"
          :key="item.variant"
          class="variant"
          type="button"
          :aria-pressed="item.variant === shown.variant"
          @click="variant = item.variant"
        >
          {{ variantLabel(item.variant) }}
        </button>
      </div>
      <ValueChart :points="shown.points" :currency="history.currency" premium text-key="priceHistory.chart" />
      <p class="note">{{ sourceNote }}</p>
    </template>
  </section>
</template>

<style scoped>
.price-history {
  margin-top: var(--space-6);
}

.price-history h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.locked {
  position: relative;
  display: grid;
  margin-top: var(--space-3);
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.sketch {
  grid-area: 1 / 1;
  width: 100%;
  height: 9rem;
  opacity: 0.35;
  pointer-events: none;
}

.sketch path {
  fill: none;
  stroke: var(--color-line-strong);
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
}

.locked-text {
  position: relative;
  display: grid;
  grid-area: 1 / 1;
  place-content: center;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  text-align: center;
}

.locked-text p {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 34ch;
  font-size: var(--text-sm);
  font-weight: 600;
}

.chart-skeleton {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.chart-skeleton .line {
  width: 40%;
  height: 1.1rem;
}

.chart-skeleton .plot {
  height: 13rem;
  border-radius: var(--radius-md);
}

.variants {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.variant {
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.variant[aria-pressed='true'] {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.note {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.error {
  margin-top: var(--space-2);
  color: var(--color-danger);
  font-size: var(--text-sm);
}
</style>
