<script setup>
import { nextTick, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { wishlistApi } from '../../services/api'
import { formatAmountInput, parseMoneyInput } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * The most a customer wants to pay for a card on their wishlist (2026-11-24), changed from the wishlist page without
 * leaving it. Open while [card] is set. The same maximum as on the card page: above it, the card gets a red frame
 * on the home page when it's for sale. The browser's modal <dialog> keeps the focus inside, closes on Escape and
 * gives the focus back to the button that opened it.
 */
const props = defineProps({
  card: { type: Object, default: null },
})

const emit = defineEmits(['saved', 'close'])

const id = useId()
const dialog = ref(null)
const input = ref(null)
const text = ref('')
const error = ref('')
const saving = ref(false)

watch(
  () => props.card,
  async (card) => {
    if (!card) {
      if (dialog.value?.open) dialog.value.close()
      return
    }
    text.value = formatAmountInput(card.wishlistMaxPriceCents)
    error.value = ''
    if (!dialog.value.open) dialog.value.showModal()
    await nextTick()
    input.value?.select()
  },
)

async function save(cents) {
  const card = props.card
  if (!card) return
  saving.value = true
  error.value = ''
  try {
    await wishlistApi.setMaxPrice(card.id, cents)
    emit('saved', card, cents)
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

function submit() {
  const typed = text.value.trim()
  const cents = typed ? parseMoneyInput(typed) : null
  if (typed && !cents) {
    error.value = t('collection.card.maxPriceInvalid')
    return
  }
  save(cents)
}
</script>

<template>
  <dialog ref="dialog" class="max-dialog" :aria-labelledby="`${id}-title`" @close="emit('close')" @click.self="emit('close')">
    <form v-if="card" class="panel" @submit.prevent="submit">
      <button class="close" type="button" :aria-label="t('wishlist.max.close')" @click="emit('close')">
        <X :size="20" aria-hidden="true" />
      </button>

      <h2 :id="`${id}-title`" class="title">{{ t('wishlist.max.title', { name: card.name }) }}</h2>
      <label class="label" :for="`${id}-input`">{{ t('collection.card.maxPrice') }}</label>
      <span class="field">
        <span class="euro" aria-hidden="true">€</span>
        <input
          :id="`${id}-input`"
          ref="input"
          v-model="text"
          class="input"
          type="text"
          inputmode="decimal"
          autocomplete="off"
          :placeholder="t('collection.card.maxPricePlaceholder')"
          :aria-describedby="`${id}-hint`"
        />
      </span>
      <p :id="`${id}-hint`" class="hint">{{ t('collection.card.maxPriceHint') }}</p>

      <FormMessage v-if="error">{{ error }}</FormMessage>

      <div class="actions">
        <button class="btn btn-primary" type="submit" :disabled="saving">{{ t('collection.card.maxPriceSave') }}</button>
        <button
          v-if="card.wishlistMaxPriceCents != null"
          class="btn btn-secondary"
          type="button"
          :disabled="saving"
          @click="save(null)"
        >
          {{ t('wishlist.max.clear') }}
        </button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.max-dialog {
  width: min(28rem, calc(100vw - 2 * var(--space-4)));
  max-height: calc(100dvh - 2 * var(--space-4));
  padding: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: var(--shadow-panel);
}

/* The scrim of the Premium popup. Written out: older browsers don't pass custom properties on to ::backdrop. */
.max-dialog::backdrop {
  background: rgb(11 42 111 / 50%);
}

.max-dialog[open] {
  animation: max-in 220ms cubic-bezier(0.2, 0.9, 0.3, 1);
}

.max-dialog[open]::backdrop {
  animation: max-fade 220ms ease-out;
}

@keyframes max-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
}

@keyframes max-fade {
  from {
    opacity: 0;
  }
}

:global(html:has(.max-dialog[open])) {
  overflow: hidden;
}

.panel {
  position: relative;
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
}

.close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: var(--touch-target);
  height: var(--touch-target);
  border: 0;
  border-radius: var(--radius-round);
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
}

@media (hover: hover) {
  .close:hover {
    background: var(--color-tint);
    color: var(--color-ink);
  }
}

.title {
  padding-right: var(--touch-target);
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
}

.label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.field {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.field:focus-within {
  outline: 2px solid var(--color-focus);
  outline-offset: 1px;
}

.euro {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

/* 16px, so a phone doesn't zoom in when the field gets focus. */
.input {
  width: 9rem;
  height: 100%;
  padding-inline: var(--space-1);
  border: 0;
  background: transparent;
  color: var(--color-ink);
  font-size: 1rem;
  outline: none;
}

.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (prefers-reduced-motion: reduce) {
  .max-dialog[open],
  .max-dialog[open]::backdrop {
    animation: none;
  }
}
</style>
