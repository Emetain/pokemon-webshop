<script setup>
import { ref, watch } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import PageLinks from '../../components/catalog/PageLinks.vue'
import StarRating from '../../components/reviews/StarRating.vue'
import { adminContentApi } from '../../services/api'
import { formatDateTime } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Reviews wait here until an admin approves them. Pending ones come first by default. */
const props = defineProps({
  status: { type: String, default: undefined },
  page: { type: String, default: undefined },
})

const STATUSES = ['PENDING', 'APPROVED', 'REJECTED']

const result = ref(null)
const error = ref('')
const busy = ref(false)

const current = () => props.status ?? 'PENDING'

async function load() {
  try {
    const status = current() === 'ALL' ? undefined : current()
    result.value = await adminContentApi.reviews({ status, page: props.page ? Number(props.page) - 1 : 0 })
  } catch (exception) {
    error.value = exception.message
  }
}

watch(() => [props.status, props.page], load, { immediate: true })

async function act(action) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function remove(review) {
  if (window.confirm(t('admin.reviews.deleteConfirm'))) act(() => adminContentApi.deleteReview(review.id))
}
</script>

<template>
  <h1>{{ t('admin.reviews.title') }}</h1>
  <p class="admin-intro">{{ t('admin.reviews.intro') }}</p>

  <nav class="tabs" :aria-label="t('admin.reviews.filter')">
    <router-link
      v-for="status in [...STATUSES, 'ALL']"
      :key="status"
      class="tab"
      :class="{ active: current() === status }"
      :to="{ query: { status } }"
    >
      {{ t(`admin.reviews.statuses.${status}`) }}
      <span v-if="status === 'PENDING' && result?.pendingCount" class="count">{{ result.pendingCount }}</span>
    </router-link>
  </nav>

  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>
  <p v-if="result && !result.reviews.length" class="admin-intro message">{{ t('admin.reviews.empty') }}</p>

  <ul v-if="result?.reviews.length" class="list">
    <li v-for="review in result.reviews" :key="review.id" class="review admin-panel">
      <div class="top">
        <StarRating :rating="review.rating" />
        <router-link :to="`/product/${review.productSlug}`" target="_blank">{{ review.productName }}</router-link>
        <span class="state" :class="review.status.toLowerCase()">{{ t(`admin.reviews.statuses.${review.status}`) }}</span>
      </div>
      <p v-if="review.title" class="title">{{ review.title }}</p>
      <p class="text">{{ review.text }}</p>
      <p class="admin-hint">
        {{ t('admin.reviews.by', { name: review.authorName, email: review.customerEmail, date: formatDateTime(review.updatedAt) }) }}
      </p>
      <div class="admin-actions">
        <button
          v-if="review.status !== 'APPROVED'"
          class="btn btn-primary btn-small"
          type="button"
          :disabled="busy"
          @click="act(() => adminContentApi.approveReview(review.id))"
        >
          {{ t('admin.reviews.approve') }}
        </button>
        <button
          v-if="review.status !== 'REJECTED'"
          class="btn btn-secondary btn-small"
          type="button"
          :disabled="busy"
          @click="act(() => adminContentApi.rejectReview(review.id))"
        >
          {{ t('admin.reviews.reject') }}
        </button>
        <button class="link-button danger" type="button" :disabled="busy" @click="remove(review)">
          {{ t('admin.reviews.delete') }}
        </button>
      </div>
    </li>
  </ul>
  <PageLinks v-if="result?.totalPages > 1" :page="result.page + 1" :total-pages="result.totalPages" />
</template>

<style scoped>
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.tab.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.count {
  padding: 0 var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-sale);
  color: var(--color-on-brand);
  font-size: var(--text-xs);
}

.message {
  margin-top: var(--space-4);
}

.list {
  display: grid;
  gap: var(--space-3);
  max-width: 50rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  font-size: var(--text-sm);
}

.state {
  margin-left: auto;
  font-weight: 700;
}

.state.pending {
  color: var(--color-warning);
}

.state.approved {
  color: var(--color-success);
}

.state.rejected {
  color: var(--color-danger);
}

.title {
  font-weight: 700;
}

.text {
  white-space: pre-line;
}

.admin-actions {
  margin-top: var(--space-1);
}
</style>
