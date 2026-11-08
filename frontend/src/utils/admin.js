import { t } from './i18n'

/** The status of a product as a short Dutch word. */
export const statusLabel = (status) => t(`admin.products.statuses.${status}`)

export const reasonLabel = (reason) => t(`admin.stock.reasons.${reason}`)

/** "+5" or "-2", so a change in a log reads at a glance. */
export const signed = (number) => (number > 0 ? `+${number}` : String(number))

/** A date-time input works in local time without a zone. These turn it into an instant and back. */
export function toLocalInput(instant) {
  if (!instant) return ''
  const date = new Date(instant)
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export function fromLocalInput(value) {
  return value ? new Date(value).toISOString() : null
}
