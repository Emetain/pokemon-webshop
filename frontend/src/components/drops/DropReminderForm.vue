<script setup>
import { ref, useId } from 'vue'
import { BellRing } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { dropApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/** "Herinner mij": one mail shortly before the start. A logged-in customer needs no email field. */
const props = defineProps({
  slug: { type: String, required: true },
  minutes: { type: Number, default: 15 },
})

const session = useSession()
const id = useId()
const email = ref('')
const website = ref('')
const sending = ref(false)
const done = ref('')
const error = ref('')

async function submit() {
  sending.value = true
  error.value = ''
  try {
    const address = session.isLoggedIn.value ? null : email.value.trim()
    await dropApi.remind(props.slug, { email: address, website: website.value })
    done.value = address ? t('drops.reminder.done', { email: address }) : t('drops.reminder.doneLoggedIn')
  } catch (exception) {
    error.value = exception.message
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="reminder">
    <h3 class="title"><BellRing :size="18" aria-hidden="true" />{{ t('drops.reminder.title') }}</h3>
    <p class="text">{{ t('drops.reminder.text', { minutes }) }}</p>
    <FormMessage v-if="done" type="success">{{ done }}</FormMessage>
    <form v-else class="form" @submit.prevent="submit">
      <!-- People never see this field. A bot that fills in every field gets turned away. -->
      <div class="trap" aria-hidden="true">
        <label :for="`${id}-website`">{{ t('drops.checkout.honeypot') }}</label>
        <input :id="`${id}-website`" v-model="website" type="text" name="website" tabindex="-1" autocomplete="off" />
      </div>
      <template v-if="!session.isLoggedIn.value">
        <label class="visually-hidden" :for="`${id}-email`">{{ t('drops.reminder.email') }}</label>
        <input
          :id="`${id}-email`"
          v-model="email"
          class="input"
          type="email"
          autocomplete="email"
          required
          :placeholder="t('drops.reminder.email')"
        />
      </template>
      <button class="btn btn-secondary" type="submit" :disabled="sending">
        {{ sending ? t('drops.reminder.sending') : session.isLoggedIn.value ? t('drops.reminder.submitLoggedIn') : t('drops.reminder.submit') }}
      </button>
    </form>
    <FormMessage v-if="error">{{ error }}</FormMessage>
  </div>
</template>

<style scoped>
.reminder {
  display: grid;
  gap: var(--space-2);
}

.title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
}

.text {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.input {
  flex: 1 1 14rem;
  min-height: var(--control-height);
  padding: 0 var(--space-3);
  border: 1px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  font: inherit;
}

.trap {
  position: absolute;
  left: -10000px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}
</style>
