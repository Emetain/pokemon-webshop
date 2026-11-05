const dateFormat = new Intl.DateTimeFormat('nl-NL', {
  dateStyle: 'long',
  timeZone: 'Europe/Amsterdam',
})

export function formatDate(instant) {
  return dateFormat.format(new Date(instant))
}
