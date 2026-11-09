<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, Lock } from 'lucide-vue-next'
import CartTotals from '../../components/cart/CartTotals.vue'
import CountrySelect from '../../components/account/CountrySelect.vue'
import FormField from '../../components/FormField.vue'
import FormMessage from '../../components/FormMessage.vue'
import ProductImage from '../../components/shop/ProductImage.vue'
import { accountApi, cartApi, checkoutApi } from '../../services/api'
import { useCart } from '../../stores/cart'
import { useRegion } from '../../stores/region'
import { useSession } from '../../stores/session'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

const route = useRoute()
const session = useSession()
const region = useRegion()
const cart = useCart()

const user = computed(() => session.state.user)

const options = ref(null)
const addresses = ref([])
/** 'new', or the id of a saved address. */
const chosenAddress = ref('new')
const loading = ref(true)
const loadError = ref('')

const contact = reactive({ name: '', email: '' })
const address = reactive({ street: '', houseNumber: '', postcode: '', city: '', country: region.country.value })
const saveAddress = ref(true)
const shipmentPreference = ref('TOGETHER')
const acceptTerms = ref(false)

const submitting = ref(false)
const error = ref('')

/** The cart priced for the country of the chosen address, which can differ from the region setting. */
const quote = ref(null)

const country = computed(() => {
  if (chosenAddress.value === 'new') return address.country
  return addresses.value.find((saved) => saved.id === chosenAddress.value)?.country ?? address.country
})

const rate = computed(() => options.value?.countries.find((entry) => entry.country === country.value) ?? null)
const mixed = computed(() => quote.value?.hasPreOrders && quote.value?.hasInStock)
const expected = computed(() => {
  const dates = (quote.value?.items ?? []).filter((item) => item.preOrder && item.shipsFrom).map((item) => item.shipsFrom)
  return dates.sort().at(-1) ?? null
})
const canPay = computed(() => options.value?.paymentsEnabled && quote.value?.canCheckout && !submitting.value)

let latestQuote = 0

async function loadQuote() {
  const request = ++latestQuote
  try {
    const result = await cartApi.get(country.value)
    if (request === latestQuote) quote.value = result
  } catch (exception) {
    if (request === latestQuote) error.value = exception.message
  }
}

onMounted(async () => {
  try {
    const [checkoutOptions, saved] = await Promise.all([
      checkoutApi.options(),
      user.value ? accountApi.addresses() : Promise.resolve([]),
    ])
    options.value = checkoutOptions
    addresses.value = saved
    if (saved.length) chosenAddress.value = saved[0].id
    await loadQuote()
  } catch (exception) {
    loadError.value = exception.message
  } finally {
    loading.value = false
  }
})

watch(country, loadQuote)

async function pay() {
  error.value = ''
  if (!acceptTerms.value) {
    error.value = t('checkout.termsRequired')
    return
  }
  submitting.value = true
  try {
    const usingSaved = chosenAddress.value !== 'new'
    const started = await checkoutApi.start({
      addressId: usingSaved ? chosenAddress.value : null,
      address: usingSaved ? null : { ...address },
      saveAddress: Boolean(user.value) && !usingSaved && saveAddress.value,
      name: user.value ? null : contact.name,
      email: user.value ? null : contact.email,
      shipmentPreference: mixed.value ? shipmentPreference.value : null,
      acceptTerms: true,
    })
    window.location.assign(started.redirectUrl)
  } catch (exception) {
    error.value = exception.message
    submitting.value = false
    if (exception.code === 'cart_changed') {
      await Promise.all([loadQuote(), cart.load()])
    }
  }
}

const loginLink = computed(() => ({ path: '/inloggen', query: { redirect: route.fullPath } }))
</script>

