<script setup>
import { ref, watch } from 'vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import CollectionSummary from '../../components/collection/CollectionSummary.vue'
import { collectionApi } from '../../services/api'
import { t } from '../../utils/i18n'

const props = defineProps({
  slug: { type: String, required: true },
})

const shared = ref(null)
const loading = ref(true)
const notFound = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  notFound.value = false
  error.value = ''
  try {
    shared.value = await collectionApi.shared(props.slug)
  } catch (exception) {
    shared.value = null
    if (exception.status === 404) notFound.value = true
    else error.value = exception.message
  } finally {
    loading.value = false
  }
}

watch(() => props.slug, load, { immediate: true })
</script>

<template>
  <CatalogBand accent="pokemon">
    <h1>{{ shared ? t('collection.sharedTitle', { name: shared.ownerName }) : t('collection.title') }}</h1>
  </CatalogBand>

  <div class="container page">
    <CatalogNotice
      v-if="notFound"
      :title="t('collection.shared.notFoundTitle')"
      :text="t('collection.shared.notFoundText')"
    />

    <CatalogNotice v-else-if="error" :title="error">
      <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
    </CatalogNotice>

    <p v-else-if="loading" class="loading" role="status">{{ t('catalog.loading') }}</p>

    <CatalogNotice v-else-if="shared.collection.copies === 0" :title="t('collection.empty.sharedTitle')" />

    <CollectionSummary v-else :collection="shared.collection" />
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-5) 0;
}

.loading {
  color: var(--color-ink-soft);
}
</style>
