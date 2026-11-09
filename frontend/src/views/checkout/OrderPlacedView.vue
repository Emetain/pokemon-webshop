<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { CircleCheck, Hourglass } from 'lucide-vue-next'
import FormField from '../../components/FormField.vue'
import FormMessage from '../../components/FormMessage.vue'
import OrderDetails from '../../components/order/OrderDetails.vue'
import { orderApi } from '../../services/api'
import { useCart } from '../../stores/cart'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * Where Stripe sends the customer after paying. This page never changes the order: only Stripe's webhook makes it
 * paid. Until it is, the page asks again every few seconds.
 */
const props = defineProps({
  order: { type: String, default: undefined },
  token: { type: String, default: undefined },
})

const session = useSession()
const cart = useCart()

const current = ref(null)
const notFound = ref(false)
const slow = ref(false)
const error = ref('')

const account = reactive({ password: '', saving: false, error: '', created: false })

const POLL_EVERY_MS = 2000
const SLOW_AFTER_MS = 30000
let timer = null
let startedAt = 0

const paid = computed(() => current.value?.paidAt != null)
const waiting = computed(() => current.value?.status === 'PENDING_PAYMENT')

async function load() {
  if (!props.order) {
    notFound.value = true
    return
  }
  try {
    current.value = await orderApi.get(props.order, props.token)
    error.value = ''
  } catch (exception) {
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  }
}

async function poll() {
  await load()
  if (waiting.value) {
    slow.value = Date.now() - startedAt > SLOW_AFTER_MS
    timer = window.setTimeout(poll, slow.value ? POLL_EVERY_MS * 5 : POLL_EVERY_MS)
  } else if (paid.value) {
    // The webhook took what was bought out of the cart.
    cart.load()
  }
}

async function createAccount() {
  account.error = ''
  account.saving = true
  try {
    session.state.user = await orderApi.createAccount(props.order, props.token, account.password)
    account.created = true
    account.password = ''
    await load()
  } catch (exception) {
    account.error = exception.message
  } finally {
    account.saving = false
  }
}

onMounted(() => {
  startedAt = Date.now()
  poll()
})

onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<template>
  <div class="container page">
    <div v-if="notFound" class="notice">
      <h1>{{ t('orders.placed.title') }}</h1>
      <p>{{ t('orders.placed.notFound') }}</p>
    </div>

    <template v-else-if="current">
      <header class="head" :class="{ done: paid }">
        <CircleCheck v-if="paid" class="icon" :size="40" aria-hidden="true" />
        <Hourglass v-else-if="waiting" class="icon" :size="40" aria-hidden="true" />
        <div aria-live="polite">
          <h1>{{ paid ? t('orders.placed.title') : waiting ? t('orders.placed.waitingTitle') : t('orders.statuses.CANCELLED') }}</h1>
          <p class="number">{{ t('orders.placed.number', { number: current.number }) }}</p>
          <p v-if="paid" class="lead">{{ t('orders.placed.paid', { email: current.email }) }}</p>
          <p v-else-if="waiting" class="lead">{{ slow ? t('orders.placed.slow') : t('orders.placed.waiting') }}</p>
          <p v-else class="lead">{{ t('orders.placed.cancelled') }}</p>
        </div>
      </header>
      <FormMessage v-if="error">{{ error }}</FormMessage>

      <section v-if="current.canCreateAccount || account.created" class="account">
        <FormMessage v-if="account.created" type="success">{{ t('orders.account.created') }}</FormMessage>
        <form v-else class="account-form" novalidate @submit.prevent="createAccount">
          <h2 class="account-title">{{ t('orders.account.title') }}</h2>
          <p class="muted">{{ t('orders.account.intro', { email: current.email }) }}</p>
          <FormMessage v-if="account.error">{{ account.error }}</FormMessage>
          <FormField
            v-model="account.password"
            :label="t('orders.account.password')"
            type="password"
            autocomplete="new-password"
            :hint="t('orders.account.passwordHint')"
          />
          <div>
            <button class="btn btn-primary" type="submit" :disabled="account.saving">{{ t('orders.account.create') }}</button>
          </div>
        </form>
      </section>

      <OrderDetails class="details" :order="current" />

      <div class="buttons">
        <router-link v-if="session.isLoggedIn.value && !current.guest" class="btn btn-secondary" to="/account/bestellingen">
          {{ t('orders.placed.toAccount') }}
        </router-link>
        <router-link class="btn btn-primary" to="/">{{ t('orders.placed.toShop') }}</router-link>
      </div>
    </template>

    <p v-else-if="error" class="notice">{{ error }}</p>
    <p v-else class="muted" aria-busy="true">{{ t('orders.placed.waiting') }}</p>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-6) 0;
}

.notice {
  display: grid;
  gap: var(--space-3);
  max-width: 40rem;
}

.muted {
  color: var(--color-ink-soft);
}

.head {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
  margin-bottom: var(--space-5);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-tint);
}

.head.done {
  background: var(--color-success-soft);
}

.icon {
  flex: none;
  color: var(--color-brand);
}

.done .icon {
  color: var(--color-success);
}

.number {
  margin-top: var(--space-1);
  font-weight: 700;
}

.lead {
  max-width: 60ch;
  margin-top: var(--space-2);
}

.account {
  max-width: 34rem;
  margin-bottom: var(--space-6);
}

.account-form {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-md);
}

.account-title {
  font-size: var(--text-lg);
}

.details {
  margin-top: var(--space-2);
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-6);
}
</style>
