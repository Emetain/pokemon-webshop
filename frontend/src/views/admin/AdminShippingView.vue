<script setup>
import { onMounted, ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import { centsToEuros, eurosToCents } from '../../data/shop'
import { adminOrderApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

const rates = ref([])
const error = ref('')
const loading = ref(true)

function form(rate) {
  return {
    country: rate.country,
    price: centsToEuros(rate.priceCents),
    freeFrom: centsToEuros(rate.freeFromCents),
    deliveryTime: rate.deliveryTime,
    active: rate.active,
    updatedAt: rate.updatedAt,
    saving: false,
    saved: false,
    error: '',
  }
}

onMounted(async () => {
  try {
    rates.value = (await adminOrderApi.shippingRates()).map(form)
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
})

async function save(rate) {
  rate.error = ''
  rate.saved = false
  const priceCents = eurosToCents(rate.price)
  const freeFromCents = eurosToCents(rate.freeFrom)
  if (priceCents === null || (rate.freeFrom.trim() && freeFromCents === null)) {
    rate.error = t('admin.shipping.price')
    return
  }
  rate.saving = true
  try {
    const saved = await adminOrderApi.updateShippingRate(rate.country, {
      priceCents,
      freeFromCents,
      deliveryTime: rate.deliveryTime,
      active: rate.active,
    })
    Object.assign(rate, form(saved), { saved: true })
  } catch (exception) {
    rate.error = exception.message
  } finally {
    rate.saving = false
  }
}
</script>

<template>
  <h1>{{ t('admin.shipping.title') }}</h1>
  <p class="admin-intro">{{ t('admin.shipping.intro') }}</p>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <p v-else-if="loading" class="admin-intro">{{ t('cart.loading') }}</p>

  <form v-for="rate in rates" :key="rate.country" class="admin-panel" novalidate @submit.prevent="save(rate)">
    <h2>{{ t(`countries.${rate.country}`) }}</h2>
    <p class="admin-hint">{{ t('admin.shipping.updated', { date: formatDateTime(rate.updatedAt) }) }}</p>
    <FormMessage v-if="rate.error" class="message">{{ rate.error }}</FormMessage>
    <FormMessage v-if="rate.saved" class="message" type="success">{{ t('admin.shipping.saved') }}</FormMessage>
    <div class="admin-fields">
      <div class="admin-field">
        <label class="admin-label" :for="`price-${rate.country}`">{{ t('admin.shipping.price') }}</label>
        <input :id="`price-${rate.country}`" v-model="rate.price" class="admin-input" inputmode="decimal" />
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="`free-${rate.country}`">{{ t('admin.shipping.freeFrom') }}</label>
        <input :id="`free-${rate.country}`" v-model="rate.freeFrom" class="admin-input" inputmode="decimal" />
        <p class="admin-hint">{{ t('admin.shipping.freeFromHint') }}</p>
      </div>
      <div class="admin-field">
        <label class="admin-label" :for="`time-${rate.country}`">{{ t('admin.shipping.deliveryTime') }}</label>
        <input :id="`time-${rate.country}`" v-model="rate.deliveryTime" class="admin-input" maxlength="200" />
      </div>
      <label class="admin-check admin-field wide">
        <input v-model="rate.active" type="checkbox" />
        {{ t('admin.shipping.active') }}
      </label>
    </div>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="rate.saving">{{ t('admin.shipping.save') }}</button>
    </div>
  </form>
</template>

<style scoped>
.message {
  margin-top: var(--space-3);
}
</style>
