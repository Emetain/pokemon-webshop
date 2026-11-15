<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AuthShell from '../components/auth/AuthShell.vue'
import FormField from '../components/FormField.vue'
import FormMessage from '../components/FormMessage.vue'
import { authApi } from '../services/api'
import { t } from '../utils/i18n'

const route = useRoute()

const token = computed(() => route.query.token)
const password = ref('')
const passwordConfirmation = ref('')
const mismatch = ref('')
const confirmation = ref(null)
const error = ref('')
const done = ref(false)
const submitting = ref(false)

watch([password, passwordConfirmation], () => {
  mismatch.value = ''
})

async function submit() {
  error.value = ''
  if (password.value !== passwordConfirmation.value) {
    mismatch.value = t('auth.passwordsDiffer')
    confirmation.value.focus()
    return
  }
  submitting.value = true
  try {
    await authApi.resetPassword({
      token: token.value,
      password: password.value,
      passwordConfirmation: passwordConfirmation.value,
    })
    done.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell :title="t('auth.reset.title')">
    <template v-if="done">
      <FormMessage type="success">{{ t('auth.reset.done') }}</FormMessage>
      <router-link class="btn btn-primary btn-block next-step" to="/inloggen">{{ t('auth.reset.toLogin') }}</router-link>
    </template>

    <template v-else-if="!token">
      <FormMessage>{{ t('auth.reset.missingToken') }}</FormMessage>
      <p class="auth-links">
        <router-link to="/wachtwoord-vergeten">{{ t('auth.reset.requestNew') }}</router-link>
      </p>
    </template>

    <template v-else>
      <form class="form-stack" novalidate @submit.prevent="submit">
        <FormMessage v-if="error">{{ error }}</FormMessage>
        <FormField
          v-model="password"
          :label="t('auth.newPassword')"
          type="password"
          autocomplete="new-password"
          :hint="t('auth.passwordHint')"
        />
        <FormField
          ref="confirmation"
          v-model="passwordConfirmation"
          :label="t('auth.repeatNewPassword')"
          type="password"
          autocomplete="new-password"
          :error="mismatch"
        />
        <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
          {{ t('auth.reset.submit') }}
        </button>
      </form>
      <p v-if="error" class="auth-links">
        <router-link to="/wachtwoord-vergeten">{{ t('auth.reset.requestNew') }}</router-link>
      </p>
    </template>
  </AuthShell>
</template>

<style scoped>
.auth-links {
  margin-top: var(--space-4);
  font-size: var(--text-sm);
}

.next-step {
  margin-top: var(--space-4);
}
</style>
