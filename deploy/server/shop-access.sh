#!/usr/bin/env bash
# Opens the shop to everyone, or closes it again behind the preview login. Run as root on the server:
#
#   shop-access.sh status    how the shop is set up now, and what opening it still needs
#   shop-access.sh open      checks the settings, asks you to confirm the legal check, then opens the shop
#   shop-access.sh preview   back to the closed preview: the shared login and "keep out" for search engines
#
# It changes one line in /opt/poketopia/.env (SHOP_ACCESS) and restarts the containers that read it, which takes the
# site away for about a minute. Never prints a secret. The whole go-live order is in docs/phases/phase-10.md.
set -euo pipefail

ROOT=${POKETOPIA_ROOT:-/opt/poketopia}
ENV_FILE=$ROOT/.env
COMPOSE=$ROOT/bin/compose.sh

problems=()
warnings=()

# One value from the .env, without surrounding quotes. The file isn't safe to source (cron times have spaces).
value() {
  local line
  line=$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 || true)
  line=${line#*=}
  if [[ $line == \"*\" || $line == \'*\' ]]; then line=${line:1:${#line}-2}; fi
  printf '%s' "$line"
}

stripe_mode() {
  case $1 in
    '') echo "none" ;;
    sk_live_* | rk_live_*) echo "live" ;;
    sk_test_* | rk_test_*) echo "test" ;;
    *) echo "unknown" ;;
  esac
}

# Everything the open shop needs. Problems stop `open`; warnings only ask.
check_for_opening() {
  local site base key webhook mail_host mail_from shop_email kvk domain
  site=$(value SITE_ADDRESS)
  base=$(value APP_BASE_URL)
  key=$(value STRIPE_SECRET_KEY)
  webhook=$(value STRIPE_WEBHOOK_SECRET)
  mail_host=$(value MAIL_HOST)
  mail_from=$(value MAIL_FROM)
  shop_email=$(value SHOP_EMAIL)
  kvk=$(value COMPANY_KVK_NUMBER)
  domain=${site#www.}

  [[ -n $site ]] || problems+=("SITE_ADDRESS is empty.")
  [[ $site != *.sslip.io ]] || problems+=("SITE_ADDRESS is still the temporary sslip.io address: set the shop's own domain first.")
  [[ $base == "https://$site" ]] || problems+=("APP_BASE_URL must be https://$site (mails and Stripe's return page use it).")
  if [[ -n $site ]] && ! getent ahosts "$site" >/dev/null; then
    problems+=("$site doesn't resolve yet: add the A record (and AAAA with IPv6) at the domain's DNS first.")
  fi

  case $(stripe_mode "$key") in
    live) [[ -n $webhook ]] || problems+=("STRIPE_WEBHOOK_SECRET is empty: paid orders would never be marked paid.") ;;
    none) warnings+=("No STRIPE_SECRET_KEY: the shop opens without online payment (checkout says it isn't set up).") ;;
    *) problems+=("STRIPE_SECRET_KEY isn't a live key (sk_live_...): the open shop keeps online payment off with it.") ;;
  esac

  [[ $mail_host != mailpit && -n $mail_host ]] || problems+=("MAIL_HOST is still Mailpit: customers would get no order confirmations.")
  [[ $mail_from == *"@$domain" ]] || warnings+=("MAIL_FROM ($mail_from) isn't an address on $domain: SPF, DKIM and DMARC won't match, so mails may end up in spam.")
  [[ -n $shop_email ]] || warnings+=("SHOP_EMAIL is empty: tickets go to MAIL_FROM.")
  [[ -n $kvk ]] || problems+=("COMPANY_KVK_NUMBER is empty: iDEAL's rules and Dutch law ask for the KVK number on the site.")
  [[ -n $(value COMPANY_ADDRESS) ]] || warnings+=("COMPANY_ADDRESS is empty: a webshop must show where it is based (check this in the legal check).")
}

