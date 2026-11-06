<script setup>
import { reactive, ref } from 'vue'
import FormField from '../FormField.vue'
import FormMessage from '../FormMessage.vue'
import CountrySelect from './CountrySelect.vue'
import { useRegion } from '../../stores/region'
import { t } from '../../utils/i18n'

const props = defineProps({
  address: { type: Object, default: null },
  save: { type: Function, required: true },
})

const emit = defineEmits(['cancel'])

const { country } = useRegion()

const form = reactive({
  street: props.address?.street ?? '',
  houseNumber: props.address?.houseNumber ?? '',
  postcode: props.address?.postcode ?? '',
  city: props.address?.city ?? '',
  country: props.address?.country ?? country.value,
})

const error = ref('')
const saving = ref(false)

async function submit() {
  error.value = ''
  saving.value = true
  try {
    await props.save({ ...form })
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="address-form" novalidate @submit.prevent="submit">
    <FormMessage v-if="error">{{ error }}</FormMessage>
    <div class="row street-row">
      <FormField v-model="form.street" :label="t('account.addresses.street')" autocomplete="address-line1" />
      <FormField v-model="form.houseNumber" :label="t('account.addresses.houseNumber')" autocomplete="address-line2" />
    </div>
    <div class="row city-row">
      <FormField v-model="form.postcode" :label="t('account.addresses.postcode')" autocomplete="postal-code" />
      <FormField v-model="form.city" :label="t('account.addresses.city')" autocomplete="address-level2" />
    </div>
    <CountrySelect v-model="form.country" :label="t('account.addresses.country')" />
    <div class="buttons">
      <button class="btn btn-primary" type="submit" :disabled="saving">{{ t('account.addresses.save') }}</button>
      <button class="btn btn-secondary" type="button" @click="emit('cancel')">
        {{ t('account.addresses.cancel') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.address-form {
  display: grid;
  gap: var(--space-3);
}

.row {
  display: grid;
  gap: var(--space-3);
}

.street-row {
  grid-template-columns: minmax(0, 1fr) 6.5rem;
}

.city-row {
  grid-template-columns: 7.5rem minmax(0, 1fr);
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-1);
}
</style>
