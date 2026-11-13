<script setup>
import { ref, watch } from 'vue'
import { ChevronLeft, Send } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import TicketStatusBadge from '../../components/support/TicketStatusBadge.vue'
import TicketThread from '../../components/support/TicketThread.vue'
import { adminTicketApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** One ticket: the conversation, an answer that the customer gets by mail, and closing it. */
const props = defineProps({
  number: { type: String, required: true },
})

const ticket = ref(null)
const answer = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  error.value = ''
  try {
    ticket.value = await adminTicketApi.get(props.number)
  } catch (exception) {
    error.value = exception.message
  }
}

async function act(action, success) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    ticket.value = await action()
    notice.value = success
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

async function send(close) {
  await act(() => adminTicketApi.reply(props.number, { message: answer.value, close }), t('admin.tickets.sent'))
  if (!error.value) answer.value = ''
}

watch(() => props.number, load, { immediate: true })
</script>

<template>
  <router-link class="back" to="/admin/tickets">
    <ChevronLeft :size="18" aria-hidden="true" />
    {{ t('admin.tickets.back') }}
  </router-link>

  <FormMessage v-if="error && !ticket">{{ error }}</FormMessage>
  <template v-else-if="ticket">
    <header class="head">
      <p class="number">{{ ticket.number }}</p>
      <h1>{{ ticket.subject }}</h1>
      <p class="meta">
        <TicketStatusBadge :status="ticket.status" />
        <span>{{ ticket.name }} · <a :href="`mailto:${ticket.email}`">{{ ticket.email }}</a></span>
        <span>{{ ticket.hasAccount ? t('admin.tickets.account') : t('admin.tickets.guest') }}</span>
        <router-link v-if="ticket.orderNumber" :to="`/admin/bestellingen/${ticket.orderNumber}`">{{ ticket.orderNumber }}</router-link>
        <span>{{ t('admin.tickets.created', { date: formatDateTime(ticket.createdAt) }) }}</span>
      </p>
    </header>

    <TicketThread class="thread" :messages="ticket.messages" :customer-name="ticket.name" />

    <section class="admin-panel answer" aria-labelledby="ticket-answer">
      <h2 id="ticket-answer">{{ t('admin.tickets.answerTitle') }}</h2>
      <p class="admin-hint">{{ t('admin.tickets.answerHint', { email: ticket.email }) }}</p>
      <textarea v-model="answer" class="textarea" rows="7" maxlength="5000" :aria-label="t('admin.tickets.answerTitle')"></textarea>
      <FormMessage v-if="error">{{ error }}</FormMessage>
      <FormMessage v-if="notice" type="success">{{ notice }}</FormMessage>
      <div class="admin-actions">
        <button class="btn btn-primary" type="button" :disabled="busy || !answer.trim()" @click="send(false)">
          <Send :size="18" aria-hidden="true" />
          {{ t('admin.tickets.send') }}
        </button>
        <button class="btn btn-secondary" type="button" :disabled="busy || !answer.trim()" @click="send(true)">
          {{ t('admin.tickets.sendAndClose') }}
        </button>
        <button
          v-if="ticket.status !== 'CLOSED'"
          class="link-button"
          type="button"
          :disabled="busy"
          @click="act(() => adminTicketApi.setStatus(number, 'CLOSED'), t('admin.tickets.closed'))"
        >
          {{ t('admin.tickets.close') }}
        </button>
        <button
          v-else
          class="link-button"
          type="button"
          :disabled="busy"
          @click="act(() => adminTicketApi.setStatus(number, 'OPEN'), t('admin.tickets.reopened'))"
        >
          {{ t('admin.tickets.reopen') }}
        </button>
      </div>
    </section>
  </template>
  <p v-else class="admin-intro" aria-busy="true">{{ t('admin.tickets.loading') }}</p>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.head {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.number {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 700;
}

.head h1 {
  overflow-wrap: anywhere;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  align-items: center;
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.thread {
  max-width: 50rem;
  margin-top: var(--space-5);
}

.answer {
  display: grid;
  gap: var(--space-3);
  max-width: 50rem;
  margin-top: var(--space-5);
}

.textarea {
  width: 100%;
  padding: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  font: inherit;
  resize: vertical;
}
</style>
