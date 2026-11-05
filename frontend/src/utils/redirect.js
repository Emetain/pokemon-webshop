/** Only follows paths inside this site, so a crafted login link can't send people elsewhere. */
export function safeRedirect(target, fallback = '/account') {
  const isInternal = typeof target === 'string' && /^\/(?![/\\])/.test(target)
  return isInternal ? target : fallback
}
