<script setup>
import { ref, watch } from 'vue'
import { Download } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import { adminContentApi } from '../../services/api'
import { formatDateTime, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  status: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const STATUSES = ['CONFIRMED', 'PENDING', 'UNSUBSCRIBED']

const result = ref(null)
const error = ref('')

async function load() {
  try {
    result.value = await adminContentApi.subscribers({ status: props.status, page: props.page ? Number(props.page) - 1 : 0 })
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => [props.status, props.page], load, { immediate: true })

async function remove(subscriber) {
  if (!window.confirm(t('admin.newsletter.deleteConfirm', { email: subscriber.email }))) return
  try {
    await adminContentApi.deleteSubscriber(subscriber.id)
    await load()
  } catch (exception) {
    error.value = exception.message
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

  <dl v-if="result" class="counts">
    <div v-for="status in STATUSES" :key="status">
      <dt>{{ t(`admin.newsletter.statuses.${status}`) }}</dt>
      <dd>{{ formatNumber(result.counts[status] ?? 0) }}</dd>
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

  <div v-if="result?.subscribers.length" class="admin-table-scroll">
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
        <tr v-for="subscriber in result.subscribers" :key="subscriber.id">
          <th scope="row">{{ subscriber.email }}</th>
          <td>{{ t(`admin.newsletter.statuses.${subscriber.status}`) }}</td>
          <td>{{ formatDateTime(subscriber.createdAt) }}</td>
          <td>{{ subscriber.confirmedAt ? formatDateTime(subscriber.confirmedAt) : '' }}</td>
          <td><button class="link-button danger" type="button" @click="remove(subscriber)">{{ t('admin.newsletter.delete') }}</button></td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="result" class="admin-intro message">{{ t('admin.newsletter.empty') }}</p>
  <PageLinks v-if="result?.totalPages > 1" :page="result.page + 1" :total-pages="result.totalPages" />
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
