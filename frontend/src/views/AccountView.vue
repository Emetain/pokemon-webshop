<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LogOut, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import AddressForm from '../components/account/AddressForm.vue'
import CountrySelect from '../components/account/CountrySelect.vue'
import FormField from '../components/FormField.vue'
import FormMessage from '../components/FormMessage.vue'
import { accountApi } from '../services/api'
import { useRegion } from '../stores/region'
import { useSession } from '../stores/session'
import { formatDate } from '../utils/format'
import { t } from '../utils/i18n'

const router = useRouter()
const session = useSession()
const region = useRegion()

const user = computed(() => session.state.user)

const profile = reactive({
  displayName: user.value.displayName,
  saving: false,
  saved: false,
  error: '',
})

async function saveProfile() {
  profile.error = ''
  profile.saved = false
  profile.saving = true
  try {
    await session.updateProfile({ displayName: profile.displayName })
    profile.saved = true
  } catch (exception) {
    profile.error = exception.message
  } finally {
    profile.saving = false
  }
}

const countryStatus = reactive({ saved: false, error: '' })

const selectedCountry = computed({
  get: () => region.country.value,
  set: async (country) => {
    countryStatus.error = ''
    countryStatus.saved = false
    try {
      await region.setCountry(country)
      countryStatus.saved = true
    } catch (exception) {
      countryStatus.error = exception.message
    }
  },
})

const addresses = ref([])
const addressesLoading = ref(true)
const addressesError = ref('')
// null: no form open. 'new': adding an address. A number: the id of the address being changed.
const editing = ref(null)

async function loadAddresses() {
  addressesLoading.value = true
  addressesError.value = ''
  try {
    addresses.value = await accountApi.addresses()
  } catch (exception) {
    addressesError.value = exception.message
  } finally {
    addressesLoading.value = false
  }
}

async function saveAddress(data) {
  if (editing.value === 'new') {
    await accountApi.createAddress(data)
  } else {
    await accountApi.updateAddress(editing.value, data)
  }
  editing.value = null
  await loadAddresses()
}

async function deleteAddress(address) {
  if (!window.confirm(t('account.addresses.deleteConfirm'))) return
  addressesError.value = ''
  try {
    await accountApi.deleteAddress(address.id)
    await loadAddresses()
  } catch (exception) {
    addressesError.value = exception.message
  }
}

const logoutError = ref('')

async function logout() {
  logoutError.value = ''
  try {
    await session.logout()
    await router.push('/')
  } catch (exception) {
    logoutError.value = exception.message
  }
}

onMounted(loadAddresses)
</script>

<template>
  <div v-if="user" class="container account">
    <header class="account-head">
      <div>
        <h1>{{ t('account.title') }}</h1>
        <p class="member-since">{{ t('account.memberSince', { date: formatDate(user.createdAt) }) }}</p>
      </div>
      <div class="head-actions">
        <router-link v-if="session.isAdmin.value" class="btn btn-secondary" to="/admin">
          {{ t('account.toAdmin') }}
        </router-link>
        <button class="btn btn-secondary" type="button" @click="logout">
          <LogOut :size="18" aria-hidden="true" />
          {{ t('account.logout') }}
        </button>
      </div>
    </header>
    <FormMessage v-if="logoutError">{{ logoutError }}</FormMessage>

    <section class="account-section">
      <h2>{{ t('account.profile.title') }}</h2>
      <form class="section-body form-stack" novalidate @submit.prevent="saveProfile">
        <FormMessage v-if="profile.error">{{ profile.error }}</FormMessage>
        <FormMessage v-if="profile.saved" type="success">{{ t('account.profile.saved') }}</FormMessage>
        <FormField v-model="profile.displayName" :label="t('account.profile.name')" autocomplete="name" />
        <div>
          <p class="static-label">{{ t('account.profile.email') }}</p>
          <p class="static-value">{{ user.email }}</p>
        </div>
        <div>
          <button class="btn btn-primary" type="submit" :disabled="profile.saving">
            {{ t('account.profile.save') }}
          </button>
        </div>
      </form>
    </section>

    <section class="account-section">
      <h2>{{ t('account.country.title') }}</h2>
      <div class="section-body form-stack">
        <p class="section-intro">{{ t('account.country.intro') }}</p>
        <FormMessage v-if="countryStatus.error">{{ countryStatus.error }}</FormMessage>
        <FormMessage v-if="countryStatus.saved" type="success">{{ t('account.country.saved') }}</FormMessage>
        <CountrySelect v-model="selectedCountry" :label="t('account.country.label')" />
      </div>
    </section>

    <section class="account-section">
      <h2>{{ t('account.addresses.title') }}</h2>
      <div class="section-body">
        <FormMessage v-if="addressesError">{{ addressesError }}</FormMessage>
        <p v-if="addressesLoading" class="section-intro">{{ t('account.addresses.loading') }}</p>
        <p v-else-if="!addresses.length && editing === null" class="section-intro">
          {{ t('account.addresses.empty') }}
        </p>

        <ul class="address-list">
          <li v-for="address in addresses" :key="address.id" class="address">
            <AddressForm
              v-if="editing === address.id"
              :address="address"
              :save="saveAddress"
              @cancel="editing = null"
            />
            <template v-else>
              <address class="address-text">
                <strong>{{ address.name }}</strong><br />
                {{ address.street }} {{ address.houseNumber }}<br />
                {{ address.postcode }} {{ address.city }}<br />
                {{ t(`countries.${address.country}`) }}
              </address>
              <div class="address-actions">
                <button
                  class="text-button"
                  type="button"
                  :aria-label="t('account.addresses.editLabel', { name: address.name })"
                  @click="editing = address.id"
                >
                  <Pencil :size="16" aria-hidden="true" />
                  {{ t('account.addresses.edit') }}
                </button>
                <button
                  class="text-button danger"
                  type="button"
                  :aria-label="t('account.addresses.deleteLabel', { name: address.name })"
                  @click="deleteAddress(address)"
                >
                  <Trash2 :size="16" aria-hidden="true" />
                  {{ t('account.addresses.delete') }}
                </button>
              </div>
            </template>
          </li>
        </ul>

        <AddressForm v-if="editing === 'new'" :save="saveAddress" @cancel="editing = null" />
        <button v-else class="btn btn-secondary" type="button" @click="editing = 'new'">
          <Plus :size="18" aria-hidden="true" />
          {{ t('account.addresses.add') }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.account {
  max-width: 56rem;
  padding-block: var(--space-6) 0;
}

.account-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.member-since {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.account-section {
  display: grid;
  gap: var(--space-3);
  padding-block: var(--space-5);
  border-top: 1px solid var(--color-line);
}

.section-body {
  min-width: 0;
  max-width: 30rem;
}

.section-intro {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.static-label {
  margin-bottom: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.static-value {
  overflow-wrap: anywhere;
}

.address-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.address {
  padding-block: var(--space-3);
  border-bottom: 1px solid var(--color-line);
}

.address:first-child {
  padding-top: 0;
}

.address:last-child {
  margin-bottom: var(--space-4);
}

.address-text {
  font-style: normal;
  overflow-wrap: anywhere;
}

.address-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin-top: var(--space-2);
}

.text-button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.text-button:hover {
  text-decoration: underline;
}

.text-button.danger {
  color: var(--color-danger);
}

@media (min-width: 768px) {
  .account-section {
    grid-template-columns: 11rem minmax(0, 1fr);
    gap: var(--space-5);
  }
}
</style>
