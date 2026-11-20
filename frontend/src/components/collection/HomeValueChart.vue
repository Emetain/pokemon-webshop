<script setup>
import { computed, ref, watch } from 'vue'
import ValueChart from './ValueChart.vue'
import { collectionApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The value graph of the collection page, on the home page too (the owner's wish, 2026-11-20). Only for a logged-in
 * customer whose collection has a value history; everyone else sees nothing here.
 */
const session = useSession()
const history = ref(null)

watch(
  () => session.isLoggedIn.value,
  async (loggedIn) => {
    history.value = null
    if (!loggedIn) return
    try {
      history.value = await collectionApi.history()
    } catch {
      // The home page simply goes without the graph.
    }
  },
  { immediate: true },
)

const latest = computed(() => history.value?.points?.at(-1) ?? null)
</script>

<template>
  <section v-if="latest" class="home-value" aria-labelledby="home-value">
    <div class="head">
      <div>
        <h2 id="home-value" class="label">{{ t('home.value.title') }}</h2>
        <p class="value">{{ formatMoney(latest.cents, history.currency) }}</p>
      </div>
      <router-link class="all" to="/collectie">{{ t('home.value.toCollection') }}</router-link>
    </div>
    <ValueChart :points="history.points" :currency="history.currency" :premium="history.premium" :has-older="history.hasOlder" />
  </section>
</template>

<style scoped>
.home-value {
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.label {
  color: var(--color-ink-soft);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 650;
}

.value {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-stretch: 88%;
  font-weight: 800;
  line-height: var(--leading-tight);
}

.all {
  font-size: var(--text-sm);
  font-weight: 600;
  white-space: nowrap;
}

@media (min-width: 768px) {
  .home-value {
    padding: var(--space-5);
  }
}
</style>
