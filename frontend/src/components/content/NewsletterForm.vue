<script setup>
import { ref, useId } from 'vue'
import { Mail } from 'lucide-vue-next'
import { contentApi } from '../../services/api'
import { t } from '../../utils/i18n'

/** Newsletter signup. `onBrand` is the white-on-blue version for the footer. */
defineProps({
  onBrand: { type: Boolean, default: false },
})

const id = useId()
const email = ref('')
const website = ref('')
const busy = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await contentApi.signUp({ email: email.value, website: website.value })
    done.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="newsletter" :class="{ 'on-brand': onBrand }" novalidate @submit.prevent="submit">
    <p v-if="done" class="done" role="status">
      <Mail :size="18" aria-hidden="true" />
      {{ t('newsletter.checkMail', { email }) }}
    </p>
    <template v-else>
      <label class="label" :for="id">{{ t('newsletter.label') }}</label>
      <div class="row">
        <input
          :id="id"
          v-model="email"
          class="input"
          type="email"
          autocomplete="email"
          required
          :placeholder="t('newsletter.placeholder')"
        />
        <button class="btn btn-small" :class="onBrand ? 'btn-on-brand' : 'btn-primary'" type="submit" :disabled="busy">
          {{ t('newsletter.submit') }}
        </button>
      </div>
      <!-- People never see this field. A bot that fills in every field gets turned away. -->
      <div class="trap" aria-hidden="true">
        <label :for="`${id}-website`">{{ t('drops.checkout.honeypot') }}</label>
        <input :id="`${id}-website`" v-model="website" type="text" tabindex="-1" autocomplete="off" />
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p class="hint">{{ t('newsletter.hint') }}</p>
    </template>
  </form>
</template>

<style scoped>
.label {
  display: block;
  margin-bottom: var(--space-2);
  font-weight: 650;
}

.row {
  display: flex;
  gap: var(--space-2);
}

.input {
  flex: 1;
  min-width: 0;
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
}

.on-brand .input {
  border-color: transparent;
}

.hint,
.error {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
}

.hint {
  color: var(--color-ink-soft);
}

.on-brand .hint {
  color: var(--color-on-brand-soft);
}

.error {
  color: var(--color-danger);
  font-weight: 600;
}

.on-brand .error {
  color: var(--color-on-brand);
}

.done {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-weight: 600;
}

.trap {
  position: absolute;
  left: -10000px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}
</style>
