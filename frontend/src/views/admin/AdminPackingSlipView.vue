<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronLeft, Printer } from 'lucide-vue-next'
import AppLogo from '../../components/layout/AppLogo.vue'
import FormMessage from '../../components/FormMessage.vue'
import { adminOrderApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/** A plain page to print and put in the box. Outside the admin layout, so only the slip itself prints. */
const props = defineProps({
  number: { type: String, required: true },
})

const order = ref(null)
const error = ref('')

const shipmentText = computed(() => {
  const value = order.value
  if (!value?.hasPreOrders || !value.hasInStock) return ''
  return value.shipmentPreference === 'IN_STOCK_FIRST' ? t('admin.packing.inStockFirst') : t('admin.packing.together')
})

const print = () => window.print()

onMounted(async () => {
  try {
    order.value = (await adminOrderApi.get(props.number)).order
  } catch (exception) {
    error.value = exception.message
  }
})
</script>

<template>
  <div class="slip-page">
    <div class="toolbar">
      <router-link class="btn btn-secondary btn-small" :to="`/admin/bestellingen/${number}`">
        <ChevronLeft :size="18" aria-hidden="true" />
        {{ t('admin.packing.back') }}
      </router-link>
      <button class="btn btn-primary btn-small" type="button" :disabled="!order" @click="print">
        <Printer :size="18" aria-hidden="true" />
        {{ t('admin.packing.print') }}
      </button>
    </div>

    <FormMessage v-if="error">{{ error }}</FormMessage>

    <article v-if="order" class="slip">
      <header class="slip-head">
        <AppLogo />
        <div class="slip-title">
          <h1>{{ t('admin.packing.title') }}</h1>
          <p>{{ t('admin.packing.order') }} <strong>{{ order.number }}</strong></p>
          <p>{{ t('admin.packing.date') }} {{ formatDate(order.paidAt ?? order.createdAt) }}</p>
        </div>
      </header>

      <section class="ship-to">
        <h2>{{ t('admin.packing.shipTo') }}</h2>
        <address>
          {{ order.address.name }}<br />
          {{ order.address.street }} {{ order.address.houseNumber }}<br />
          {{ order.address.postcode }} {{ order.address.city }}<br />
          {{ t(`countries.${order.address.country}`) }}
        </address>
      </section>

      <p v-if="shipmentText" class="note">{{ shipmentText }}</p>

      <table class="lines">
        <thead>
          <tr>
            <th scope="col" class="check-col">{{ t('admin.packing.check') }}</th>
            <th scope="col" class="qty">{{ t('admin.packing.quantity') }}</th>
            <th scope="col">{{ t('admin.packing.product') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in order.lines" :key="`${line.productId}-${line.unitPriceCents}`">
            <td class="check-col"><span class="box" aria-hidden="true"></span></td>
            <td class="qty">{{ line.quantity }}</td>
            <td>
              {{ line.name }}
              <span v-if="line.preOrder" class="pre-order">
                {{ t('admin.packing.preOrderNote', { date: line.shipsFrom ? formatDate(line.shipsFrom) : '?' }) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="thanks">{{ t('admin.packing.thanks') }}</p>
    </article>
  </div>
</template>

<style scoped>
.slip-page {
  max-width: 48rem;
  margin: 0 auto;
  padding: var(--space-5) var(--gutter);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.slip {
  display: grid;
  gap: var(--space-5);
}

.slip-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: 2px solid var(--color-ink);
}

.slip-title {
  text-align: right;
}

.ship-to h2 {
  margin-bottom: var(--space-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
}

.ship-to address {
  font-size: var(--text-lg);
  font-style: normal;
  line-height: 1.4;
}

.note {
  padding: var(--space-2) var(--space-3);
  border: 1.5px solid var(--color-ink);
  font-weight: 700;
}

.lines {
  width: 100%;
  border-collapse: collapse;
}

.lines th,
.lines td {
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-line-strong);
  text-align: left;
  vertical-align: top;
}

.check-col {
  width: 4.5rem;
}

.qty {
  width: 4rem;
  font-weight: 700;
}

.box {
  display: inline-block;
  width: 1.125rem;
  height: 1.125rem;
  border: 1.5px solid var(--color-ink);
}

.pre-order {
  display: block;
  font-size: var(--text-xs);
  font-weight: 700;
}

.thanks {
  color: var(--color-ink-soft);
}

@media print {
  .toolbar {
    display: none;
  }

  .slip-page {
    max-width: none;
    padding: 0;
  }
}
</style>
