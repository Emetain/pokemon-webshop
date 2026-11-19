<script setup>
import { computed, ref } from 'vue'
import { Download } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import ListEnd from '../../components/catalog/ListEnd.vue'
import { adminContentApi } from '../../services/api'
import { pageFromQuery } from '../../utils/catalog'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'
import { useAdminList } from '../../utils/infiniteList'

const props = defineProps({
  status: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const STATUSES = ['CONFIRMED', 'PENDING', 'UNSUBSCRIBED']

const counts = ref(null)
const actionError = ref('')

/** More subscribers load while scrolling (phase 11). */
const subscribers = useAdminList({
  fetchPage: async (page) => {
    const result = await adminContentApi.subscribers({ status: props.status, page })
    if (page === 0) counts.value = result.counts
    return { items: result.subscribers, totalPages: result.totalPages, totalItems: null }
  },
  key: () => [props.status],
  page: () => pageFromQuery(props.page),
})
const error = computed(() => actionError.value || subscribers.error?.message || '')

async function remove(subscriber) {
  if (!window.confirm(t('admin.newsletter.deleteConfirm', { email: subscriber.email }))) return
  actionError.value = ''
  try {
    await adminContentApi.deleteSubscriber(subscriber.id)
    await subscribers.refresh()
  } catch (exception) {
    actionError.value = exception.message
  }
}
</script>

<template>
  <div class="head">
    <div>
      <h1>{{ t('admin.newsletter.title') }}</h1>
      <p class="admin-intro">{{ t('admin.newsletter.intro') }}</p>
    </div>
    <a class="btn btn-primary btn-small" :href="adminContentApi.subscribersCsvUrl" download>
      <Download :size="18" aria-hidden="true" />
      {{ t('admin.newsletter.export') }}
    </a>
  </div>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <dl v-if="counts" class="counts">
    <div v-for="status in STATUSES" :key="status">
      <dt>{{ t(`admin.newsletter.statuses.${status}`) }}</dt>
      <dd>{{ formatNumber(counts[status] ?? 0) }}</dd>
    </div>
  </dl>

  <nav class="tabs" :aria-label="t('admin.newsletter.filter')">
    <router-link class="tab" :class="{ active: !status }" :to="{ query: {} }">{{ t('admin.newsletter.all') }}</router-link>
    <router-link
      v-for="item in STATUSES"
      :key="item"
      class="tab"
      :class="{ active: status === item }"
      :to="{ query: { status: item } }"
    >
      {{ t(`admin.newsletter.statuses.${item}`) }}
    </router-link>
  </nav>

  <div v-if="subscribers.items.length" class="admin-table-scroll">
    <table class="admin-table">
      <thead>
        <tr>
          <th scope="col">{{ t('admin.newsletter.email') }}</th>
          <th scope="col">{{ t('admin.newsletter.state') }}</th>
          <th scope="col">{{ t('admin.newsletter.signedUp') }}</th>
          <th scope="col">{{ t('admin.newsletter.confirmedAt') }}</th>
          <th scope="col"><span class="visually-hidden">{{ t('admin.newsletter.actions') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="subscriber in subscribers.items" :key="subscriber.id">
          <th scope="row">{{ subscriber.email }}</th>
          <td>{{ t(`admin.newsletter.statuses.${subscriber.status}`) }}</td>
          <td>{{ formatDateTime(subscriber.createdAt) }}</td>
          <td>{{ subscriber.confirmedAt ? formatDateTime(subscriber.confirmedAt) : '' }}</td>
          <td><button class="link-button danger" type="button" @click="remove(subscriber)">{{ t('admin.newsletter.delete') }}</button></td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="subscribers.loaded && !subscribers.loading" class="admin-intro message">{{ t('admin.newsletter.empty') }}</p>
  <ListEnd v-if="subscribers.items.length" :list="subscribers" />
  <p class="admin-hint note">{{ t('admin.newsletter.privacy') }}</p>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.message {
  margin-top: var(--space-3);
}

.counts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
}

.counts div {
  min-width: 9rem;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.counts dt {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.counts dd {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.tab {
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.tab.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.note {
  margin-top: var(--space-4);
}
</style>
