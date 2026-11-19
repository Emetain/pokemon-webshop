<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { BadgeCheck, ChartLine, Crown, ScanLine, Sparkles } from 'lucide-vue-next'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import FormMessage from '../components/FormMessage.vue'
import { premiumApi } from '../services/api'
import { useSession } from '../stores/session'
import { formatDate, formatMoney } from '../utils/format'
import { t } from '../utils/i18n'
import { usePageMeta } from '../utils/pageMeta'

/**
 * Premium (phase 11): what it adds, the two plans and Stripe's payment page. Premium only starts once Stripe's webhook
 * says the payment went through, so the page back from Stripe waits for that.
 */
const route = useRoute()
const session = useSession()

const status = ref(null)
const plan = ref('YEARLY')
const consent = ref(false)
const busy = ref(false)
const error = ref('')
const waiting = ref(false)
let poll = null

const POLL_MS = 2000
const POLL_TRIES = 15

const loggedIn = computed(() => session.isLoggedIn.value)
const returnedFromStripe = computed(() => route.query.betaald === '1')

const FEATURES = [
  { icon: ScanLine, key: 'scanner' },
  { icon: ChartLine, key: 'history' },
  { icon: BadgeCheck, key: 'pregrade' },
  { icon: Sparkles, key: 'more' },
]

/** What a year costs against twelve months, in whole percents. */
const saving = computed(() => {
  if (!status.value) return 0
  const yearOfMonths = status.value.monthlyPriceCents * 12
  return Math.round(((yearOfMonths - status.value.yearlyPriceCents) / yearOfMonths) * 100)
})

async function load() {
  if (!loggedIn.value) return
  try {
    status.value = await premiumApi.status()
  } catch (exception) {
    error.value = exception.message
  }
}

/** Back from Stripe: asks a few times until the webhook has switched Premium on. */
async function waitForPremium(tries = POLL_TRIES) {
  waiting.value = true
  await load()
  if (status.value?.premium) {
    waiting.value = false
    session.refresh()
    return
  }
  if (tries <= 1) {
    waiting.value = false
    return
  }
  poll = setTimeout(() => waitForPremium(tries - 1), POLL_MS)
}

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

watch(
  loggedIn,
  () => {
    if (returnedFromStripe.value) waitForPremium()
    else load()
  },
  { immediate: true },
)

onBeforeUnmount(() => clearTimeout(poll))

usePageMeta(() => ({ title: t('premium.title'), description: t('premium.intro') }))
</script>

<template>
  <CatalogBand>
    <p class="eyebrow"><Crown :size="18" aria-hidden="true" /> {{ t('premium.eyebrow') }}</p>
    <h1>{{ t('premium.title') }}</h1>
    <p class="intro">{{ t('premium.intro') }}</p>
  </CatalogBand>

  <div class="container page">
    <ul class="features">
      <li v-for="feature in FEATURES" :key="feature.key" class="feature">
        <component :is="feature.icon" class="feature-icon" :size="22" aria-hidden="true" />
        <div>
          <h2>{{ t(`premium.features.${feature.key}.title`) }}</h2>
          <p>{{ t(`premium.features.${feature.key}.text`) }}</p>
        </div>
      </li>
    </ul>

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

    <section v-if="!loggedIn" class="panel">
      <h2>{{ t('premium.loginTitle') }}</h2>
      <p>{{ t('premium.loginText') }}</p>
      <div class="actions">
        <router-link class="btn btn-primary" :to="{ path: '/inloggen', query: { redirect: '/premium' } }">
          {{ t('premium.login') }}
        </router-link>
        <router-link class="btn btn-secondary" to="/registreren">
          {{ t('premium.register') }}
        </router-link>
      </div>
    </section>

    <section v-else-if="waiting" class="panel" role="status">
      <h2>{{ t('premium.waitingTitle') }}</h2>
      <p>{{ t('premium.waitingText') }}</p>
    </section>

    <section v-else-if="status?.premium" class="panel has-premium">
      <h2><Crown :size="20" aria-hidden="true" /> {{ returnedFromStripe ? t('premium.welcome') : t('premium.active') }}</h2>
      <p v-if="status.reason === 'ADMIN'">{{ t('premium.admin') }}</p>
      <p v-else-if="status.reason === 'FOREVER'">{{ t('premium.forever') }}</p>
      <p v-else-if="status.cancelAtPeriodEnd">{{ t('premium.endsOn', { date: formatDate(status.until) }) }}</p>
      <p v-else-if="status.nextPaymentAt">{{ t('premium.renewsOn', { date: formatDate(status.nextPaymentAt) }) }}</p>
      <router-link class="btn btn-secondary" to="/account#premium">{{ t('premium.manage') }}</router-link>
    </section>

    <section v-else-if="returnedFromStripe" class="panel" role="status">
      <h2>{{ t('premium.pendingTitle') }}</h2>
      <p>{{ t('premium.pendingText') }}</p>
    </section>

    <form v-else-if="status" class="panel buy" @submit.prevent="buy">
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

      <button class="btn btn-primary buy-button" type="submit" :disabled="busy">
        {{ busy ? t('premium.toStripe') : t('premium.buy') }}
      </button>
      <p class="small">
        {{ t('premium.cancelAnytime') }}
        <router-link to="/info/algemene-voorwaarden">{{ t('premium.terms') }}</router-link>
      </p>
      <p v-if="status.freeScansLeft !== null" class="small">
        {{ t('premium.freeScansLeft', { count: status.freeScansLeft, total: status.freeScans }) }}
      </p>
    </form>
  </div>
</template>

<style scoped>
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-brand);
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.intro {
  max-width: 40rem;
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}

.page {
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-6);
}

.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr));
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.feature {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.feature-icon {
  flex: none;
  margin-top: 2px;
  color: var(--color-brand);
}

.feature h2 {
  font-size: var(--text-md);
}

.feature p {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.panel {
  display: grid;
  gap: var(--space-3);
  max-width: 36rem;
  padding: var(--space-5);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.panel h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.has-premium {
  border-color: var(--color-brand);
}

.has-premium .btn {
  justify-self: start;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
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
