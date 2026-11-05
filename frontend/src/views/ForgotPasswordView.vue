<script setup>
import { ref } from 'vue'
import AuthShell from '../components/auth/AuthShell.vue'
import FormField from '../components/FormField.vue'
import FormMessage from '../components/FormMessage.vue'
import { authApi } from '../services/api'
import { t } from '../utils/i18n'

const email = ref('')
const error = ref('')
const sent = ref(false)
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await authApi.forgotPassword({ email: email.value })
    sent.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell :title="t('auth.forgot.title')" :intro="sent ? '' : t('auth.forgot.intro')">
    <FormMessage v-if="sent" type="success">{{ t('auth.forgot.sent') }}</FormMessage>
    <form v-else class="form-stack" novalidate @submit.prevent="submit">
      <FormMessage v-if="error">{{ error }}</FormMessage>
      <FormField v-model="email" :label="t('auth.email')" type="email" autocomplete="email" />
      <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
        {{ t('auth.forgot.submit') }}
      </button>
    </form>
    <p class="auth-links">
      <router-link to="/inloggen">{{ t('auth.forgot.backToLogin') }}</router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.auth-links {
  margin-top: var(--space-4);
  font-size: var(--text-sm);
}
</style>
