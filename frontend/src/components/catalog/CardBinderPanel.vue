<script setup>
import { computed, ref, useId, watch } from 'vue'
import { BookCopy } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { extraBinderApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { variantLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

/**
 * Puts a card in one of the customer's extra binders (Premium, phase 12), and says which binders hold it. Without
 * Premium the binders a former member made are still listed; adding opens the Premium popup.
 */
const props = defineProps({
  card: { type: Object, required: true },
})

const session = useSession()
const id = useId()
const premium = computed(() => session.isPremium.value)

const state = ref(null)
const binderId = ref(null)
const variant = ref(null)
const error = ref('')
const message = ref('')
const saving = ref(false)

const variants = computed(() => props.card.variants ?? [])

async function load() {
  state.value = null
  error.value = ''
  message.value = ''
  variant.value = variants.value[0] ?? null
  if (!session.isLoggedIn.value) return
  try {
    state.value = await extraBinderApi.card(props.card.id)
    binderId.value = state.value.binders[0]?.id ?? null
  } catch (exception) {
    error.value = exception.message
  }
}

async function add() {
  error.value = ''
  message.value = ''
  saving.value = true
  try {
    state.value = await extraBinderApi.add(binderId.value, { cardId: props.card.id, variant: variant.value, quantity: 1 })
    const name = state.value.binders.find((binder) => binder.id === binderId.value)?.name ?? ''
    message.value = t('binders.card.added', { name })
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

watch([() => props.card.id, () => session.isLoggedIn.value], load, { immediate: true })
</script>

<template>
  <section v-if="session.isLoggedIn.value && state" class="binders">
    <h2><BookCopy :size="20" aria-hidden="true" /> {{ t('binders.card.title') }}</h2>

    <p v-if="state.entries.length" class="in">
      {{ t('binders.card.inBinders') }}:
      <template v-for="(entry, index) in state.entries" :key="entry.entryId">
        <router-link :to="{ name: 'binder', params: { id: entry.binderId } }">{{ entry.binderName }}</router-link>
        <span class="detail"> ({{ variantLabel(entry.variant) }}, {{ entry.quantity }}×)</span>
        <span v-if="index < state.entries.length - 1">, </span>
      </template>
    </p>

    <template v-if="!premium">
      <p class="intro">{{ t('binders.card.intro') }}</p>
      <button class="btn btn-secondary" type="button" aria-haspopup="dialog" @click="openPremium('binders')">
        {{ t('binders.card.unlock') }}
      </button>
    </template>

    <p v-else-if="!state.binders.length" class="intro">
      {{ t('binders.card.none') }}
      <router-link to="/collectie/binders">{{ t('binders.card.makeOne') }}</router-link>
    </p>

    <form v-else class="form" @submit.prevent="add">
      <div class="field">
        <label :for="`${id}-binder`">{{ t('binders.card.binder') }}</label>
        <select :id="`${id}-binder`" v-model="binderId" class="input">
          <option v-for="binder in state.binders" :key="binder.id" :value="binder.id">{{ binder.name }}</option>
        </select>
      </div>
      <div v-if="variants.length > 1" class="field">
        <label :for="`${id}-variant`">{{ t('binders.card.variant') }}</label>
        <select :id="`${id}-variant`" v-model="variant" class="input">
          <option v-for="item in variants" :key="item" :value="item">{{ variantLabel(item) }}</option>
        </select>
      </div>
      <button class="btn btn-primary" type="submit" :disabled="saving || !binderId">{{ t('binders.card.add') }}</button>
    </form>

    <FormMessage v-if="error">{{ error }}</FormMessage>
    <FormMessage v-if="message" type="success">{{ message }}</FormMessage>
  </section>
</template>

<style scoped>
.binders {
  margin-top: var(--space-6);
}

.binders h2 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
}

.in {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
}

.detail {
  color: var(--color-ink-soft);
}

.intro {
  margin-block: var(--space-2) var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.field {
  display: grid;
  gap: var(--space-1);
}

.field label {
  font-size: var(--text-xs);
  font-weight: 600;
}

.input {
  min-width: 10rem;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-md);
}
</style>
