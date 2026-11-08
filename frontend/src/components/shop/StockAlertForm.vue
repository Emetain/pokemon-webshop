<script setup>
import { ref, useId } from 'vue'
import { BellRing } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { shopApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/** "Mail me when back in stock", filled in with the account's address when logged in. */
const props = defineProps({
  slug: { type: String, required: true },
})

const session = useSession()
const id = useId()
const email = ref(session.state.user?.email ?? '')
const busy = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await shopApi.stockAlert(props.slug, email.value.trim())
    done.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="alert-box" :aria-labelledby="`${id}-title`">
    <h2 :id="`${id}-title`" class="title">
      <BellRing :size="18" aria-hidden="true" />
      {{ t('shop.alert.title') }}
    </h2>
    <FormMessage v-if="done" type="success">{{ t('shop.alert.done') }}</FormMessage>
    <template v-else>
      <p class="text">{{ t('shop.alert.text') }}</p>
      <form class="row" @submit.prevent="submit">
        <label class="visually-hidden" :for="id">{{ t('shop.alert.email') }}</label>
        <input
          :id="id"
          v-model="email"
          class="input"
          type="email"
          autocomplete="email"
          required
          :placeholder="t('shop.alert.email')"
        />
        <button class="btn btn-secondary" type="submit" :disabled="busy">{{ t('shop.alert.submit') }}</button>
      </form>
      <FormMessage v-if="error">{{ error }}</FormMessage>
    </template>
  </section>
</template>

<style scoped>
.alert-box {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-md);
}

.title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-body);
  font-size: var(--text-md);
  font-stretch: 100%;
  font-weight: 700;
}

.text {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.input {
  flex: 1 1 12rem;
  min-width: 0;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.input:hover {
  border-color: var(--color-ink);
}
</style>
