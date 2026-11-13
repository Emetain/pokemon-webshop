<script setup>
import { ref, watch } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import TicketStatusBadge from '../../components/support/TicketStatusBadge.vue'
import { adminTicketApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Tickets from customers. Open ones first, the longest waiting on top. */
const props = defineProps({
  status: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const STATUSES = ['OPEN', 'ANSWERED', 'CLOSED']

const result = ref(null)
const error = ref('')

const current = () => props.status ?? 'OPEN'

async function load() {
  try {
    const status = current() === 'ALL' ? undefined : current()
    result.value = await adminTicketApi.list({ status, page: props.page ? Number(props.page) - 1 : 0 })
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => [props.status, props.page], load, { immediate: true })
</script>

<template>
  <h1>{{ t('admin.tickets.title') }}</h1>
  <p class="admin-intro">{{ t('admin.tickets.intro') }}</p>

  <nav class="tabs" :aria-label="t('admin.tickets.filter')">
    <router-link
      v-for="status in [...STATUSES, 'ALL']"
      :key="status"
      class="tab"
      :class="{ active: current() === status }"
      :to="{ query: { status } }"
    >
      {{ t(`admin.tickets.statuses.${status}`) }}
      <span v-if="status === 'OPEN' && result?.counts.OPEN" class="count">{{ result.counts.OPEN }}</span>
    </router-link>
  </nav>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <p v-if="result && !result.tickets.length" class="admin-intro message">{{ t('admin.tickets.empty') }}</p>

  <ul v-if="result?.tickets.length" class="list">
    <li v-for="ticket in result.tickets" :key="ticket.number">
      <router-link class="ticket admin-panel" :to="`/admin/tickets/${ticket.number}`">
        <span class="top">
          <span class="number">{{ ticket.number }}</span>
          <TicketStatusBadge :status="ticket.status" />
        </span>
        <span class="subject">{{ ticket.subject }}</span>
        <span class="admin-hint">
          {{ t('admin.tickets.from', { name: ticket.name, email: ticket.email }) }} ·
          {{ t('admin.tickets.updated', { date: formatDateTime(ticket.updatedAt) }) }}
        </span>
      </router-link>
    </li>
  </ul>
  <PageLinks v-if="result?.totalPages > 1" :page="result.page + 1" :total-pages="result.totalPages" />
</template>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
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

.count {
  padding: 0 var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-sale);
  color: var(--color-on-brand);
  font-size: var(--text-xs);
}

.message {
  margin-top: var(--space-4);
}

.list {
  display: grid;
  gap: var(--space-3);
  max-width: 50rem;
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.ticket {
  display: grid;
  gap: var(--space-1);
  color: var(--color-ink);
  text-decoration: none;
}

.ticket:hover {
  border-color: var(--color-brand);
}

.top {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  justify-content: space-between;
}

.number {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 700;
}

.subject {
  font-weight: 700;
  overflow-wrap: anywhere;
}
</style>
