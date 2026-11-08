<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Heart } from 'lucide-vue-next'
import { productWishlistApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/** A heart that puts a product on the wishlist. A visitor who isn't logged in goes to the login page first. */
const props = defineProps({
  product: { type: Object, required: true },
  withText: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'error'])

const route = useRoute()
const router = useRouter()
const session = useSession()
const busy = ref(false)

const wished = computed(() => props.product.wished === true)
const label = computed(() =>
  wished.value
    ? t('shop.wishlistRemoveLabel', { name: props.product.name })
    : t('shop.wishlistAddLabel', { name: props.product.name }),
)

async function toggle() {
  if (!session.isLoggedIn.value) {
    router.push({ path: '/inloggen', query: { redirect: route.fullPath } })
    return
  }
  busy.value = true
  try {
    if (wished.value) await productWishlistApi.remove(props.product.id)
    else await productWishlistApi.add(props.product.id)
    emit('change', !wished.value)
  } catch (exception) {
    emit('error', exception.message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button
    class="wishlist-toggle"
    :class="{ wished, 'with-text': withText }"
    type="button"
    :aria-label="withText ? undefined : label"
    :aria-pressed="wished"
    :disabled="busy"
    @click="toggle"
  >
    <Heart :size="20" :fill="wished ? 'currentColor' : 'none'" aria-hidden="true" />
    <span v-if="withText">{{ wished ? t('shop.wishlistRemove') : t('shop.wishlistAdd') }}</span>
  </button>
</template>

<style scoped>
.wishlist-toggle {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-width: var(--touch-target);
  height: var(--touch-target);
  padding: 0;
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink-soft);
  cursor: pointer;
}

.wishlist-toggle:hover:not(:disabled) {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.wishlist-toggle.wished {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.wishlist-toggle:disabled {
  cursor: progress;
  opacity: 0.6;
}

.with-text {
  height: var(--control-height);
  padding-inline: var(--space-4);
  font-weight: 600;
}
</style>
