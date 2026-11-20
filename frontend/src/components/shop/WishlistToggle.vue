<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Heart } from 'lucide-vue-next'
import { productWishlistApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * A heart that puts a product on the wishlist. A visitor who isn't logged in goes to the login page first.
 *
 * The heart changes at once, before the shop has answered, and changes back if saving fails. Taps in quick succession
 * are sent one after the other, so the last one wins.
 */
const props = defineProps({
  product: { type: Object, required: true },
  withText: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'error'])

const route = useRoute()
const router = useRouter()
const session = useSession()

// What the heart shows while the shop is still saving, or null when it shows what the product says.
const optimistic = ref(null)
// Set when a tap fills the heart, for its small pop. Not on load: then nothing happened.
const popped = ref(false)

const wished = computed(() => optimistic.value ?? props.product.wished === true)
const label = computed(() =>
  wished.value
    ? t('shop.wishlistRemoveLabel', { name: props.product.name })
    : t('shop.wishlistAddLabel', { name: props.product.name }),
)

let queue = Promise.resolve()
let pending = 0

function toggle() {
  if (!session.isLoggedIn.value) {
    router.push({ path: '/inloggen', query: { redirect: route.fullPath } })
    return
  }
  const next = !wished.value
  optimistic.value = next
  popped.value = next
  pending++
  queue = queue.then(() => save(next))
}

async function save(next) {
  try {
    if (next) await productWishlistApi.add(props.product.id)
    else await productWishlistApi.remove(props.product.id)
    emit('change', next)
  } catch (exception) {
    emit('error', exception.message)
  } finally {
    pending--
    if (pending === 0) optimistic.value = null
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
    @click="toggle"
  >
    <Heart
      class="heart"
      :class="{ popped }"
      :size="20"
      :fill="wished ? 'currentColor' : 'none'"
      aria-hidden="true"
      @animationend="popped = false"
    />
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
  transition: transform var(--press-out);
}

@media (hover: hover) {
  .wishlist-toggle:hover {
    border-color: var(--color-brand);
    color: var(--color-brand-ink);
  }
}

.wishlist-toggle:active {
  transform: scale(0.92);
  transition: transform var(--press-in);
}

/* A wide button with text gives way less, like the other buttons with text. */
.wishlist-toggle.with-text:active {
  transform: scale(var(--press-scale));
}

.wishlist-toggle.wished {
  border-color: var(--color-brand);
  color: var(--color-brand-ink);
}

/* The heart swells a little as it fills, and settles with a small bounce: the tap gave it a push. */
.heart.popped {
  animation: pop 0.45s var(--ease-spring-bounce);
}

@keyframes pop {
  30% {
    transform: scale(1.3);
  }
}

@media (prefers-reduced-motion: reduce) {
  .heart.popped {
    animation: none;
  }
}

.with-text {
  height: var(--control-height);
  padding-inline: var(--space-4);
  font-weight: 600;
}
</style>
