<script setup>
import { reactive, ref } from 'vue'
import { CircleCheck, Send } from 'lucide-vue-next'
import FormField from '../FormField.vue'
import FormMessage from '../FormMessage.vue'
import { supportApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { useSupportChat } from '../../stores/supportChat'
import { t } from '../../utils/i18n'

/**
 * Makes a ticket. A logged-in customer's ticket goes to the account's address and shows under "Mijn tickets". The
 * conversation with the chatbot can go along, so the shop doesn't have to ask the same things again.
 */
const session = useSession()
const chat = useSupportChat()
const user = session.state.user

const form = reactive({
  name: user?.displayName ?? '',
  email: user?.email ?? '',
  orderNumber: '',
  subject: '',
  message: '',
  website: '',
})
const withChat = ref(true)
const busy = ref(false)
const created = ref(null)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    created.value = await supportApi.createTicket({
      ...form,
      orderNumber: form.orderNumber || null,
      chat: withChat.value && chat.hasConversation.value ? chat.state.messages.slice(-20) : [],
    })
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div v-if="created" class="done" role="status">
    <CircleCheck class="done-icon" :size="28" aria-hidden="true" />
    <div>
      <p class="done-title">{{ t('support.ticket.sentTitle', { number: created.number }) }}</p>
      <p>{{ t('support.ticket.sentText') }}</p>
      <router-link class="btn btn-secondary btn-small" :to="`/klantenservice/ticket/${created.token}`">
        {{ t('support.ticket.view') }}
      </router-link>
    </div>
  </div>
  <form v-else class="ticket form-stack" novalidate @submit.prevent="submit">
    <div class="row">
      <FormField v-model="form.name" :label="t('contact.name')" autocomplete="name" />
      <FormField v-if="!user" v-model="form.email" :label="t('contact.email')" type="email" autocomplete="email" />
      <p v-else class="account-mail">{{ t('support.ticket.toAccount', { email: user.email }) }}</p>
    </div>
    <div class="row">
      <FormField v-model="form.orderNumber" :label="t('contact.orderNumber')" :hint="t('contact.orderHint')" />
      <FormField v-model="form.subject" :label="t('contact.subject')" />
    </div>
    <div>
      <label class="label" for="ticket-message">{{ t('contact.message') }}</label>
      <textarea id="ticket-message" v-model="form.message" class="textarea" rows="7" maxlength="5000"></textarea>
    </div>
    <label v-if="chat.hasConversation.value" class="check">
      <input v-model="withChat" type="checkbox" />
      {{ t('support.ticket.withChat') }}
    </label>
    <!-- People never see this field. A bot that fills in every field gets turned away. -->
    <div class="trap" aria-hidden="true">
      <label for="ticket-website">{{ t('drops.checkout.honeypot') }}</label>
      <input id="ticket-website" v-model="form.website" type="text" tabindex="-1" autocomplete="off" />
    </div>
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <div>
      <button class="btn btn-primary" type="submit" :disabled="busy">
        <Send :size="18" aria-hidden="true" />
        {{ busy ? t('contact.sending') : t('support.ticket.send') }}
      </button>
    </div>
    <p class="hint">{{ t('contact.privacy') }}</p>
  </form>
</template>

<style scoped>
.ticket {
  max-width: 44rem;
}

.row {
  display: grid;
  gap: var(--space-4);
}

@media (min-width: 640px) {
  .row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.account-mail {
  align-self: end;
  padding-bottom: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.textarea {
  width: 100%;
  padding: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  font: inherit;
  resize: vertical;
}

.check {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-size: var(--text-sm);
}

.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.done {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  max-width: 44rem;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-success-soft);
}

.done > div {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
}

.done-icon {
  flex: none;
  color: var(--color-success);
}

.done-title {
  font-weight: 750;
}

.trap {
  position: absolute;
  left: -10000px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}
</style>
