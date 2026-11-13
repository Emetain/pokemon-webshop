<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Star } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import StarRating from './StarRating.vue'
import { reviewApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Approved reviews of a product, and the form for a customer who bought it. */
const props = defineProps({
  slug: { type: String, required: true },
})

const emit = defineEmits(['loaded'])

const route = useRoute()
const session = useSession()

const data = ref(null)
const reviews = ref([])
const error = ref('')
const writing = ref(false)
const busy = ref(false)
const saved = ref(false)
const form = reactive({ rating: 0, title: '', text: '' })

async function load(page = 0) {
  try {
    const result = await reviewApi.list(props.slug, page)
    data.value = result
    reviews.value = page === 0 ? result.reviews : [...reviews.value, ...result.reviews]
    emit('loaded', result)
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => props.slug, () => load(0), { immediate: true })

const hasMore = computed(() => data.value && data.value.page + 1 < data.value.totalPages)

function startWriting() {
  const own = data.value?.own
  Object.assign(form, { rating: own?.rating ?? 0, title: own?.title ?? '', text: own?.text ?? '' })
  writing.value = true
  saved.value = false
}

async function submit() {
  error.value = ''
  if (!form.rating) {
    error.value = t('reviews.chooseStars')
    return
  }
  busy.value = true
  try {
    data.value = await reviewApi.write(props.slug, { ...form, title: form.title || null })
    reviews.value = data.value.reviews
    writing.value = false
    saved.value = true
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const loginLink = computed(() => ({ path: '/inloggen', query: { redirect: `${route.fullPath}#reviews` } }))
</script>

<template>
  <section id="reviews" class="reviews" aria-labelledby="reviews-title">
    <div class="head">
      <h2 id="reviews-title">{{ t('reviews.title') }}</h2>
      <p v-if="data?.count" class="summary">
        <StarRating :rating="data.averageRating" :size="18" />
        <strong>{{ String(data.averageRating).replace('.', ',') }}</strong>
        <span class="muted">{{ t(data.count === 1 ? 'reviews.countOne' : 'reviews.count', { count: data.count }) }}</span>
      </p>
    </div>

    <FormMessage v-if="error && !writing">{{ error }}</FormMessage>
    <FormMessage v-if="saved" type="success">{{ t('reviews.saved') }}</FormMessage>

    <template v-if="data">
      <div v-if="data.own && !writing" class="own">
        <p>
          <strong>{{ t('reviews.yours') }}</strong>
          <span class="status" :class="data.own.status.toLowerCase()">{{ t(`reviews.status.${data.own.status}`) }}</span>
        </p>
        <button class="link-button" type="button" @click="startWriting">{{ t('reviews.edit') }}</button>
      </div>
      <button v-else-if="data.canReview && !writing" class="btn btn-secondary btn-small" type="button" @click="startWriting">
        <Star :size="16" aria-hidden="true" />
        {{ t('reviews.write') }}
      </button>
      <p v-else-if="!session.isLoggedIn.value && !writing" class="muted small">
        {{ t('reviews.onlyBuyers') }}
        <router-link :to="loginLink">{{ t('reviews.login') }}</router-link>
      </p>

      <form v-if="writing" class="form" novalidate @submit.prevent="submit">
        <fieldset class="rating">
          <legend class="label">{{ t('reviews.yourRating') }}</legend>
          <label v-for="value in 5" :key="value" class="rating-option">
            <input v-model="form.rating" class="visually-hidden" type="radio" name="rating" :value="value" />
            <Star class="rating-star" :class="{ on: form.rating >= value }" :size="28" aria-hidden="true" />
            <span class="visually-hidden">{{ t('reviews.starsOption', { count: value }) }}</span>
          </label>
        </fieldset>
        <div>
          <label class="label" for="review-title">{{ t('reviews.titleField') }}</label>
          <input id="review-title" v-model="form.title" class="input" maxlength="100" />
        </div>
        <div>
          <label class="label" for="review-text">{{ t('reviews.text') }}</label>
          <textarea id="review-text" v-model="form.text" class="input" rows="5" maxlength="1000"></textarea>
          <p class="muted small">{{ t('reviews.textHint') }}</p>
        </div>
        <FormMessage v-if="error">{{ error }}</FormMessage>
        <div class="buttons">
          <button class="btn btn-primary btn-small" type="submit" :disabled="busy">{{ t('reviews.send') }}</button>
          <button class="link-button" type="button" @click="writing = false">{{ t('reviews.cancel') }}</button>
        </div>
      </form>

      <p v-if="!reviews.length" class="muted">{{ t('reviews.empty') }}</p>
      <ul v-else class="list">
        <li v-for="review in reviews" :key="review.id" class="review">
          <StarRating :rating="review.rating" />
          <h3 v-if="review.title" class="review-title">{{ review.title }}</h3>
          <p class="review-text">{{ review.text }}</p>
          <p class="muted small">{{ t('reviews.by', { name: review.authorName, date: formatDate(review.createdAt) }) }}</p>
        </li>
      </ul>
      <button v-if="hasMore" class="btn btn-secondary btn-small" type="button" @click="load(data.page + 1)">
        {{ t('reviews.more') }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.reviews {
  display: grid;
  gap: var(--space-3);
  align-content: start;
  justify-items: start;
  scroll-margin-top: 8rem;
}

.reviews > * {
  max-width: 100%;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-4);
}

.summary {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.muted {
  color: var(--color-ink-soft);
}

.small {
  font-size: var(--text-sm);
}

.own {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-3);
}

.status {
  margin-left: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 700;
}

.status.pending {
  color: var(--color-warning);
}

.status.approved {
  color: var(--color-success);
}

.status.rejected {
  color: var(--color-danger);
}

.form {
  display: grid;
  gap: var(--space-3);
  width: min(100%, 36rem);
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}

.label {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.rating {
  display: flex;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  border: 0;
}

.rating legend {
  float: left;
  width: 100%;
}

.rating-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.rating-option:focus-within {
  outline: 3px solid var(--color-focus);
}

.rating-star {
  color: var(--color-line-strong);
}

.rating-star.on {
  color: var(--color-warning);
  fill: currentColor;
}

.input {
  width: 100%;
  min-height: var(--touch-target);
  padding: var(--space-2) var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  font: inherit;
}

.buttons {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.list {
  display: grid;
  gap: var(--space-3);
  width: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review {
  display: grid;
  gap: var(--space-1);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-line);
}

.review-title {
  font-size: var(--text-md);
}

.review-text {
  max-width: 68ch;
  white-space: pre-line;
}
</style>
