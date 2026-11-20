<script setup>
import { onMounted, ref } from 'vue'
import { MailCheck, MailX } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import { contentApi } from '../../services/api'
import { t } from '../../utils/i18n'

/** Where the links in the newsletter mails land: `confirm` or `unsubscribe`. */
const props = defineProps({
  mode: { type: String, required: true },
  token: { type: String, default: undefined },
})

const state = ref('busy')
const error = ref('')

onMounted(async () => {
  try {
    if (!props.token) throw new Error(t('newsletter.noToken'))
    if (props.mode === 'confirm') await contentApi.confirm(props.token)
    else await contentApi.unsubscribe(props.token)
    state.value = 'done'
  } catch (exception) {
    error.value = exception.message
    state.value = 'error'
  }
})
</script>

<template>
  <div class="container page">
    <component :is="mode === 'confirm' ? MailCheck : MailX" class="icon" :size="40" aria-hidden="true" />
    <h1>{{ mode === 'confirm' ? t('newsletter.confirmTitle') : t('newsletter.unsubscribeTitle') }}</h1>
    <p v-if="state === 'busy'" class="muted" aria-busy="true">{{ t('newsletter.working') }}</p>
    <p v-else-if="state === 'done'" class="text">
      {{ mode === 'confirm' ? t('newsletter.confirmed') : t('newsletter.unsubscribed') }}
    </p>
    <FormMessage v-else class="text">{{ error }}</FormMessage>
    <router-link class="btn btn-primary" to="/">{{ t('newsletter.toShop') }}</router-link>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  max-width: 40rem;
  padding-block: var(--space-7) 0;
}

.icon {
  color: var(--color-brand-ink);
}

.muted {
  color: var(--color-ink-soft);
}
</style>
