<script setup>
import { computed, ref, useId, watch } from 'vue'
import { BellRing } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { priceAlertApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { variantLabel } from '../../utils/catalog'
import { formatDate, formatMoney, parseMoneyInput } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * Price alerts on one card (Premium, phase 12): "mail me when it drops below €…" or "when it rises or falls more
 * than …%", per variant. Without Premium one button that opens the Premium popup.
 */
const props = defineProps({
  card: { type: Object, required: true },
})

const KINDS = ['BELOW', 'CHANGE']

const session = useSession()
const id = useId()
const premium = computed(() => session.isPremium.value)
const currency = computed(() => session.state.user?.currency ?? 'EUR')

const alerts = ref([])
const error = ref('')
const message = ref('')
const saving = ref(false)
const form = ref({ kind: 'BELOW', variant: null, amount: '', percent: '20' })

const variants = computed(() => props.card.variants ?? [])

async function load() {
  alerts.value = []
  error.value = ''
  message.value = ''
  form.value = { kind: 'BELOW', variant: variants.value[0] ?? null, amount: '', percent: '20' }
  if (!premium.value) return
  try {
    alerts.value = (await priceAlertApi.forCard(props.card.id)).alerts
  } catch (exception) {
    error.value = exception.message
  }
}

function describe(alert) {
  if (alert.kind === 'BELOW') return t('priceAlerts.below', { price: formatMoney(alert.thresholdCents, alert.currency) })
  if (alert.baselineCents === null) return t('priceAlerts.changeNoStart', { percent: alert.percent })
  return t('priceAlerts.change', { percent: alert.percent, price: formatMoney(alert.baselineCents, alert.baselineCurrency) })
}

async function save() {
  error.value = ''
  message.value = ''
  const data = { cardId: props.card.id, variant: form.value.variant, kind: form.value.kind }
  if (form.value.kind === 'BELOW') {
    data.thresholdCents = parseMoneyInput(form.value.amount)
    if (!data.thresholdCents) {
      error.value = t('errors.amount')
      return
    }
  } else {
    data.percent = Number.parseInt(form.value.percent, 10)
  }
  saving.value = true
  try {
    await priceAlertApi.save(data)
    message.value = t('priceAlerts.saved')
    form.value.amount = ''
    alerts.value = (await priceAlertApi.forCard(props.card.id)).alerts
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

async function remove(alert) {
  error.value = ''
  message.value = ''
  try {
    await priceAlertApi.remove(alert.id)
    alerts.value = alerts.value.filter((item) => item.id !== alert.id)
    message.value = t('priceAlerts.removed')
  } catch (exception) {
    error.value = exception.message
  }
}

watch([() => props.card.id, premium], load, { immediate: true })
</script>

<template>
  <section class="alerts">
    <h2><BellRing :size="20" aria-hidden="true" /> {{ t('priceAlerts.cardTitle') }}</h2>

    <template v-if="!premium">
      <p class="intro">{{ t('priceAlerts.cardIntro') }}</p>
      <button class="btn btn-secondary" type="button" aria-haspopup="dialog" @click="openPremium('alerts')">
        {{ t('priceAlerts.unlock') }}
      </button>
    </template>

    <template v-else>
      <ul v-if="alerts.length" class="list">
        <li v-for="alert in alerts" :key="alert.id" class="alert">
          <div>
            <p class="what">
              {{ describe(alert) }}
              <span v-if="variants.length > 1" class="variant">· {{ variantLabel(alert.variant) }}</span>
            </p>
            <p v-if="alert.kind === 'BELOW' && !alert.armed" class="note">{{ t('priceAlerts.waiting') }}</p>
            <p v-else-if="alert.lastTriggeredOn" class="note">
              {{ t('priceAlerts.lastTriggered', { date: formatDate(alert.lastTriggeredOn) }) }}
            </p>
          </div>
          <button class="link" type="button" @click="remove(alert)">{{ t('priceAlerts.remove') }}</button>
        </li>
      </ul>

      <form class="form" @submit.prevent="save">
        <fieldset class="kinds">
          <legend>{{ t('priceAlerts.kind') }}</legend>
          <label v-for="kind in KINDS" :key="kind" class="kind">
            <input v-model="form.kind" type="radio" :name="`${id}-kind`" :value="kind" />
            <span>
              {{ t(`priceAlerts.kinds.${kind}`) }}
              <small>{{ t(`priceAlerts.hints.${kind}`) }}</small>
            </span>
          </label>
        </fieldset>

        <div class="fields">
          <div v-if="variants.length > 1" class="field">
            <label :for="`${id}-variant`">{{ t('priceAlerts.variant') }}</label>
            <select :id="`${id}-variant`" v-model="form.variant" class="input">
              <option v-for="variant in variants" :key="variant" :value="variant">{{ variantLabel(variant) }}</option>
            </select>
          </div>
          <div v-if="form.kind === 'BELOW'" class="field">
            <label :for="`${id}-amount`">{{ t('priceAlerts.threshold', { currency }) }}</label>
            <input :id="`${id}-amount`" v-model="form.amount" class="input" inputmode="decimal" autocomplete="off" placeholder="10,00" />
          </div>
          <div v-else class="field">
            <label :for="`${id}-percent`">{{ t('priceAlerts.percent') }}</label>
            <input :id="`${id}-percent`" v-model="form.percent" class="input" type="number" min="1" max="1000" step="1" />
          </div>
          <button class="btn btn-primary" type="submit" :disabled="saving">{{ t('priceAlerts.save') }}</button>
        </div>
      </form>

      <FormMessage v-if="error">{{ error }}</FormMessage>
      <FormMessage v-if="message" type="success">{{ message }}</FormMessage>
      <router-link class="all" to="/prijsalerts">{{ t('priceAlerts.toAll') }}</router-link>
    </template>
  </section>
</template>

<style scoped>
.alerts {
  margin-top: var(--space-6);
}

.alerts h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.intro {
  margin-block: var(--space-2) var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.list {
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.what {
  font-weight: 600;
}

.variant {
  color: var(--color-ink-soft);
  font-weight: 400;
}

.note {
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

.form {
  margin-top: var(--space-3);
}

.kinds {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  border: 0;
}

.kinds legend {
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
}

.kind {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  font-size: var(--text-sm);
  cursor: pointer;
}

.kind input {
  margin-top: 0.2rem;
  accent-color: var(--color-brand);
}

.kind small {
  display: block;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.fields {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.field {
  display: grid;
  gap: var(--space-1);
  font-size: var(--text-sm);
}

.field label {
  font-size: var(--text-xs);
  font-weight: 600;
}

.input {
  width: 9rem;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-md);
}

.all {
  display: inline-block;
  margin-top: var(--space-2);
  font-size: var(--text-sm);
}
</style>
