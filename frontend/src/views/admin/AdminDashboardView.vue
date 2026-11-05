<script setup>
import { onMounted, ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import { adminApi } from '../../services/api'
import { t } from '../../utils/i18n'

const status = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    status.value = await adminApi.status()
  } catch (exception) {
    error.value = exception.message
  }
})
</script>

<template>
  <h1>{{ t('admin.overview') }}</h1>
  <FormMessage v-if="error" class="status">{{ error }}</FormMessage>
  <p v-else-if="status" class="status">{{ t('admin.greeting', { name: status.displayName }) }}</p>
</template>

<style scoped>
.status {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}
</style>
