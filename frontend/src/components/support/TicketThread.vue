<script setup>
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** The messages of a ticket, oldest first. The customer's on one side, the shop's on the other. */
defineProps({
  messages: { type: Array, required: true },
  /** Admin shows which admin answered, and calls the customer by name. */
  customerName: { type: String, default: null },
})
</script>

<template>
  <ol class="thread">
    <li v-for="(message, index) in messages" :key="index" class="message" :class="{ shop: message.fromShop }">
      <p class="meta">
        <strong>
          {{ message.fromShop ? (message.author ? t('support.thread.shopBy', { name: message.author }) : t('support.thread.shop')) : (customerName ?? t('support.thread.you')) }}
        </strong>
        · {{ formatDateTime(message.createdAt) }}
      </p>
      <p class="body">{{ message.body }}</p>
    </li>
  </ol>
</template>

<style scoped>
.thread {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.message {
  display: grid;
  gap: var(--space-1);
  max-width: 44rem;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.message.shop {
  justify-self: end;
  border-color: transparent;
  background: var(--color-tint);
}

.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.meta strong {
  color: var(--color-ink);
}

.body {
  white-space: pre-line;
  overflow-wrap: anywhere;
}
</style>