<template>
  <div class="container page">
    <router-link class="back" to="/winkelwagen">
      <ChevronLeft :size="18" aria-hidden="true" />
      {{ t('checkout.back') }}
    </router-link>
    <h1>{{ t('checkout.title') }}</h1>

    <p v-if="loading" class="muted" aria-busy="true">{{ t('cart.loading') }}</p>
    <FormMessage v-else-if="loadError">{{ loadError }}</FormMessage>

    <div v-else-if="!quote?.items.length" class="empty">
      <p>{{ t('checkout.empty') }}</p>
      <router-link class="btn btn-primary" to="/producten">{{ t('cart.toShop') }}</router-link>
    </div>

    <form v-else class="layout" novalidate @submit.prevent="pay">
      <ol class="steps">
        <li class="step">
          <h2 class="step-title"><span class="number" aria-hidden="true">1</span>{{ t('checkout.steps.contact') }}</h2>
          <div v-if="user" class="step-body">
            <p>{{ t('checkout.loggedInAs', { name: user.displayName, email: user.email }) }}</p>
            <p class="muted small">{{ t('checkout.loggedInHint') }}</p>
          </div>
          <div v-else class="step-body form-stack">
            <p class="muted small">
              {{ t('checkout.guestIntro') }}
              <router-link :to="loginLink">{{ t('checkout.login') }}</router-link>
            </p>
            <FormField v-model="contact.name" :label="t('checkout.name')" autocomplete="name" />
            <FormField
              v-model="contact.email"
              :label="t('checkout.email')"
              type="email"
              autocomplete="email"
              :hint="t('checkout.emailHint')"
            />
          </div>
        </li>

        <li class="step">
          <h2 class="step-title"><span class="number" aria-hidden="true">2</span>{{ t('checkout.steps.address') }}</h2>
          <div class="step-body form-stack">
            <fieldset v-if="addresses.length" class="choices">
              <legend class="visually-hidden">{{ t('checkout.savedAddresses') }}</legend>
              <label v-for="saved in addresses" :key="saved.id" class="choice">
                <input v-model="chosenAddress" type="radio" name="address" :value="saved.id" />
                <span>
                  <strong>{{ user.displayName }}</strong><br />
                  {{ saved.street }} {{ saved.houseNumber }}, {{ saved.postcode }} {{ saved.city }},
                  {{ t(`countries.${saved.country}`) }}
                </span>
              </label>
              <label class="choice">
                <input v-model="chosenAddress" type="radio" name="address" value="new" />
                <span>{{ t('checkout.newAddress') }}</span>
              </label>
            </fieldset>

            <div v-if="chosenAddress === 'new'" class="address-fields">
              <div class="row street-row">
                <FormField v-model="address.street" :label="t('account.addresses.street')" autocomplete="address-line1" />
                <FormField v-model="address.houseNumber" :label="t('account.addresses.houseNumber')" autocomplete="address-line2" />
              </div>
              <div class="row city-row">
                <FormField v-model="address.postcode" :label="t('account.addresses.postcode')" autocomplete="postal-code" />
                <FormField v-model="address.city" :label="t('account.addresses.city')" autocomplete="address-level2" />
              </div>
              <CountrySelect v-model="address.country" :label="t('account.addresses.country')" />
              <label v-if="user" class="check">
                <input v-model="saveAddress" type="checkbox" />
                {{ t('checkout.saveAddress') }}
              </label>
            </div>
          </div>
        </li>

        <li class="step">
          <h2 class="step-title"><span class="number" aria-hidden="true">3</span>{{ t('checkout.steps.shipping') }}</h2>
          <div class="step-body form-stack">
            <p v-if="!rate" class="warning">{{ t('cart.noShipping') }}</p>
            <p v-else class="shipping-line">
              <strong>{{ t('cart.shippingTo', { country: t(`countries.${country}`) }) }}</strong>
              <span>
                {{
                  quote.shipping?.priceCents === 0
                    ? t('checkout.shippingFree', { time: rate.deliveryTime })
                    : t('checkout.shippingCost', { price: formatMoney(rate.priceCents, 'EUR'), time: rate.deliveryTime })
                }}
              </span>
            </p>

            <fieldset v-if="mixed" class="choices">
              <legend class="split-title">{{ t('checkout.split.title') }}</legend>
              <p class="muted small">{{ t('checkout.split.intro') }}</p>
              <label class="choice">
                <input v-model="shipmentPreference" type="radio" name="shipment" value="TOGETHER" />
                <span>
                  <strong>{{ t('checkout.split.TOGETHER') }}</strong><br />
                  <span class="muted small">
                    {{ t('checkout.split.TOGETHER_hint', { date: expected ? t('checkout.split.expected', { date: formatDate(expected) }) : '' }) }}
                  </span>
                </span>
              </label>
              <label class="choice">
                <input v-model="shipmentPreference" type="radio" name="shipment" value="IN_STOCK_FIRST" />
                <span>
                  <strong>{{ t('checkout.split.IN_STOCK_FIRST') }}</strong><br />
                  <span class="muted small">{{ t('checkout.split.IN_STOCK_FIRST_hint') }}</span>
                </span>
              </label>
            </fieldset>
          </div>
        </li>

        <li class="step">
          <h2 class="step-title"><span class="number" aria-hidden="true">4</span>{{ t('checkout.steps.confirm') }}</h2>
          <div class="step-body form-stack">
            <FormMessage v-if="!options.paymentsEnabled">{{ t('checkout.paymentsOff') }}</FormMessage>
            <FormMessage v-else-if="!quote.canCheckout">{{ t('checkout.fixCart') }}</FormMessage>
            <label class="check terms">
              <input v-model="acceptTerms" type="checkbox" />
              <span>
                {{ t('checkout.terms') }}
                <router-link to="/info/algemene-voorwaarden" target="_blank">{{ t('checkout.termsLink') }}</router-link>
              </span>
            </label>
            <FormMessage v-if="error">{{ error }}</FormMessage>
            <button class="btn btn-primary btn-block pay" type="submit" :disabled="!canPay">
              <Lock :size="18" aria-hidden="true" />
              {{ submitting ? t('checkout.paying') : `${t('checkout.pay')} ${formatMoney(quote.totalCents, 'EUR')}` }}
            </button>
            <div class="methods">
              <span class="muted small">{{ t('cart.payWith') }}</span>
              <ul class="method-list">
                <li v-for="method in rate?.paymentMethods ?? quote.paymentMethods" :key="method">
                  {{ t(`cart.paymentMethods.${method}`) }}
                </li>
              </ul>
            </div>
            <p class="muted small">{{ t('checkout.reservation', { minutes: options.reservationMinutes }) }}</p>
            <p class="muted small">{{ t('checkout.payNote') }}</p>
          </div>
        </li>
      </ol>

      <aside class="summary">
        <h2 class="summary-title">{{ t('cart.summary') }}</h2>
        <ul class="summary-lines">
          <li v-for="item in quote.items" :key="item.productId" class="summary-line">
            <span class="thumb">
              <ProductImage :src="item.imageUrl" :is-card="item.imageIsCard" :set-logo="item.setLogoUrl" :type="item.type" />
              <span class="badge">{{ item.quantity }}</span>
            </span>
            <span class="summary-name">
              {{ item.name }}
              <span v-if="item.preOrder" class="pre-order">{{ t('shop.badges.preOrder') }}</span>
              <span v-if="item.problem" class="warning">{{ t(`cart.problems.${item.problem}`, { available: item.available }) }}</span>
            </span>
            <span class="summary-amount">{{ item.lineTotalCents !== null ? formatMoney(item.lineTotalCents, 'EUR') : '' }}</span>
          </li>
        </ul>
        <CartTotals
          :subtotal-cents="quote.subtotalCents"
          :shipping-cents="quote.shipping?.priceCents ?? null"
          :total-cents="quote.totalCents"
          :vat-cents="quote.vatCents"
          :country="quote.shipping?.country"
        />
      </aside>
    </form>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-4) 0;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.muted {
  color: var(--color-ink-soft);
}

