<script setup>
import { ref, watch } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'
import SupportFlow from '../../components/support/SupportFlow.vue'
import FormMessage from '../../components/FormMessage.vue'
import RichText from '../../components/content/RichText.vue'
import { checkoutApi, contentApi } from '../../services/api'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'
import { usePageMeta } from '../../utils/pageMeta'
import NotFoundView from '../NotFoundView.vue'

/** A page under /info with the text from admin. Contact gets the form, shipping the rates as they are set now. */
const props = defineProps({
  slug: { type: String, required: true },
})

const page = ref(null)
const rates = ref([])
const error = ref('')
const notFound = ref(false)

async function load() {
  page.value = null
  error.value = ''
  notFound.value = false
  try {
    page.value = await contentApi.page(props.slug)
    if (props.slug === 'verzending') rates.value = (await checkoutApi.options()).countries
  } catch (exception) {
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  }
}

watch(() => props.slug, load, { immediate: true })

// A draft, such as a legal page that hasn't been checked yet, stays out of search engines.
usePageMeta(() => (page.value ? { title: page.value.title, description: page.value.body, noindex: page.value.draft } : null))
</script>

<template>
  <NotFoundView v-if="notFound" />
  <div v-else class="container page">
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <p v-else-if="!page" class="muted" aria-busy="true">{{ t('info.loading') }}</p>
    <article v-else>
      <h1>{{ page.title }}</h1>
      <p v-if="page.draft" class="draft" role="note">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ t('info.draft') }}
      </p>
      <RichText class="text" :text="page.body" />

      <div v-if="slug === 'verzending' && rates.length" class="rates-scroll">
      <table class="rates">
        <caption>{{ t('info.ratesTitle') }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ t('info.country') }}</th>
            <th scope="col">{{ t('info.price') }}</th>
            <th scope="col">{{ t('info.freeFrom') }}</th>
            <th scope="col">{{ t('info.deliveryTime') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="rate in rates" :key="rate.country">
            <td>{{ t(`countries.${rate.country}`) }}</td>
            <td>{{ formatMoney(rate.priceCents, 'EUR') }}</td>
            <td>{{ rate.freeFromCents !== null ? formatMoney(rate.freeFromCents, 'EUR') : t('info.never') }}</td>
            <td>{{ rate.deliveryTime }}</td>
          </tr>
        </tbody>
      </table>
      </div>

      <SupportFlow v-if="slug === 'contact'" class="contact" />

      <p class="updated">{{ t('info.updated', { date: formatDate(page.updatedAt) }) }}</p>
    </article>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-6) 0;
}

.muted {
  color: var(--color-ink-soft);
}

.draft {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  max-width: 68ch;
  margin-top: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-weight: 600;
}

.text {
  margin-top: var(--space-4);
}

.rates-scroll {
  max-width: 44rem;
  margin-top: var(--space-5);
  overflow-x: auto;
}

.rates {
  width: 100%;
  min-width: 30rem;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.rates caption {
  margin-bottom: var(--space-2);
  font-weight: 700;
  text-align: left;
}

.rates th,
.rates td {
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-line);
  text-align: left;
}

.rates th {
  background: var(--color-tint);
}

.contact {
  margin-top: var(--space-6);
}

.updated {
  margin-top: var(--space-6);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
