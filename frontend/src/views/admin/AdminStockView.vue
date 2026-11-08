<script setup>
import { ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import { adminShopApi } from '../../services/api'
import { reasonLabel, signed } from '../../utils/admin'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const low = ref(null)
const movements = ref(null)
const page = ref(0)
const error = ref('')

async function loadLow() {
  try {
    low.value = await adminShopApi.lowStock()
  } catch (exception) {
    error.value = exception.message
  }
}

async function loadMovements() {
  try {
    movements.value = await adminShopApi.latestMovements(page.value)
  } catch (exception) {
    error.value = exception.message
  }
}

function goTo(number) {
  page.value = number
  loadMovements()
}

loadLow()
loadMovements()
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
    <p v-if="movements && !movements.items.length" class="empty">{{ t('admin.stock.latestEmpty') }}</p>
    <div v-else-if="movements" class="admin-table-scroll">
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
    <div v-if="movements && movements.totalPages > 1" class="admin-actions">
      <button class="link-button" type="button" :disabled="page === 0" @click="goTo(page - 1)">
        {{ t('catalog.pagination.previous') }}
      </button>
      <span class="admin-hint">{{ t('catalog.pagination.page', { page: page + 1 }) }} / {{ movements.totalPages }}</span>
      <button class="link-button" type="button" :disabled="page + 1 >= movements.totalPages" @click="goTo(page + 1)">
        {{ t('catalog.pagination.next') }}
      </button>
    </div>
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
