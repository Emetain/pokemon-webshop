<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Crown } from 'lucide-vue-next'
import CatalogBand from '../components/catalog/CatalogBand.vue'
import FormMessage from '../components/FormMessage.vue'
import PremiumBuyForm from '../components/premium/PremiumBuyForm.vue'
import PremiumFeatureList from '../components/premium/PremiumFeatureList.vue'
import { premiumApi } from '../services/api'
import { useSession } from '../stores/session'
import { formatDate } from '../utils/format'
import { t } from '../utils/i18n'
import { usePageMeta } from '../utils/pageMeta'

/**
 * Premium (phase 11): what it adds, the two plans and Stripe's payment page. Premium only starts once Stripe's webhook
 * says the payment went through, so the page back from Stripe waits for that.
 */
const route = useRoute()
const session = useSession()

const status = ref(null)
const error = ref('')
const waiting = ref(false)
let poll = null

const POLL_MS = 2000
const POLL_TRIES = 15

const loggedIn = computed(() => session.isLoggedIn.value)
const returnedFromStripe = computed(() => route.query.betaald === '1')

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
    <PremiumFeatureList />

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

    <PremiumBuyForm v-else-if="status" class="panel" :status="status" />
  </div>
</template>

<style scoped>
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  opacity: 0.85;
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* The band sets the text colour for its accent. */
.intro {
  max-width: 40rem;
  margin-top: var(--space-2);
}

.page {
  display: grid;
  gap: var(--space-5);
  padding-block: var(--space-6);
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

</style>
