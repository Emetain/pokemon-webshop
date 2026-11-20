<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Bot, CircleHelp, LifeBuoy } from 'lucide-vue-next'
import SupportChat from './SupportChat.vue'
import TicketForm from './TicketForm.vue'
import { useSupportChat } from '../../stores/supportChat'
import { t } from '../../utils/i18n'

/**
 * Customer service on the contact page, in the order that helps fastest: the FAQ, then the assistant, then a ticket
 * for the people of the shop. `?ticket=1` (from the chat button) opens the ticket form straight away.
 */
const route = useRoute()
const chat = useSupportChat()
const ticketOpen = ref(route.query.ticket === '1')
const ticketSection = ref(null)

const FAQ_LINKS = [
  { to: '/info/veelgestelde-vragen', key: 'faq' },
  { to: '/info/verzending', key: 'shipping' },
  { to: '/info/retourneren', key: 'returns' },
]

async function openTicket() {
  ticketOpen.value = true
  await nextTick()
  ticketSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  ticketSection.value?.querySelector('input')?.focus({ preventScroll: true })
}

onMounted(async () => {
  await chat.loadStatus()
  if (ticketOpen.value) openTicket()
})
</script>

<template>
  <div class="support">
    <section class="step" aria-labelledby="support-faq">
      <h2 id="support-faq"><CircleHelp :size="22" aria-hidden="true" />{{ t('support.flow.faqTitle') }}</h2>
      <p class="text">{{ t('support.flow.faqText') }}</p>
      <ul class="links">
        <li v-for="link in FAQ_LINKS" :key="link.key">
          <router-link class="btn btn-secondary btn-small" :to="link.to">{{ t(`support.flow.links.${link.key}`) }}</router-link>
        </li>
      </ul>
    </section>

    <section v-if="chat.state.available" class="step chat-step" aria-labelledby="support-chat">
      <h2 id="support-chat"><Bot :size="22" aria-hidden="true" />{{ t('support.flow.chatTitle') }}</h2>
      <p class="text">{{ t('support.flow.chatText') }}</p>
      <SupportChat roomy @ticket="openTicket" />
    </section>

    <section id="ticket" ref="ticketSection" class="step" aria-labelledby="support-ticket">
      <h2 id="support-ticket"><LifeBuoy :size="22" aria-hidden="true" />{{ t('support.flow.ticketTitle') }}</h2>
      <p class="text">{{ t('support.flow.ticketText') }}</p>
      <TicketForm v-if="ticketOpen" />
      <div v-else>
        <button class="btn btn-primary" type="button" @click="openTicket">{{ t('support.chat.makeTicket') }}</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.support {
  display: grid;
  gap: var(--space-4);
}

.step {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  scroll-margin-top: 9rem;
}

.chat-step {
  border: 2px solid var(--color-brand);
  box-shadow: var(--shadow-panel);
}

.step h2 {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-size: var(--text-xl);
}

.step h2 svg {
  flex: none;
  color: var(--color-brand-ink);
}

.text {
  color: var(--color-ink-soft);
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

@media (min-width: 640px) {
  .step {
    padding: var(--space-5);
  }
}
</style>
