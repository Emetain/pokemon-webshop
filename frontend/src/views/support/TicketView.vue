<script setup>
import { ref, watch } from 'vue'
import { ChevronLeft, Send } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import TicketStatusBadge from '../../components/support/TicketStatusBadge.vue'
import TicketThread from '../../components/support/TicketThread.vue'
import { supportApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'
import PageSkeleton from '../../components/skeleton/PageSkeleton.vue'

/** A ticket, opened with the secret link from the mail, logged in or not. The customer can add a message. */
const props = defineProps({
  token: { type: String, required: true },
})

const session = useSession()
const ticket = ref(null)
const loadError = ref('')
const message = ref('')
const busy = ref(false)
const error = ref('')
const sent = ref(false)

async function load() {
  loadError.value = ''
  try {
    ticket.value = await supportApi.ticket(props.token)
  } catch (exception) {
    loadError.value = exception.message
  }
}

async function reply() {
  busy.value = true
  error.value = ''
  sent.value = false
  try {
    ticket.value = await supportApi.reply(props.token, message.value)
    message.value = ''
    sent.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

watch(() => props.token, load, { immediate: true })
</script>

<template>
  <div class="container page">
    <router-link class="back" :to="session.state.user ? '/account/tickets' : '/info/contact'">
      <ChevronLeft :size="18" aria-hidden="true" />
      {{ session.state.user ? t('support.mine.title') : t('support.ticket.back') }}
    </router-link>

    <FormMessage v-if="loadError">{{ loadError }}</FormMessage>
    <PageSkeleton v-else-if="!ticket" kind="article" :page="false" />
    <template v-else>
      <header class="head">
        <p class="number">{{ t('support.ticket.number', { number: ticket.number }) }}</p>
        <h1>{{ ticket.subject }}</h1>
        <p class="meta">
          <TicketStatusBadge :status="ticket.status" />
          <span>{{ t('support.ticket.created', { date: formatDateTime(ticket.createdAt) }) }}</span>
          <span v-if="ticket.orderNumber">{{ t('support.ticket.order', { number: ticket.orderNumber }) }}</span>
        </p>
        <p class="status-text">{{ t(`support.status.text.${ticket.status}`) }}</p>
      </header>

      <TicketThread :messages="ticket.messages" />

      <form class="reply" @submit.prevent="reply">
        <label class="label" for="ticket-reply">{{ t('support.ticket.replyLabel') }}</label>
        <textarea id="ticket-reply" v-model="message" class="textarea" rows="5" maxlength="5000"></textarea>
        <FormMessage v-if="error">{{ error }}</FormMessage>
        <FormMessage v-if="sent" type="success">{{ t('support.ticket.replySent') }}</FormMessage>
        <div>
          <button class="btn btn-primary" type="submit" :disabled="busy || !message.trim()">
            <Send :size="18" aria-hidden="true" />
            {{ busy ? t('contact.sending') : t('support.ticket.reply') }}
          </button>
        </div>
      </form>
    </template>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: var(--space-5);
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

.muted,
.status-text {
  color: var(--color-ink-soft);
}

.head {
  display: grid;
  gap: var(--space-2);
}

.number {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.04em;
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

.reply {
  display: grid;
  gap: var(--space-3);
  max-width: 44rem;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-line);
}

.label {
  font-weight: 650;
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
