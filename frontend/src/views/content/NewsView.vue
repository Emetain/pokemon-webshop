<script setup>
import AdSlot from '../../components/AdSlot.vue'
import { ref, watch } from 'vue'
import { Newspaper } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import GameFilter from '../../components/shop/GameFilter.vue'
import NewsCard from '../../components/content/NewsCard.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import { contentApi } from '../../services/api'
import { t } from '../../utils/i18n'

const props = defineProps({
  game: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const news = ref(null)
const error = ref('')

async function load() {
  error.value = ''
  try {
    news.value = await contentApi.news({ game: props.game, page: props.page ? Number(props.page) - 1 : 0 })
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => [props.game, props.page], load, { immediate: true })
</script>

<template>
  <div class="container page">
    <header class="head">
      <h1><Newspaper class="title-icon" :size="28" aria-hidden="true" />{{ t('news.title') }}</h1>
      <p class="intro">{{ t('news.intro') }}</p>
    </header>
    <GameFilter :current="game ?? null" :label="t('news.filterLabel')" />

    <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
    <p v-else-if="!news" class="muted message" aria-busy="true">{{ t('news.loading') }}</p>
    <p v-else-if="!news.articles.length" class="muted message">{{ t('news.empty') }}</p>
    <template v-else>
      <ul class="grid">
        <li v-for="article in news.articles" :key="article.slug"><NewsCard :article="article" /></li>
      </ul>
      <PageLinks :page="news.page + 1" :total-pages="news.totalPages" />
    </template>
    <AdSlot place="content" />
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-5) 0;
}

.head {
  margin-bottom: var(--space-4);
}

h1 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.title-icon {
  color: var(--color-brand);
}

.intro {
  max-width: 60ch;
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-lg);
}

.message {
  margin-top: var(--space-5);
}

.muted {
  color: var(--color-ink-soft);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 17rem), 1fr));
  gap: var(--space-6) var(--space-4);
  margin: var(--space-5) 0;
  padding: 0;
  list-style: none;
}
</style>
