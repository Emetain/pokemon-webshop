<script setup>
import { computed, ref } from 'vue'
import FormMessage from '../FormMessage.vue'
import { premiumApi } from '../../services/api'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The two plans, the consent for a digital service and the way to Stripe's payment page (phase 11). Used on the
 * Premium page and in the Premium popup. `status` is the answer of `premiumApi.status()`.
 */
const props = defineProps({
  status: { type: Object, required: true },
})

const plan = ref('YEARLY')
const consent = ref(false)
const busy = ref(false)
const error = ref('')

/** What a year costs against twelve months, in whole percents. */
const saving = computed(() => {
  const yearOfMonths = props.status.monthlyPriceCents * 12
  return Math.round(((yearOfMonths - props.status.yearlyPriceCents) / yearOfMonths) * 100)
})

async function buy() {
  error.value = ''
  if (!consent.value) {
    error.value = t('premium.consentNeeded')
    return
  }
  busy.value = true
  try {
    const { url } = await premiumApi.checkout(plan.value, true)
    window.location.assign(url)
  } catch (exception) {
    error.value = exception.message
    busy.value = false
  }
}
</script>

<template>
  <form class="buy" @submit.prevent="buy">
    <fieldset class="plans">
      <legend>{{ t('premium.choose') }}</legend>
      <label class="plan" :class="{ chosen: plan === 'MONTHLY' }">
        <input v-model="plan" type="radio" name="plan" value="MONTHLY" />
        <span class="plan-name">{{ t('premium.monthly') }}</span>
        <span class="plan-price">{{ formatMoney(status.monthlyPriceCents, 'EUR') }}</span>
        <span class="plan-note">{{ t('premium.perMonth') }}</span>
      </label>
      <label class="plan" :class="{ chosen: plan === 'YEARLY' }">
        <input v-model="plan" type="radio" name="plan" value="YEARLY" />
        <span class="plan-name">
          {{ t('premium.yearly') }}
          <span v-if="saving > 0" class="badge">{{ t('premium.save', { percent: saving }) }}</span>
        </span>
        <span class="plan-price">{{ formatMoney(status.yearlyPriceCents, 'EUR') }}</span>
        <span class="plan-note">{{ t('premium.perYear') }}</span>
      </label>
    </fieldset>
    <p class="vat">{{ t('premium.vat') }}</p>

    <label class="consent">
      <input v-model="consent" type="checkbox" />
      <span>{{ t('premium.consent') }}</span>
    </label>

    <FormMessage v-if="error">{{ error }}</FormMessage>

    <button class="btn btn-primary buy-button" type="submit" :disabled="busy">
      {{ busy ? t('premium.toStripe') : t('premium.buy') }}
    </button>
    <p class="small">
      {{ t('premium.cancelAnytime') }}
      <router-link to="/info/algemene-voorwaarden">{{ t('premium.terms') }}</router-link>
    </p>
    <p v-if="status.freeScansLeft !== null && status.freeScansLeft !== undefined" class="small">
      {{ t('premium.freeScansLeft', { count: status.freeScansLeft, total: status.freeScans }) }}
    </p>
  </form>
</template>

<style scoped>
.buy {
  display: grid;
  gap: var(--space-3);
}

.plans {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  border: 0;
}

.plans legend {
  margin-bottom: var(--space-3);
  font-weight: 700;
}

.plan {
  position: relative;
  display: grid;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 150ms ease-out, background-color 150ms ease-out;
}

.plan input {
  position: absolute;
  inset: var(--space-3) var(--space-3) auto auto;
  accent-color: var(--color-brand);
}

.plan.chosen {
  border-color: var(--color-brand);
  background: var(--color-tint);
}

.plan:has(input:focus-visible) {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.plan-name {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  font-weight: 700;
}

.badge {
  padding: 0 var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: var(--text-xs);
  font-weight: 700;
}

.plan-price {
  font-size: var(--text-2xl);
  font-weight: 800;
}

.plan-note,
.vat,
.small {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.consent {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  font-size: var(--text-sm);
}

.consent input {
  flex: none;
  width: 1.1rem;
  height: 1.1rem;
  margin-top: 2px;
  accent-color: var(--color-brand);
}

.buy-button {
  justify-self: start;
  min-width: 14rem;
}

@media (prefers-reduced-motion: reduce) {
  .plan {
    transition: none;
  }
}
</style>
