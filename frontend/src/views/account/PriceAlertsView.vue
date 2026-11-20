<script setup>
import { onMounted, ref } from 'vue'
import { BellRing } from 'lucide-vue-next'
import CardImage from '../../components/catalog/CardImage.vue'
import FormMessage from '../../components/FormMessage.vue'
import { priceAlertApi } from '../../services/api'
import { useCurrency } from '../../stores/currency'
import { variantLabel } from '../../utils/catalog'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** All price alerts of a customer (Premium, phase 12). After Premium ended they're listed and can be switched off. */
const { formatPrice } = useCurrency()

const list = ref(null)
const error = ref('')
const message = ref('')

async function load() {
  error.value = ''
  try {
    list.value = await priceAlertApi.list()
  } catch (exception) {
    error.value = exception.message
  }
}

async function remove(alert) {
  error.value = ''
  message.value = ''
  try {
    await priceAlertApi.remove(alert.id)
    list.value.alerts = list.value.alerts.filter((item) => item.id !== alert.id)
    message.value = t('priceAlerts.removed')
  } catch (exception) {
    error.value = exception.message
  }
}

function describe(alert) {
  if (alert.kind === 'BELOW') return t('priceAlerts.below', { price: formatMoney(alert.thresholdCents, alert.currency) })
  if (alert.baselineCents === null) return t('priceAlerts.changeNoStart', { percent: alert.percent })
  return t('priceAlerts.change', { percent: alert.percent, price: formatMoney(alert.baselineCents, alert.baselineCurrency) })
}

const cardLink = (card) => ({ name: 'card', params: { game: card.game, setCode: card.setCode, cardId: card.id } })

onMounted(load)
</script>

<template>
  <div class="container page">
    <h1><BellRing :size="26" aria-hidden="true" /> {{ t('priceAlerts.title') }}</h1>
    <p class="intro">{{ t('priceAlerts.intro') }}</p>

    <FormMessage v-if="error">{{ error }}</FormMessage>
    <FormMessage v-if="message" type="success">{{ message }}</FormMessage>
    <p v-if="!list && !error" class="note" role="status">{{ t('priceAlerts.loading') }}</p>

    <template v-if="list">
      <p v-if="!list.premium && list.alerts.length" class="notice">{{ t('priceAlerts.paused') }}</p>
      <p v-if="!list.alerts.length" class="note">{{ t('priceAlerts.empty') }}</p>

      <ul v-else class="alerts">
        <li v-for="alert in list.alerts" :key="alert.id" class="alert">
          <router-link class="thumb" :to="cardLink(alert.card)" tabindex="-1" aria-hidden="true">
            <CardImage :src="alert.card.imageUrl" :alt="''" />
          </router-link>
          <div class="text">
            <router-link class="name" :to="cardLink(alert.card)">{{ alert.card.name }}</router-link>
            <p class="meta">{{ alert.card.setName }} · {{ alert.card.number }} · {{ variantLabel(alert.variant) }}</p>
            <p class="what">{{ describe(alert) }}</p>
            <p class="meta">
              {{
                alert.priceCents === null
                  ? t('priceAlerts.noPrice')
                  : t('priceAlerts.current', { price: formatPrice(alert.priceCents, alert.priceCurrency) })
              }}
              <template v-if="alert.kind === 'BELOW' && !alert.armed"> · {{ t('priceAlerts.waiting') }}</template>
              <template v-else-if="alert.lastTriggeredOn">
                · {{ t('priceAlerts.lastTriggered', { date: formatDate(alert.lastTriggeredOn) }) }}
              </template>
            </p>
          </div>
          <button class="link" type="button" @click="remove(alert)">{{ t('priceAlerts.remove') }}</button>
        </li>
      </ul>
      <p v-if="list.premium && list.alerts.length" class="note">{{ t('priceAlerts.limit', { max: list.max }) }}</p>
    </template>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-6) var(--space-7);
}

h1 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.intro {
  max-width: 65ch;
  margin-block: var(--space-2) var(--space-4);
  color: var(--color-ink-soft);
}

.notice {
  max-width: 65ch;
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-sm);
}

.alerts {
  max-width: 48rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.alert {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-3);
  border-bottom: 1px solid var(--color-line);
}

.thumb {
  flex: none;
  width: 3.25rem;
}

.text {
  flex: 1;
  min-width: 0;
}

.name {
  color: var(--color-ink);
  font-weight: 650;
  text-decoration: none;
}

@media (hover: hover) {
  .name:hover {
    color: var(--color-brand-ink);
    text-decoration: underline;
  }
}

.what {
  font-size: var(--text-sm);
  font-weight: 600;
}

.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.link {
  flex: none;
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.note {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
