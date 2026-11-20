<script setup>
import { computed, ref } from 'vue'
import { BellRing, BookCopy, FileDown } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { collectionApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * The Premium tools of the collection page (phase 12): the binders, the price alerts and the export. Binders and
 * alerts are pages a former member still opens to look at or clean up; exporting is Premium only.
 */
const session = useSession()
const premium = computed(() => session.isPremium.value)

const working = ref('')
const error = ref('')

async function download(format) {
  if (!premium.value) {
    openPremium('export')
    return
  }
  error.value = ''
  working.value = format
  try {
    await collectionApi.exportFile(format)
  } catch (exception) {
    error.value = exception.message
  } finally {
    working.value = ''
  }
}
</script>

<template>
  <section class="tools">
    <router-link class="tool" to="/collectie/binders">
      <BookCopy :size="22" aria-hidden="true" />
      <span>
        <strong>{{ t('binders.title') }}</strong>
        <small>{{ t('premium.features.binders.text') }}</small>
      </span>
    </router-link>

    <router-link class="tool" to="/prijsalerts">
      <BellRing :size="22" aria-hidden="true" />
      <span>
        <strong>{{ t('priceAlerts.title') }}</strong>
        <small>{{ t('premium.features.alerts.text') }}</small>
      </span>
    </router-link>

    <div class="tool export">
      <FileDown :size="22" aria-hidden="true" />
      <span>
        <strong>{{ t('exports.title') }}</strong>
        <small>{{ t('exports.intro') }}</small>
        <span class="buttons">
          <button class="btn btn-secondary" type="button" :disabled="Boolean(working)" @click="download('csv')">
            {{ working === 'csv' ? t('exports.working') : t('exports.csv') }}
          </button>
          <button class="btn btn-secondary" type="button" :disabled="Boolean(working)" @click="download('pdf')">
            {{ working === 'pdf' ? t('exports.working') : t('exports.pdf') }}
          </button>
        </span>
      </span>
    </div>
    <FormMessage v-if="error" class="error">{{ error }}</FormMessage>
  </section>
</template>

<style scoped>
.tools {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-7);
}

.tool {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-ink);
  text-decoration: none;
}

.tool > svg {
  flex: none;
  color: var(--color-brand-ink);
}

.tool strong {
  display: block;
  font-size: var(--text-md);
}

.tool small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

@media (hover: hover) {
  a.tool:hover {
    border-color: var(--color-brand);
  }

  a.tool:hover strong {
    color: var(--color-brand-ink);
  }
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.error {
  grid-column: 1 / -1;
}

@media (min-width: 900px) {
  .tools {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
