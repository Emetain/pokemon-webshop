<script setup>
import { onMounted, ref } from 'vue'
import { Crown, ExternalLink } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { premiumApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/** Premium in My account (phase 11): the status, the next payment, and Stripe's portal to cancel or switch. */
const status = ref(null)
const error = ref('')
const opening = ref(false)

onMounted(async () => {
  try {
    status.value = await premiumApi.status()
  } catch (exception) {
    error.value = exception.message
  }
})

async function manage() {
  opening.value = true
  error.value = ''
  try {
    const { url } = await premiumApi.portal()
    window.location.assign(url)
  } catch (exception) {
    error.value = exception.message
    opening.value = false
  }
}
</script>

<template>
  <div class="premium-panel">
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <template v-if="status">
      <p v-if="status.premium" class="state on">
        <Crown :size="18" aria-hidden="true" />
        {{ status.plan ? t(`account.premium.plan.${status.plan}`) : t('account.premium.on') }}
      </p>
      <p v-else class="state">{{ t('account.premium.off') }}</p>

      <p v-if="status.reason === 'ADMIN'" class="detail">{{ t('premium.admin') }}</p>
      <p v-else-if="status.reason === 'FOREVER'" class="detail">{{ t('premium.forever') }}</p>
      <template v-else-if="status.premium">
        <p v-if="status.since" class="detail">{{ t('account.premium.since', { date: formatDate(status.since) }) }}</p>
        <p v-if="status.cancelAtPeriodEnd" class="detail">{{ t('premium.endsOn', { date: formatDate(status.until) }) }}</p>
        <p v-else-if="status.nextPaymentAt" class="detail">
          {{ t('account.premium.nextPayment', { date: formatDate(status.nextPaymentAt) }) }}
        </p>
      </template>
      <p v-else-if="status.subscriptionStatus === 'past_due'" class="detail warning">{{ t('account.premium.pastDue') }}</p>
      <p v-else-if="status.freeScansLeft !== null" class="detail">
        {{ t('premium.freeScansLeft', { count: status.freeScansLeft, total: status.freeScans }) }}
      </p>

      <div class="actions">
        <button v-if="status.canManage" class="btn btn-secondary" type="button" :disabled="opening" @click="manage">
          <ExternalLink :size="16" aria-hidden="true" />
          {{ t('account.premium.manage') }}
        </button>
        <router-link v-if="!status.premium" class="btn btn-primary" to="/premium">{{ t('account.premium.get') }}</router-link>
      </div>
      <p v-if="status.canManage" class="hint">{{ t('account.premium.manageHint') }}</p>
    </template>
  </div>
</template>

<style scoped>
.premium-panel {
  display: grid;
  gap: var(--space-2);
}

.state {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 700;
}

.state.on {
  color: var(--color-brand);
}

.detail,
.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.warning {
  color: var(--color-warning);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-1);
}
</style>
