<script setup>
import { computed, ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import ListEnd from '../../components/catalog/ListEnd.vue'
import { adminShopApi } from '../../services/api'
import { reasonLabel, signed } from '../../utils/admin'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'
import { useAdminList } from '../../utils/infiniteList'

const low = ref(null)
const lowError = ref('')

async function loadLow() {
  try {
    low.value = await adminShopApi.lowStock()
  } catch (exception) {
    lowError.value = exception.message
  }
}

/** More movements load while scrolling (phase 11). */
const movements = useAdminList({
  fetchPage: (page) => adminShopApi.latestMovements(page),
  key: () => 'latest',
  page: () => 1,
  syncUrl: false,
})
const error = computed(() => lowError.value || movements.error?.message || '')

loadLow()
</script>

<template>
  <h1>{{ t('admin.stock.title') }}</h1>
  <p class="admin-intro">{{ t('admin.stock.intro') }}</p>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <section class="admin-panel" aria-labelledby="stock-low">
    <h2 id="stock-low">{{ t('admin.stock.lowTitle') }}</h2>
    <p class="admin-hint">{{ t('admin.stock.lowIntro') }}</p>
    <p v-if="low && !low.length" class="empty">{{ t('admin.stock.lowEmpty') }}</p>
    <div v-else-if="low" class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th scope="col">{{ t('admin.products.product') }}</th>
            <th class="num" scope="col">{{ t('admin.stock.available') }}</th>
            <th class="num" scope="col">{{ t('admin.stock.reserved') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in low" :key="product.id">
            <th scope="row">
              <router-link :to="`/admin/producten/${product.id}`">{{ product.name }}</router-link>
              <span class="admin-hint threshold">{{ t('admin.stock.threshold', { count: product.lowStockThreshold }) }}</span>
            </th>
            <td class="num" :class="{ out: product.available <= 0 }">{{ formatNumber(product.available) }}</td>
            <td class="num">{{ formatNumber(product.reserved) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <section class="admin-panel" aria-labelledby="stock-latest">
    <h2 id="stock-latest">{{ t('admin.stock.latestTitle') }}</h2>
    <p v-if="movements.loaded && !movements.items.length" class="empty">{{ t('admin.stock.latestEmpty') }}</p>
    <div v-else-if="movements.items.length" class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th scope="col">{{ t('admin.stock.when') }}</th>
            <th scope="col">{{ t('admin.products.product') }}</th>
            <th scope="col">{{ t('admin.stock.what') }}</th>
            <th class="num" scope="col">{{ t('admin.stock.change') }}</th>
            <th scope="col">{{ t('admin.stock.who') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="movement in movements.items" :key="movement.id">
            <td class="nowrap">{{ formatDateTime(movement.createdAt) }}</td>
            <td><router-link :to="`/admin/producten/${movement.productId}`">{{ movement.productName }}</router-link></td>
            <td>
              {{ reasonLabel(movement.reason) }}
              <span v-if="movement.note" class="admin-hint note">{{ movement.note }}</span>
            </td>
            <td class="num" :class="{ plus: movement.change > 0, minus: movement.change < 0 }">{{ signed(movement.change) }}</td>
            <td>{{ movement.adminName ?? t('admin.stock.shop') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <ListEnd v-if="movements.items.length" :list="movements" />
  </section>
</template>

<style scoped>
.message {
  margin-top: var(--space-3);
}

.empty {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
}

.threshold,
.note {
  display: block;
  font-weight: 400;
}

.nowrap {
  white-space: nowrap;
}

.out {
  color: var(--color-danger);
  font-weight: 700;
}

.plus {
  color: var(--color-success);
  font-weight: 700;
}

.minus {
  color: var(--color-danger);
  font-weight: 700;
}
</style>
