<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Crown, X } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import PremiumBuyForm from './PremiumBuyForm.vue'
import PremiumFeatureList from './PremiumFeatureList.vue'
import { premiumApi } from '../../services/api'
import { closePremium, usePremiumDialog } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * The Premium popup: what the feature needs, what Premium adds, and the plans with the way to Stripe, without leaving
 * the page. Opened through stores/premiumDialog.js. The browser's modal <dialog> keeps the focus inside, closes on
 * Escape and gives the focus back to the button or link that opened it.
 */
const { state } = usePremiumDialog()
const session = useSession()
const route = useRoute()

const dialog = ref(null)
const status = ref(null)
const error = ref('')

const FEATURES = ['general', 'scanner', 'scansUsed', 'history', 'pregrade', 'cardHistory', 'movers', 'alerts', 'export', 'binders', 'themes']
const feature = computed(() => (FEATURES.includes(state.feature) ? state.feature : 'general'))
const lead = computed(() => state.message || t(`premium.dialog.${feature.value}.text`))
const loggedIn = computed(() => session.isLoggedIn.value)
/** After logging in, back to the page the customer was on, or to the Premium page a link pointed to. */
const loginTarget = computed(() => ({ path: '/inloggen', query: { redirect: state.returnPath || route.fullPath } }))

watch(
  () => state.open,
  (open) => (open ? show() : dialog.value?.open && dialog.value.close()),
)
// A link inside the popup (logging in, the terms) leaves the page: the popup goes with it.
watch(
  () => route.fullPath,
  () => closePremium(),
)

async function show() {
  error.value = ''
  if (!dialog.value.open) dialog.value.showModal()
  if (!loggedIn.value) return
  try {
    status.value = await premiumApi.status()
    // Premium after all, for example bought in another tab: nothing to sell, and the page may now show the feature.
    if (status.value.premium) {
      closePremium()
      session.refresh()
    }
  } catch (exception) {
    error.value = exception.message
  }
}
</script>

<template>
  <dialog ref="dialog" class="premium-dialog" aria-labelledby="premium-dialog-title" @close="closePremium" @click.self="closePremium">
    <div class="panel">
      <button class="close" type="button" :aria-label="t('premium.dialog.close')" @click="closePremium">
        <X :size="20" aria-hidden="true" />
      </button>

      <p class="eyebrow"><Crown :size="16" aria-hidden="true" /> {{ t('premium.eyebrow') }}</p>
      <h2 id="premium-dialog-title" class="title">{{ t(`premium.dialog.${feature}.title`) }}</h2>
      <p class="lead">{{ lead }}</p>

      <PremiumFeatureList compact class="features" />

      <FormMessage v-if="error">{{ error }}</FormMessage>

      <div v-if="!loggedIn" class="login">
        <p>{{ t('premium.loginText') }}</p>
        <div class="actions">
          <router-link class="btn btn-primary" :to="loginTarget">{{ t('premium.login') }}</router-link>
          <router-link class="btn btn-secondary" to="/registreren">{{ t('premium.register') }}</router-link>
        </div>
      </div>
      <PremiumBuyForm v-else-if="status && !status.premium" :key="state.open" :status="status" />
      <p v-else-if="!error" class="loading" role="status">{{ t('premium.dialog.loading') }}</p>
    </div>
  </dialog>
</template>

<style scoped>
.premium-dialog {
  width: min(34rem, calc(100vw - 2 * var(--space-4)));
  max-height: calc(100dvh - 2 * var(--space-4));
  padding: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: var(--shadow-panel);
}

/* The colour of --color-brand-deep, the scrim of the cart drawer. Written out: older browsers don't pass custom
   properties on to ::backdrop. */
.premium-dialog::backdrop {
  background: rgb(11 42 111 / 50%);
}

/* In from just below, quick and settling, so it feels like it comes from the click. */
.premium-dialog[open] {
  animation: premium-in 220ms cubic-bezier(0.2, 0.9, 0.3, 1);
}

.premium-dialog[open]::backdrop {
  animation: premium-fade 220ms ease-out;
}

@keyframes premium-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
}

@keyframes premium-fade {
  from {
    opacity: 0;
  }
}

/* The page behind doesn't scroll while the popup is open. */
:global(html:has(.premium-dialog[open])) {
  overflow: hidden;
}

.panel {
  position: relative;
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
}

.close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: var(--touch-target);
  height: var(--touch-target);
  border: 0;
  border-radius: var(--radius-round);
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
}

@media (hover: hover) {
  .close:hover {
    background: var(--color-tint);
    color: var(--color-ink);
  }
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-brand-ink);
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.title {
  padding-right: var(--touch-target);
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
}

.lead {
  color: var(--color-ink-soft);
}

.features {
  padding-block: var(--space-2) var(--space-1);
}

.login {
  display: grid;
  gap: var(--space-3);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.loading {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

@media (prefers-reduced-motion: reduce) {
  .premium-dialog[open],
  .premium-dialog[open]::backdrop {
    animation: none;
  }
}
</style>
