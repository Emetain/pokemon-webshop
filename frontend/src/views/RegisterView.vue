<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AuthShell from '../components/auth/AuthShell.vue'
import FormField from '../components/FormField.vue'
import FormMessage from '../components/FormMessage.vue'
import { useSession } from '../stores/session'
import { t } from '../utils/i18n'

const router = useRouter()
const session = useSession()

const form = reactive({ displayName: '', email: '', password: '', passwordConfirmation: '' })
const error = ref('')
const mismatch = ref('')
const confirmation = ref(null)
const submitting = ref(false)

watch(() => [form.password, form.passwordConfirmation], () => {
  mismatch.value = ''
})

async function submit() {
  error.value = ''
  if (form.password !== form.passwordConfirmation) {
    mismatch.value = t('auth.passwordsDiffer')
    confirmation.value.focus()
    return
  }
  submitting.value = true
  try {
    await session.register(form)
    await router.push('/account')
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell :title="t('auth.register.title')" :intro="t('auth.register.intro')">
    <form class="form-stack" novalidate @submit.prevent="submit">
      <FormMessage v-if="error">{{ error }}</FormMessage>
      <FormField v-model="form.displayName" :label="t('auth.displayName')" autocomplete="name" />
      <FormField v-model="form.email" :label="t('auth.email')" type="email" autocomplete="email" />
      <FormField
        v-model="form.password"
        :label="t('auth.password')"
        type="password"
        autocomplete="new-password"
        :hint="t('auth.passwordHint')"
      />
      <FormField
        ref="confirmation"
        v-model="form.passwordConfirmation"
        :label="t('auth.repeatPassword')"
        type="password"
        autocomplete="new-password"
        :error="mismatch"
      />
      <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
        {{ t('auth.register.submit') }}
      </button>
    </form>
    <p class="auth-links">
      {{ t('auth.register.hasAccount') }}
      <router-link to="/inloggen">{{ t('auth.register.toLogin') }}</router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.auth-links {
  margin-top: var(--space-4);
  font-size: var(--text-sm);
}
</style>
