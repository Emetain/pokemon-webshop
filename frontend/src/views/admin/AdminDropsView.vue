<script setup>
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { adminDropApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

const drops = ref(null)
const error = ref('')

adminDropApi
  .list()
  .then((result) => (drops.value = result))
  .catch((exception) => (error.value = exception.message))
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.drops.title') }}</h1>
      <p class="admin-intro">{{ t('admin.drops.intro') }}</p>
    </div>
    <router-link class="btn btn-primary btn-small" to="/admin/drops/nieuw">
      <Plus :size="18" aria-hidden="true" />
      {{ t('admin.drops.add') }}
    </router-link>
  </div>

  <FormMessage v-if="error">{{ error }}</FormMessage>
  <p v-else-if="drops && !drops.length" class="admin-intro">{{ t('admin.drops.empty') }}</p>

  <div v-else-if="drops" class="admin-table-scroll">
    <table class="admin-table">
      <thead>
        <tr>
          <th scope="col">{{ t('admin.drops.name') }}</th>
          <th scope="col">{{ t('admin.drops.status') }}</th>
          <th scope="col">{{ t('admin.drops.startsAt') }}</th>
          <th scope="col" class="num">{{ t('admin.drops.products') }}</th>
          <th scope="col" class="num">{{ t('admin.drops.sold') }}</th>
          <th scope="col">{{ t('admin.drops.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="drop in drops" :key="drop.id">
          <td>
            <router-link :to="`/admin/drops/${drop.id}`">{{ drop.name }}</router-link>
          </td>
          <td>
            <span class="status" :class="drop.status.toLowerCase()">{{ t(`drops.status.${drop.status}`) }}</span>
          </td>
          <td>{{ formatDateTime(drop.startsAt) }}</td>
          <td class="num">{{ drop.productCount }}</td>
          <td class="num">{{ drop.sold }} / {{ drop.allocated }}</td>
          <td class="actions">
            <router-link :to="`/admin/drops/${drop.id}`">{{ t('admin.drops.edit') }}</router-link>
            <router-link v-if="drop.status !== 'DRAFT'" :to="`/admin/drops/${drop.id}/live`">
              {{ drop.status === 'ENDED' ? t('admin.drops.report') : t('admin.drops.liveView') }}
            </router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.status {
  display: inline-block;
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-muted-soft);
  font-size: var(--text-xs);
  font-weight: 700;
}

.status.scheduled {
  background: var(--color-tint-strong);
  color: var(--color-brand-deep);
}

.status.live {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.status.ended {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
