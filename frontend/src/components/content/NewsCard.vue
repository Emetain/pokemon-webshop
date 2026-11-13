<script setup>
import { Newspaper } from 'lucide-vue-next'
import { findGame } from '../../data/games'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

defineProps({
  article: { type: Object, required: true },
})
</script>

<template>
  <article class="news-card" :data-game="findGame(article.game)?.accent">
    <router-link class="link" :to="`/nieuws/${article.slug}`">
      <span class="picture">
        <img v-if="article.imageUrl" :src="article.imageUrl" alt="" loading="lazy" decoding="async" />
        <Newspaper v-else :size="36" aria-hidden="true" />
      </span>
      <span class="meta">
        <span v-if="article.game" class="game">{{ t(findGame(article.game)?.labelKey ?? '') }}</span>
        <time v-if="article.publishedAt" :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
      </span>
      <h3 class="title">{{ article.title }}</h3>
      <p v-if="article.summary" class="summary">{{ article.summary }}</p>
    </router-link>
  </article>
</template>

<style scoped>
.link {
  display: grid;
  gap: var(--space-2);
  color: inherit;
  text-decoration: none;
}

.link:hover .title {
  color: var(--color-link);
  text-decoration: underline;
}

.picture {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-md);
  background: var(--color-tint);
  color: var(--accent);
}

.picture img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.game {
  color: var(--accent);
  font-weight: 700;
}

.title {
  font-size: var(--text-lg);
}

.summary {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
