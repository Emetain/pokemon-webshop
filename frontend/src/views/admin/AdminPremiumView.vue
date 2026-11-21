<script setup>
import { computed, onMounted, reactive, ref, useId } from 'vue'
import FormMessage from '../../components/FormMessage.vue'
import PageSkeleton from '../../components/skeleton/PageSkeleton.vue'
import { adminPremiumApi } from '../../services/api'
import { formatDate } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * Giving Premium without paying (2026-11-21, the owner's wish: for friends who help). The friend makes an account
 * first; a paid subscription is cancelled in Stripe, never here. The server checks all of it again.
 */
const uid = useId()
const field = (name) => `${uid}-${name}`

const members = ref(null)
const error = ref('')
const message = ref('')
const busy = ref(false)
const form = reactive({ email: '', length: 'forever', lastDay: '' })

// A date input wants the day in the browser's own time zone.
const today = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})

async function load() {
  try {
    members.value = await adminPremiumApi.members()
  } catch (exception) {
    error.value = exception.message
  }
}

async function give() {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    const lastDay = form.length === 'until' ? form.lastDay : null
    const member = await adminPremiumApi.grant(form.email, lastDay)
    await load()
    message.value = t('admin.premium.given', {
      email: member.email,
      length: lastDay
        ? t('admin.premium.givenUntil', { date: formatDate(lastDay) })
        : t('admin.premium.givenForever'),
    })
    form.email = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

async function revoke(member) {
  if (!window.confirm(t('admin.premium.revokeConfirm', { email: member.email }))) return
  error.value = ''
  message.value = ''
  try {
    await adminPremiumApi.revoke(member.id)
    await load()
    message.value = t('admin.premium.revoked', { email: member.email })
  } catch (exception) {
    error.value = exception.message
  }
}

const how = (member) => t(`admin.premium.reasons.${member.reason}`, { date: member.lastDay ? formatDate(member.lastDay) : '' })
</script>

<template>
  <h1>{{ t('admin.premium.title') }}</h1>
  <p class="admin-intro">{{ t('admin.premium.intro') }}</p>

  <FormMessage v-if="message" type="success" class="message">{{ message }}</FormMessage>
  <FormMessage v-if="error" class="message">{{ error }}</FormMessage>

  <form class="admin-panel" @submit.prevent="give">
    <div class="admin-fields">
      <div class="admin-field wide">
        <label class="admin-label" :for="field('email')">{{ t('admin.premium.email') }}</label>
        <input
          :id="field('email')"
          v-model="form.email"
          class="admin-input"
          type="email"
          required
          maxlength="254"
          autocomplete="off"
          placeholder="vriend@voorbeeld.nl"
        />
      </div>
      <fieldset class="admin-field wide lengths">
        <legend class="admin-label">{{ t('admin.premium.length') }}</legend>
        <label class="admin-check">
          <input v-model="form.length" type="radio" value="forever" :name="field('length')" />
          {{ t('admin.premium.forever') }}
        </label>
        <label class="admin-check">
          <input v-model="form.length" type="radio" value="until" :name="field('length')" />
          {{ t('admin.premium.until') }}
        </label>
        <input
          v-if="form.length === 'until'"
          v-model="form.lastDay"
          class="admin-input date"
          type="date"
          required
          :min="today"
          :aria-label="t('admin.premium.lastDay')"
        />
      </fieldset>
    </div>
    <p class="admin-hint">{{ t('admin.premium.payingNote') }}</p>
    <div class="admin-actions">
      <button class="btn btn-primary btn-small" type="submit" :disabled="busy">
        {{ busy ? t('admin.premium.giving') : t('admin.premium.give') }}
      </button>
    </div>
  </form>

  <section class="list">
    <h2>{{ t('admin.premium.listTitle') }}</h2>
    <p class="admin-hint">{{ t('admin.premium.listIntro') }}</p>

    <PageSkeleton v-if="!members && !error" kind="table" :page="false" :heading="false" />
    <p v-else-if="members && !members.length" class="admin-intro">{{ t('admin.premium.empty') }}</p>

    <div v-else-if="members" class="admin-table-scroll">
      <table class="admin-table">
        <thead>
          <tr>
            <th scope="col">{{ t('admin.premium.name') }}</th>
            <th scope="col">{{ t('admin.premium.how') }}</th>
            <th scope="col">{{ t('admin.premium.since') }}</th>
            <th scope="col"><span class="visually-hidden">{{ t('admin.premium.revoke') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in members" :key="member.id">
            <td>
              <strong>{{ member.displayName }}</strong>
              <span class="email">{{ member.email }}</span>
            </td>
            <td>{{ how(member) }}</td>
            <td>{{ member.since ? formatDate(member.since) : '' }}</td>
            <td class="action">
              <button
                v-if="member.canRevoke"
                class="revoke"
                type="button"
                :aria-label="t('admin.premium.revokeLabel', { email: member.email })"
                @click="revoke(member)"
              >
                {{ t('admin.premium.revoke') }}
              </button>
              <span v-else class="hint">{{ t('admin.premium.inStripe') }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.message {
  margin-bottom: var(--space-4);
}

.lengths {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  padding: 0;
  border: 0;
}

.lengths legend {
  width: 100%;
  margin-bottom: var(--space-1);
}

.date {
  width: auto;
}

.list {
  margin-top: var(--space-7);
}

.list h2 {
  font-size: var(--text-lg);
}

.email {
  display: block;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.action {
  text-align: right;
  white-space: nowrap;
}

.revoke {
  min-height: var(--touch-target);
  padding: 0 var(--space-2);
  border: 0;
  background: transparent;
  color: var(--color-danger);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 650;
}

.hint {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}
</style>
