<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '../components/auth/AuthShell.vue'
import FormField from '../components/FormField.vue'
import FormMessage from '../components/FormMessage.vue'
import { useSession } from '../stores/session'
import { t } from '../utils/i18n'
import { safeRedirect } from '../utils/redirect'

const route = useRoute()
const router = useRouter()
const session = useSession()

const form = reactive({ email: '', password: '' })
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await session.login(form)
    await router.push(safeRedirect(route.query.redirect))
  } catch (exception) {
    error.value = exception.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell :title="t('auth.login.title')">
    <form class="form-stack" novalidate @submit.prevent="submit">
      <FormMessage v-if="error">{{ error }}</FormMessage>
      <FormField v-model="form.email" :label="t('auth.email')" type="email" autocomplete="email" />
      <FormField v-model="form.password" :label="t('auth.password')" type="password" autocomplete="current-password" />
      <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
        {{ t('auth.login.submit') }}
      </button>
    </form>
    <p class="auth-links">
      <router-link to="/wachtwoord-vergeten">{{ t('auth.login.forgot') }}</router-link>
    </p>
    <p class="auth-links">
      {{ t('auth.login.noAccount') }}
      <router-link to="/registreren">{{ t('auth.login.toRegister') }}</router-link>
    </p>
  </AuthShell>
</template>

<style scoped>
.auth-links {
  margin-top: var(--space-4);
  font-size: var(--text-sm);
}
</style>
