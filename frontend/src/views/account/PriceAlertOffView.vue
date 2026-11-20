<script setup>
import { onMounted, ref } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import { priceAlertApi } from '../../services/api'
import { t } from '../../utils/i18n'

/** The link in an alert mail: switches that one alert off, also without logging in (phase 12). */
const props = defineProps({
  token: { type: String, default: '' },
})

const done = ref('')
const error = ref('')
const working = ref(true)

onMounted(async () => {
  if (!props.token) {
    error.value = t('priceAlerts.off.noToken')
    working.value = false
    return
  }
  try {
    const result = await priceAlertApi.switchOff(props.token)
    done.value = t('priceAlerts.off.done', { name: result.cardName })
  } catch (exception) {
    error.value = exception.message
  } finally {
    working.value = false
  }
})
</script>

<template>
  <div class="container page">
    <h1>{{ t('priceAlerts.off.title') }}</h1>
    <p v-if="working" class="note" role="status">{{ t('priceAlerts.off.working') }}</p>
    <FormMessage v-if="done" type="success">{{ done }}</FormMessage>
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <router-link v-if="!working" class="manage" to="/prijsalerts">{{ t('priceAlerts.off.manage') }}</router-link>
  </div>
</template>

<style scoped>
.page {
  max-width: 40rem;
  padding-block: var(--space-6) var(--space-7);
}

h1 {
  margin-bottom: var(--space-4);
}

.note {
  color: var(--color-ink-soft);
}

.manage {
  display: inline-block;
  margin-top: var(--space-4);
}
</style>
