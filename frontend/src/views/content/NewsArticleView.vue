<script setup>
import { ref, watch } from 'vue'
import { ChevronLeft } from 'lucide-vue-next'
import FormMessage from '../../components/FormMessage.vue'
import RichText from '../../components/content/RichText.vue'
import { findGame } from '../../data/games'
import { contentApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

const props = defineProps({
  slug: { type: String, required: true },
})

const article = ref(null)
const error = ref('')

watch(
  () => props.slug,
  async () => {
    article.value = null
    error.value = ''
    try {
      article.value = await contentApi.article(props.slug)
    } catch (exception) {
      error.value = exception.message
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="container page">
    <router-link class="back" to="/nieuws">
      <ChevronLeft :size="18" aria-hidden="true" />
      {{ t('news.back') }}
    </router-link>
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <p v-else-if="!article" class="muted" aria-busy="true">{{ t('news.loading') }}</p>
    <article v-else class="article" :data-game="findGame(article.game)?.accent">
      <p v-if="!article.published" class="draft">{{ t('news.preview') }}</p>
      <p class="meta">
        <span v-if="article.game" class="game">{{ t(findGame(article.game)?.labelKey ?? '') }}</span>
        <time v-if="article.publishedAt" :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
      </p>
      <h1>{{ article.title }}</h1>
      <p v-if="article.summary" class="summary">{{ article.summary }}</p>
      <img v-if="article.imageUrl" class="picture" :src="article.imageUrl" alt="" decoding="async" />
      <RichText class="body" :text="article.body" />
    </article>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-4) 0;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.muted {
  color: var(--color-ink-soft);
}

.article {
  max-width: 48rem;
}

.draft {
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-weight: 600;
}

.meta {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.game {
  color: var(--accent);
  font-weight: 700;
}

.summary {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-lg);
}

.picture {
  width: 100%;
  margin-top: var(--space-5);
  border-radius: var(--radius-md);
}

.body {
  margin-top: var(--space-5);
}
</style>
