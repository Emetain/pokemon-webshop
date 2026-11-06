<script setup>
import { computed, ref } from 'vue'
import { Check, Copy } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const session = useSession()

const busy = ref(false)
const error = ref('')
const copied = ref(false)

const slug = computed(() => session.state.user.collectionShareSlug)
const destination = computed(() => ({ name: 'shared-collection', params: { slug: slug.value } }))
const link = computed(() => `${window.location.origin}/collectie/gedeeld/${slug.value}`)

async function change(action) {
  busy.value = true
  error.value = ''
  copied.value = false
  try {
    await action()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function stop() {
  if (window.confirm(t('account.share.disableConfirm'))) change(session.stopSharingCollection)
}

async function copy() {
  try {
    await navigator.clipboard.writeText(link.value)
    copied.value = true
  } catch {
    // Some browsers refuse this. The link stays visible to copy by hand.
  }
}
</script>

<template>
  <div class="form-stack">
    <p class="intro">{{ t('account.share.intro') }}</p>
    <FormMessage v-if="error">{{ error }}</FormMessage>

    <div>
      <button v-if="!slug" class="btn btn-secondary" type="button" :disabled="busy" @click="change(session.shareCollection)">
        {{ t('account.share.enable') }}
      </button>
    </div>

    <template v-if="slug">
      <div>
        <p class="label">{{ t('account.share.link') }}</p>
        <p class="link">{{ link }}</p>
      </div>
      <div class="actions">
        <button class="btn btn-secondary" type="button" @click="copy">
          <component :is="copied ? Check : Copy" :size="18" aria-hidden="true" />
          {{ copied ? t('account.share.copied') : t('account.share.copy') }}
        </button>
        <router-link class="btn btn-secondary" :to="destination">{{ t('account.share.open') }}</router-link>
        <button class="btn btn-secondary" type="button" :disabled="busy" @click="stop">
          {{ t('account.share.disable') }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.intro {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.label {
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.link {
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