.small {
  font-size: var(--text-sm);
}

.empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.layout {
  display: grid;
  gap: var(--space-6);
  margin-top: var(--space-5);
}

.steps {
  display: grid;
  gap: var(--space-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.step {
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-line);
}

.step:last-child {
  border-bottom: 0;
}

.step-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-xl);
}

.number {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-round);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-weight: 750;
}

.step-body {
  max-width: 34rem;
  margin-top: var(--space-4);
}

@media (min-width: 640px) {
  .step-body {
    padding-left: calc(2rem + var(--space-3));
  }
}

.choices {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  border: 0;
}

.choice {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--text-sm);
}

.choice:has(input:checked) {
  border-color: var(--color-brand);
  background: var(--color-tint);
}

.choice input,
.check input {
  flex: none;
  width: 1.125rem;
  height: 1.125rem;
  margin: var(--space-half) 0 0;
  accent-color: var(--color-brand);
}

.split-title {
  margin-bottom: var(--space-1);
  font-weight: 700;
}

.address-fields {
  display: grid;
  gap: var(--space-3);
}

.row {
  display: grid;
  gap: var(--space-3);
}

.street-row {
  grid-template-columns: minmax(0, 1fr) 6.5rem;
}

.city-row {
  grid-template-columns: 7.5rem minmax(0, 1fr);
}

.check {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  min-height: var(--touch-target);
  cursor: pointer;
  font-size: var(--text-sm);
}

.terms {
  font-weight: 600;
}

.shipping-line {
  display: grid;
  gap: var(--space-half);
  padding: var(--space-3);
  border: 1.5px solid var(--color-brand);
  border-radius: var(--radius-md);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

.warning {
  color: var(--color-warning);
  font-size: var(--text-sm);
  font-weight: 600;
}

.pay {
  font-size: var(--text-md);
}

.methods {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.method-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.method-list li {
  padding: var(--space-half) var(--space-2);
  border: 1px solid var(--color-line-strong);
  border-radius: var(--radius-xs);
  font-size: var(--text-xs);
  font-weight: 650;
}

/* On a phone the overview comes first, so you see what you pay for before the pay button. */
.summary {
  display: grid;
  align-content: start;
  align-self: start;
  order: -1;
  gap: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.summary-title {
  font-size: var(--text-xl);
}

.summary-lines {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.summary-line {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
  font-size: var(--text-sm);
}

.thumb {
  position: relative;
  display: block;
}

.badge {
  position: absolute;
  top: calc(var(--space-2) * -1);
  right: calc(var(--space-2) * -1);
  min-width: 1.375rem;
  padding-inline: var(--space-1);
  border-radius: var(--radius-round);
  background: var(--color-ink-soft);
  color: var(--color-on-brand);
  font-size: var(--text-2xs);
  font-weight: 700;
  line-height: 1.375rem;
  text-align: center;
}

.summary-name {
  display: grid;
  font-weight: 600;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.pre-order {
  color: var(--color-brand-deep);
  font-size: var(--text-xs);
}

.summary-amount {
  font-weight: 700;
  white-space: nowrap;
}

@media (min-width: 960px) {
  .layout {
    grid-template-columns: minmax(0, 1fr) 24rem;
    gap: var(--space-7);
  }

  .summary {
    position: sticky;
    top: 9rem;
    order: 0;
  }
}
</style>
