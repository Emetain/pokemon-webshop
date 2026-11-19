<script setup>
import { computed, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import ListEnd from '../../components/catalog/ListEnd.vue'
import FormMessage from '../../components/FormMessage.vue'
import OrderStatusBadge from '../../components/order/OrderStatusBadge.vue'
import { adminOrderApi } from '../../services/api'
import { filterQuery, pageFromQuery } from '../../utils/catalog'
import { formatDateTime, formatMoney, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'
import { useAdminList } from '../../utils/infiniteList'

const SEARCH_DELAY_MS = 350
const STATUSES = ['PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'PENDING_PAYMENT', 'CANCELLED', 'REFUNDED', 'ABANDONED']

/** The search, status and page live in the URL. */
const props = defineProps({
  q: { type: String, default: '' },
  status: { type: String, default: '' },
  page: { type: String, default: '1' },
})

const router = useRouter()
const searchId = useId()

const searchText = ref(props.q)

const currentPage = computed(() => pageFromQuery(props.page))

function apply(changes) {
  router.push({ query: filterQuery({ q: props.q, status: props.status, ...changes }) })
}

const statusText = (status) => (status === 'ABANDONED' ? t('admin.orders.abandoned') : t(`orders.statuses.${status}`))

let searchTimer = null
watch(searchText, (text) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (text.trim() !== props.q) apply({ q: text.trim() })
  }, SEARCH_DELAY_MS)
})
watch(() => props.q, (q) => (searchText.value = q))

/** More orders load while scrolling (phase 11). */
const orders = useAdminList({
  fetchPage: (page) => adminOrderApi.list({ q: props.q, status: props.status, page }),
  key: () => [props.q, props.status],
  page: () => currentPage.value,
})
const error = computed(() => orders.error?.message ?? '')
</script>

<template>
  <h1>{{ t('admin.orders.title') }}</h1>
  <p class="admin-intro">{{ t('admin.orders.intro') }}</p>

  <div class="filters">
    <div class="admin-field search">
      <label class="admin-label" :for="searchId">{{ t('admin.orders.search') }}</label>
      <input :id="searchId" v-model="searchText" class="admin-input" type="search" autocomplete="off" />
    </div>
  </div>
  <!-- A router link counts every tab as the current page, because only the query differs. -->
  <nav class="tabs" :aria-label="t('admin.orders.columns.status')">
    <router-link
      v-for="item in ['', ...STATUSES]"
      :key="item || 'all'"
      v-slot="{ href, navigate }"
      :to="{ query: filterQuery({ q, status: item }) }"
      custom
    >
      <a
        class="tab"
        :class="{ active: status === item }"
        :href="href"
        :aria-current="status === item ? 'page' : undefined"
        @click="navigate"
      >
        {{ item ? statusText(item) : t('admin.orders.all') }}
      </a>
    </router-link>
  </nav>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <p v-else-if="orders.loading && !orders.items.length" class="muted">{{ t('admin.orders.loading') }}</p>
  <p v-else-if="orders.loaded && !orders.items.length" class="muted">{{ t('admin.orders.empty') }}</p>

  <template v-if="orders.items.length">
    <p class="count">{{ formatNumber(orders.totalItems) }} {{ t('admin.orders.title').toLowerCase() }}</p>
    <div class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th scope="col">{{ t('admin.orders.columns.number') }}</th>
            <th scope="col">{{ t('admin.orders.columns.date') }}</th>
            <th scope="col">{{ t('admin.orders.columns.customer') }}</th>
            <th scope="col" class="num">{{ t('admin.orders.columns.items') }}</th>
            <th scope="col" class="num">{{ t('admin.orders.columns.total') }}</th>
            <th scope="col">{{ t('admin.orders.columns.status') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in orders.items" :key="order.number">
            <td>
              <router-link class="number" :to="`/admin/bestellingen/${order.number}`">{{ order.number }}</router-link>
            </td>
            <td class="nowrap">{{ formatDateTime(order.paidAt ?? order.createdAt) }}</td>
            <td>
              <span class="customer">{{ order.customerName }}</span>
              <span class="sub">{{ order.email }}<template v-if="order.guest">, {{ t('admin.orders.guest') }}</template></span>
            </td>
            <td class="num">
              {{ order.itemCount }}
              <span v-if="order.hasPreOrders" class="sub">{{ t('admin.orders.preOrder') }}</span>
            </td>
            <td class="num">
              {{ formatMoney(order.totalCents, 'EUR') }}
              <span v-if="order.refundedCents" class="sub">−{{ formatMoney(order.refundedCents, 'EUR') }}</span>
            </td>
            <td><OrderStatusBadge :status="order.status" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <ListEnd :list="orders" />
  </template>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.search {
  flex: 1 1 16rem;
  max-width: 26rem;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-3);
}

.tab {
  display: inline-flex;
  align-items: center;
  min-height: 2.25rem;
  padding: 0 var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.tab:hover {
  border-color: var(--color-line-strong);
}

.tab.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.message,
.muted,
.count {
  margin-top: var(--space-4);
}

.muted,
.count {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.number {
  font-weight: 700;
  white-space: nowrap;
}

.nowrap {
  white-space: nowrap;
}

.customer {
  display: block;
  font-weight: 600;
}

.sub {
  display: block;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