show_status() {
  local access key
  access=$(value SHOP_ACCESS)
  key=$(value STRIPE_SECRET_KEY)
  printf 'SHOP_ACCESS     %s\n' "${access:-preview (not set)}"
  printf 'SITE_ADDRESS    %s\n' "$(value SITE_ADDRESS)"
  printf 'SITE_ALIASES    %s\n' "$(value SITE_ALIASES)"
  printf 'APP_BASE_URL    %s\n' "$(value APP_BASE_URL)"
  printf 'Stripe key      %s\n' "$(stripe_mode "$key")"
  printf 'Webhook secret  %s\n' "$([[ -n $(value STRIPE_WEBHOOK_SECRET) ]] && echo set || echo empty)"
  printf 'MAIL_HOST       %s\n' "$(value MAIL_HOST)"
  printf 'MAIL_FROM       %s\n' "$(value MAIL_FROM)"
  printf 'SHOP_EMAIL      %s\n' "$(value SHOP_EMAIL)"
  printf 'KVK number      %s\n' "$([[ -n $(value COMPANY_KVK_NUMBER) ]] && echo set || echo empty)"
}

set_access() {
  local new=$1
  if grep -qE '^SHOP_ACCESS=' "$ENV_FILE"; then
    sed -i "s/^SHOP_ACCESS=.*/SHOP_ACCESS=$new/" "$ENV_FILE"
  else
    printf '\n# Who gets in: preview or open. Change it with shop-access.sh.\nSHOP_ACCESS=%s\n' "$new" >>"$ENV_FILE"
  fi
  # sed -i writes a new file: keep it readable for the deploy user only.
  chown deploy:deploy "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Restarting the shop with SHOP_ACCESS=$new (about a minute)..."
  "$COMPOSE" up --detach --wait
}

answer_of() {
  curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$1" || true
}

[[ $(id -u) == 0 ]] || { echo "Run this as root." >&2; exit 1; }
[[ -f $ENV_FILE ]] || { echo "$ENV_FILE doesn't exist." >&2; exit 1; }

case ${1:-} in
  status)
    show_status
    check_for_opening
    echo
    if ((${#problems[@]})); then echo "Opening the shop still needs:"; printf '  - %s\n' "${problems[@]}"; fi
    if ((${#warnings[@]})); then echo "Worth a look:"; printf '  - %s\n' "${warnings[@]}"; fi
    if ((${#problems[@]} == 0)); then echo "Nothing blocks opening the shop. The legal check is yours to confirm."; fi
    ;;

  open)
    check_for_opening
    if ((${#problems[@]})); then
      echo "The shop stays closed. First:" >&2
      printf '  - %s\n' "${problems[@]}" >&2
      exit 1
    fi
    if ((${#warnings[@]})); then echo "Worth a look first:"; printf '  - %s\n' "${warnings[@]}"; fi
    echo
    echo "This opens the shop to everyone: no login, search engines welcome, live payments."
    echo "Only go on when the legal check from phase 9 is done (see docs/phases/phase-10.md)."
    read -r -p "Type 'legal check done' to open the shop: " answer
    [[ $answer == "legal check done" ]] || { echo "The shop stays closed."; exit 1; }
    set_access open
    site=$(value SITE_ADDRESS)
    echo "Home page without a login: $(answer_of "https://$site/") (should be 200)"
    echo "robots.txt names the sitemap: $(curl --silent --max-time 15 "https://$site/robots.txt" | grep -c '^Sitemap:' || true) (should be 1)"
    echo "The shop is open. Next: place a small real order with iDEAL and refund it from admin (phase-10.md)."
    ;;

  preview)
    set_access preview
    echo "Home page without a login: $(answer_of "https://$(value SITE_ADDRESS)/") (should be 401)"
    echo "The shop is a closed preview again. A live Stripe key keeps online payment off until it is open."
    ;;

  *)
    echo "usage: shop-access.sh status | open | preview" >&2
    exit 2
    ;;
esac
