<script setup>
import { onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, LifeBuoy } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import TicketStatusBadge from '../../components/support/TicketStatusBadge.vue'
import { supportApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'
import PageSkeleton from '../../components/skeleton/PageSkeleton.vue'

/** The tickets sent from this account, the last changed first. */
const tickets = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    tickets.value = await supportApi.mine()
  } catch (exception) {
    error.value = exception.message
  }
})
</script>

<template>
  <div class="container page">
    <router-link class="back" to="/account">
      <ChevronLeft :size="18" aria-hidden="true" />
      {{ t('orders.list.back') }}
    </router-link>
    <h1>{{ t('support.mine.title') }}</h1>

    <FormMessage v-if="error">{{ error }}</FormMessage>
    <PageSkeleton v-else-if="!tickets" kind="list" :page="false" :heading="false" />
    <div v-else-if="!tickets.length" class="empty">
      <LifeBuoy :size="40" aria-hidden="true" />
      <p>{{ t('support.mine.empty') }}</p>
      <router-link class="btn btn-primary" to="/info/contact">{{ t('support.mine.toContact') }}</router-link>
    </div>
    <ul v-else class="tickets">
      <li v-for="ticket in tickets" :key="ticket.number">
        <router-link class="ticket" :to="`/klantenservice/ticket/${ticket.token}`">
          <span class="info">
            <span class="subject">{{ ticket.subject }}</span>
            <span class="detail">{{ ticket.number }} · {{ t('support.mine.updated', { date: formatDateTime(ticket.updatedAt) }) }}</span>
          </span>
          <TicketStatusBadge :status="ticket.status" />
          <ChevronRight class="chevron" :size="18" aria-hidden="true" />
        </router-link>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  align-content: start;
  gap: var(--space-4);
  max-width: 56rem;
  padding-block: var(--space-4) 0;
}

.back {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  gap: var(--space-1);
  min-height: var(--touch-target);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.muted {
  color: var(--color-ink-soft);
}

.empty {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-6) var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-tint);
  color: var(--color-ink-soft);
  text-align: center;
}

.tickets {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ticket {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  color: var(--color-ink);
  text-decoration: none;
}

@media (hover: hover) {
  .ticket:hover {
    border-color: var(--color-brand);
  }
}

.info {
  display: grid;
  gap: var(--space-half);
  min-width: 0;
}

.subject {
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.chevron {
  color: var(--color-ink-soft);
}
</style>
