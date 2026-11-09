<script setup>
import { onMounted, ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import OrderStatusBadge from '../../components/order/OrderStatusBadge.vue'
import { adminApi, adminOrderApi } from '../../services/api'
import { formatDateTime, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

const status = ref(null)
const dashboard = ref(null)
const error = ref('')

const PERIODS = ['today', 'week', 'month']

const orderCount = (count) => (count === 1 ? t('admin.dashboard.oneOrder') : t('admin.dashboard.orders', { count }))

onMounted(async () => {
  try {
    ;[status.value, dashboard.value] = await Promise.all([adminApi.status(), adminOrderApi.dashboard()])
  } catch (exception) {
    error.value = exception.message
  }
})
</script>

<template>
  <h1>{{ t('admin.overview') }}</h1>
  <FormMessage v-if="error" class="status">{{ error }}</FormMessage>
  <p v-else-if="status" class="status">{{ t('admin.greeting', { name: status.displayName }) }}</p>

  <template v-if="dashboard">
    <FormMessage v-if="!dashboard.paymentsEnabled" class="status">{{ t('admin.dashboard.paymentsOff') }}</FormMessage>

    <section class="revenue" :aria-label="t('admin.dashboard.revenue')">
      <div v-for="period in PERIODS" :key="period" class="figure">
        <h2 class="figure-label">{{ t(`admin.dashboard.${period}`) }}</h2>
        <p class="figure-value">{{ formatMoney(dashboard[period].revenueCents, 'EUR') }}</p>
        <p class="figure-sub">{{ orderCount(dashboard[period].orders) }}</p>
      </div>
      <router-link class="figure open" to="/admin/bestellingen?status=PAID">
        <h2 class="figure-label">{{ t('admin.dashboard.open') }}</h2>
        <p class="figure-value">{{ dashboard.openOrders }}</p>
        <p class="figure-sub">{{ t('admin.dashboard.openText') }}</p>
      </router-link>
    </section>
    <p class="note">{{ t('admin.dashboard.revenueNote') }}</p>

    <div class="columns">
      <section class="admin-panel">
        <div class="panel-head">
          <h2>{{ t('admin.dashboard.latest') }}</h2>
          <router-link to="/admin/bestellingen">{{ t('admin.dashboard.toOrders') }}</router-link>
        </div>
        <p v-if="!dashboard.latestOrders.length" class="note">{{ t('admin.dashboard.noOrders') }}</p>
        <ul v-else class="list">
          <li v-for="order in dashboard.latestOrders" :key="order.number" class="row">
            <router-link class="row-main" :to="`/admin/bestellingen/${order.number}`">{{ order.number }}</router-link>
            <span class="row-sub">{{ order.customerName }}, {{ formatDateTime(order.createdAt) }}</span>
            <OrderStatusBadge class="row-status" :status="order.status" />
            <span class="row-amount">{{ formatMoney(order.totalCents, 'EUR') }}</span>
          </li>
        </ul>
      </section>

      <section class="admin-panel">
        <div class="panel-head">
          <h2>{{ t('admin.dashboard.lowStock') }}</h2>
          <router-link to="/admin/voorraad">{{ t('admin.dashboard.toStock') }}</router-link>
        </div>
        <p v-if="!dashboard.lowStock.length" class="note">{{ t('admin.dashboard.noLowStock') }}</p>
        <ul v-else class="list">
          <li v-for="product in dashboard.lowStock" :key="product.id" class="row">
            <router-link class="row-main" :to="`/admin/producten/${product.id}`">{{ product.name }}</router-link>
            <span class="row-amount low">{{ product.available }}</span>
          </li>
        </ul>
      </section>
    </div>
  </template>
</template>

<style scoped>
.status {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}

.revenue {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr));
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.figure {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-tint);
  color: var(--color-ink);
  text-decoration: none;
}

.figure.open {
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.figure.open:hover {
  background: var(--color-brand-hover);
}

.figure-label {
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 600;
}

.figure-value {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-stretch: 88%;
  font-weight: 750;
  line-height: 1.1;
}

.figure-sub {
  font-size: var(--text-xs);
  opacity: 0.8;
}

.note {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.columns {
  display: grid;
  gap: 0 var(--space-4);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

.list {
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-1) var(--space-3);
  align-items: center;
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  font-size: var(--text-sm);
}

.row:last-child {
  border-bottom: 0;
}

.row-main {
  font-weight: 650;
  overflow-wrap: anywhere;
}

.row-sub {
  grid-column: 1;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.row-status {
  grid-column: 2;
  grid-row: 1;
  justify-self: end;
}

.row-amount {
  grid-column: 2;
  justify-self: end;
  font-weight: 700;
  white-space: nowrap;
}

.low {
  color: var(--color-warning);
}

@media (min-width: 1100px) {
  .columns {
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  }
}
</style>
