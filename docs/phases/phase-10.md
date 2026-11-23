# Phase 10: Go live (prepared, not finished)

**Status on 2026-11-15:** everything that can be prepared without the domain, the KVK number, live Stripe keys, a mail
provider and the legal check is built, tested and committed on branch `feature/phase-10-go-live` (not pushed, not
merged, no `phase-10` tag). The shop is still a closed preview, and nothing in this branch changes that: with the
default settings the preview behaves as before. Opening it is one deliberate step for later, described below.

**Before the shop opens, do the legal check.** You asked to be reminded. The list is under
[Owner checklist, step 0](#step-0-the-legal-check).

## What was prepared

**One switch between the closed preview and the open shop: `SHOP_ACCESS`**

- `deploy/Caddyfile`: `SHOP_ACCESS=preview` (the default, also when the variable is missing) keeps the shared login,
  the `X-Robots-Tag: noindex, nofollow` header and the "keep out" robots.txt. `SHOP_ACCESS=open` drops all three:
  no login, and robots.txt and the sitemap come from the backend. The lines that differ are two snippets per mode
  (`preview_headers`/`preview_gate` and their empty `open_*` twins). Any other value, such as `Open` or a typo, makes
  Caddy refuse to start instead of opening the shop. The web image validates the Caddyfile in both modes when CI
  builds it.
- The backend reads the same variable (`app.shop-access`) and ties the Stripe key to it (`StripeKeyRules`): the
  preview only takes a test key, the open shop only a live key (`sk_live_`/`rk_live_`). The other kind keeps online
  payment off, logs one ERROR line that names the problem (never the key), and the admin dashboard says why in Dutch.
  This stops two real mistakes: a live key behind the preview login (money taken, Stripe's webhook can't get in, the
  order stays unpaid), and a test key in the open shop (customers "pay" with test card 4242 and get their order).
- `deploy/server/shop-access.sh status | open | preview` on the server. `open` refuses while something is missing
  (the domain, `APP_BASE_URL`, DNS, a live key with its webhook secret, real mail, the KVK number), warns about the
  rest, asks you to type `legal check done`, and only then sets `SHOP_ACCESS=open` and restarts the containers.
  `preview` closes the shop again. It never prints a secret.

**Domain**

- `SITE_ALIASES`: more addresses, such as `www.<domain>` and the old `<ip-with-dashes>.sslip.io`, that get a
  certificate and a 308 redirect to `SITE_ADDRESS` (before any login). HTTP to HTTPS and HSTS were already there.

**Fixes from the 9c final review** (ticked off in `docs/phases/phase-09-audit.md`)

- R1: open checkouts are capped per customer (2) and per IP address (5) apart, both defaults the owner can change with
  `CHECKOUT_MAX_OPEN_PER_CUSTOMER` and `CHECKOUT_MAX_OPEN_PER_IP`. The IP case has its own message: "Er wordt vanaf jouw
  netwerk op dit moment veel tegelijk afgerekend. Probeer het over een paar minuten opnieuw."
- R2: a logged-in customer is counted by account, so guest checkouts on their address can't block them.
- R3: product, drop and news pages fall back to Caddy's own `index.html` (503 while the backend is away, 404 for an
  unknown page or an empty answer), and `deploy.sh` checks a product page besides the health endpoint.
- R4: a chunked form body above Tomcat's 2 MB limit answers 413 in the usual format, without a stack trace.
- R5: an answer without a body (Caddy's 413, a 502 during a deploy) shows a readable Dutch message.
- R8: `V14__orders_email_key_index.sql` and the matching `@Index` on `Order`.
- R9: drops, news, the collection, the Pokédex, pre-orders and the release calendar have their own description.
- R6 and R7 were left, as asked. R7: look at `/admin/sync` after the first night on Jackson 3.

**Business details in the footer**

- `COMPANY_LEGAL_NAME`, `COMPANY_ADDRESS`, `COMPANY_KVK_NUMBER`, `COMPANY_VAT_NUMBER` on the server show up in the
  footer of every page (`GET /api/v1/shop/company`). Empty shows nothing, so the preview looks the same until you fill
  them in. iDEAL's rules and Dutch law ask for the KVK number on the site; Stripe's account review looks for it too.

**Server: backups elsewhere, monitoring, updates**

- `backup.sh` also sends each day's dump and image folder to a [restic](https://restic.net) repository elsewhere when
  `RESTIC_REPOSITORY` is set: encrypted on the server before it leaves, deduplicated, kept 30 days
  (`BACKUP_OFFSITE_KEEP`). It pings `BACKUP_PING_URL` when it worked and `<url>/fail` when it didn't.
- `disk-check.sh` with `poketopia-disk-check.timer` (hourly): pings `DISK_PING_URL` while `/`, `/var/lib/docker` and
  `/var/backups/poketopia` are below 80% (`DISK_WARN_PERCENT`), and `<url>/fail` when one isn't.
- These settings and secrets live in `/opt/poketopia/ops.env` (template `deploy/server/ops.env.example`), which only
  root can read: the deploy user, which GitHub's deploy key logs in as, can't reach or delete the copies elsewhere.
  Everything is off while the file is empty. `offsite.sh` runs restic by hand with it (`init`, `snapshots`,
  `restore`, `check`).
- `setup.sh` installs the new scripts and timer, `restic`, and makes sure `unattended-upgrades` runs security updates
  daily, without rebooting by itself (Minecraft).
- `restore-check.sh` now also counts Riftbound sets.

**Smaller things**

- The preview login header no longer reaches the backend on `/sitemap.xml`, `/robots.txt` and the share pages (phase 7
  promised it never would; 9b's new routes had let it through).
- The 401 and 413 answers in the preview now carry `X-Robots-Tag: noindex` too. Stricter than before, nothing looser.

### Online without the login: `SHOP_ACCESS=unlisted`, added on 2026-11-19

The owner's friend had trouble with the shared login, and the owner wanted the site online without it before the
shop opens. So there is a third mode between the preview and the open shop:

- **Caddy:** no shared login, but still the `X-Robots-Tag: noindex, nofollow` header and the robots.txt that disallows
  everything (`unlisted_headers`/`unlisted_gate`). The web image validates all three modes.
- **Backend:** Stripe test keys only, like the preview (a live key keeps online payment off). `SalesGate` lets only
  admins start a checkout or a drop checkout; everyone else gets "De webshop is nog niet open. Bestellen kan nog niet."
  (`403 shop_not_open`), and the checkout page says so at the top with the pay button off (`canOrder` in
  `GET /v1/checkout/options`). Without this a visitor could "pay" with Stripe's test card 4242 and get an order, now
  that Stripe's webhooks get through. Looking around, making an account, the collection and the chatbot work for
  everyone.
- **Server:** `/opt/poketopia/bin/shop-access.sh unlisted` (asks you to type `without login`, refuses a live Stripe
  key); `shop-access.sh preview` puts the login back. The site is away for about a minute while it restarts.

Search engines are asked to stay out, but the site can be found: every certificate is published in the public
Certificate Transparency logs, and bots try new domains within hours. Expect strangers and bots to sign up; the rate
limits and the monthly AI budget stay in place. Everything the legal check says about a public site (privacy policy,
cookies, card images, the name) applies from the moment the login is gone.

### Ads (Google AdSense), added on 2026-11-16

The owner asked for ads from other companies through Google AdSense. Built, and off until there is an AdSense account
(which needs the domain and an open shop first).

- **One switch, `ADS`:** `off` (the default) or `adsense`, read by Caddy and the backend. Any other value stops Caddy.
  With `off` nothing changes: no request to Google, the same Content-Security-Policy and the same cookie notice.
- **Where ads show:** only on the free pages: the set list of a game, a set page, a card page (ad unit `catalog`), and
  the news list and a news article (ad unit `content`). Never on the home page, shop lists, product pages, the cart,
  checkout, drops, the collection, account or admin pages, so an ad never sits next to something the shop sells or
  where a customer pays. `components/AdSlot.vue` is the only place that shows one; it keeps 250 px of room so the page
  doesn't jump, and says "Advertentie" above it.
- **Google's script loads only when a page with an ad opens** (`stores/ads.js`), so someone who only shops never
  talks to Google.
- **Consent:** Google's own consent message does this, not a banner of ours. Google only serves full ads in the EEA
  through a consent tool it has certified, and its own message (AdSense → Privacy & messaging → European regulations)
  is one. It comes with the same script. The shop's cookie notice gets a second text that says ads are from Google,
  and the footer gets "Advertentievoorkeuren", which opens Google's message again.
- **`/ads.txt`** comes from the backend (`GET /api/v1/seo/ads.txt`), built from the publisher id; 404 without ads.
- **Content-Security-Policy with ads:** the shop's own policy plus Google's ad addresses for scripts, frames and
  calls (`(adsense_csp)` in `deploy/Caddyfile`). Google publishes no list, so this one is from what AdSense is known
  to use.
- **Settings:** `ADSENSE_CLIENT` (the publisher id, checked: `ca-pub-` and 16 digits) and `ADSENSE_SLOT_CATALOG`,
  `ADSENSE_SLOT_CONTENT` (ids of ad units; an empty one shows no ad there). A wrong value keeps ads off and logs why.
- **In development** (`npm run dev`) an ad place shows a dashed box instead of loading Google.
- **Applying while unlisted** (added 2026-11-23): the `unlisted` robots.txt lets Google's ad crawler
  (`Mediapartners-Google`) read every page and any crawler read `/ads.txt`; everything else stays `Disallow: /` with
  `X-Robots-Tag: noindex`. Without that, Google can't read `/ads.txt` or review the site. `ADS=adsense` with only
  `ADSENSE_CLIENT` and no ad units serves `/ads.txt` and shows no ads, which is what AdSense needs to approve the site.

Checked: 1042 backend tests pass (7 new: `AdsOrchestratorTest`, one in `SeoIntegrationTest`), `npm run build` passes,
`caddy validate` passes for `off` and `adsense` in both access modes and fails for a typo. With `ADS=adsense` and a
made-up publisher id, through the real Caddyfile on a copy of the database, in headless Chromium: an ad place on
`/kaarten/pokemon` and on a set page, Google's script loaded and picked the place up, no Content-Security-Policy
error in the console; no ad place and no Google script on a shop list, product page, cart, checkout or drops page;
`/ads.txt` and the second cookie text as described. Not checked: a real ad and Google's consent message, which need an
approved account.

## Main files

| Area | Files |
|------|-------|
| Switch and proxy | `deploy/Caddyfile`, `deploy/web.Dockerfile`, `deploy/compose.yml`, `docker-compose.yml` |
| Server scripts | `deploy/server/shop-access.sh`, `deploy.sh`, `backup.sh`, `offsite.sh`, `disk-check.sh`, `poketopia-disk-check.{service,timer}`, `poketopia-backup.service`, `setup.sh`, `restore-check.sh` |
| Templates | `.env.example`, `deploy/server/env.example`, `deploy/server/ops.env.example` |
| Backend | `config/AppProperties.kt` (`ShopAccess`, `Company`, checkout caps), `service/payment/StripeKeyRules.kt`, `StripePaymentGateway.kt`, `service/OpenCheckoutService.kt`, `repository/OrderRepository.kt`, `exception/GlobalExceptionHandler.kt`, `orchestration/CompanyOrchestrator.kt`, `controller/ShopController.kt`, `db/migration/V14__orders_email_key_index.sql` |
| Frontend | `services/api.js`, `components/layout/AppFooter.vue`, `views/admin/AdminDashboardView.vue`, `router.js`, `data/i18n/nl.js` |
| Tests | `CheckoutIntegrationTest` (R1, R2), `ProdProfileIntegrationTest` (R4, defaults, footer endpoint), `StripeKeyRulesTest`, `CompanyOrchestratorTest` |

## New environment variables

| Variable | Where | Default | Use |
|----------|-------|---------|-----|
| `SHOP_ACCESS` | server `.env`, `.env.example` | `preview` | `preview` or `open`; set it with `shop-access.sh` |
| `SITE_ALIASES` | server `.env` | empty | Addresses that redirect to `SITE_ADDRESS` |
| `COMPANY_LEGAL_NAME`, `COMPANY_ADDRESS`, `COMPANY_KVK_NUMBER`, `COMPANY_VAT_NUMBER` | server `.env`, `.env.example` | empty | Business details in the footer |
| `CHECKOUT_MAX_OPEN_PER_CUSTOMER`, `CHECKOUT_MAX_OPEN_PER_IP` | server `.env`, `.env.example` | `2`, `5` | Open checkouts per customer and per IP address |
| `ADS`, `ADSENSE_CLIENT`, `ADSENSE_SLOT_CATALOG`, `ADSENSE_SLOT_CONTENT` | server `.env`, `.env.example` | `off`, empty | Google AdSense on the free card and news pages |
| `RESTIC_REPOSITORY`, `RESTIC_PASSWORD_FILE`, the storage login (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` for S3), `BACKUP_OFFSITE_KEEP` | `/opt/poketopia/ops.env` | empty, `/root/restic-password`, `30d` | The copy of the backups elsewhere |
| `BACKUP_PING_URL`, `DISK_PING_URL`, `DISK_WARN_PERCENT` | `/opt/poketopia/ops.env` | empty, empty, `80` | Monitoring pings |

## Checks

| Check | Result |
|-------|--------|
| `./gradlew test` | 1035 tests, 0 failures, 0 errors (1024 before this phase) |
| `npm run build` | Passes (Vite 8.3.8) |
| `caddy validate` (`caddy:2.11-alpine`, v2.11.9) | Valid with `SHOP_ACCESS` unset, `preview` and `open`, with and without `SITE_ALIASES`. `Open` and `opne` fail (exit 1), so a typo can't open the shop. The image build's own loop over both modes ran in the same image |
| Preview unchanged | The old and the new Caddyfile side by side in `caddy:2.11-alpine` with the built frontend, 19 requests each, diffed. Same status codes, the same login, the same "Disallow: /" robots.txt and `X-Robots-Tag` on every page. Three differences, all intended: the 401 and 413 answers also carry `X-Robots-Tag`; the backend no longer gets the preview login on `/sitemap.xml`; an empty 404 from the backend now gets the app |
| Open mode | Same setup with `SHOP_ACCESS=open`: home, sitemap, product pages and the API answer without a login, no `X-Robots-Tag`, robots.txt from the backend; `www.localhost` gives 308 to the main address |
| Real data through Caddy | A copy of the local database (3,124 active products) on port 8095 behind the real Caddyfile in both modes: a product page carries its own title and `og:image`, an unknown product gives 404 with the app, the sitemap has 4,486 addresses, www redirects without a login |
| R3 fallback | With that backend stopped: product and news pages 503 with the app, `Retry-After: 60` and the security headers; other pages 200; the API 502; the login still first in the preview |
| `prod` profile on Postgres 16 | Empty database: Flyway applied 15 migrations (V1 to V14 and the repeatable one), Hibernate validation passed, 0 ERROR and 0 WARN lines, health UP in 6.7 s. `idx_orders_email_key_status` in `pg_indexes`. `/api/v1/shop/company` gives four nulls, the deploy's page check gets the app, a 3 MB chunked form gets 413 `request_too_large` |
| Stripe key guard on a real start | `SHOP_ACCESS=open` with an `sk_test_` key: one ERROR line "STRIPE_SECRET_KEY is not a live key, but SHOP_ACCESS is open…", checkout options say payment is off, and the key appears nowhere in the log |
| R5 | `services/api.js` in Node with a stubbed `fetch`: empty 413 on JSON, empty 413 on an upload, empty 502/503 each give their Dutch message; an empty 500 the generic one; a backend message still wins |
| Server scripts | `bash -n` on all nine under bash 5 (Ubuntu image). Dry runs with stubbed `docker`, `curl`, `restic`, `getent`: `shop-access.sh` (status on today's preview lists what's missing; `open` refuses; a wrong answer keeps it closed; the right one sets `SHOP_ACCESS=open`, keeps the `.env` at deploy 600 and the hash untouched, and restarts; `preview` and a `.env` without the line; a test key is refused; no secret in the output), `disk-check.sh` (ping, `/fail` and exit 1 above the limit), `backup.sh` (as before without `ops.env`; restic backup and forget with the right tags; `/fail` when the database is down), `deploy.sh` (a broken product page goes back to the previous version, a working one goes live) |
| Not run | Anything on the VPS, GitHub, Stripe, DNS or a mail provider (not allowed tonight, and needs the owner). The GitHub workflow (only runs on a push to `main`) |

Cleaned up afterwards: the test databases `phase10_prod_check` and `phase10_copy`, the throwaway backends on 8095 and
8096, the Caddy test containers and the stub backend. The owner's backend on 8080, Vite on 5173 and the
`pokemonwebshop` database were not touched (the copy was made with `pg_dump`, which only reads).

## Decisions made for the owner (please confirm)

1. **`SHOP_ACCESS` with `preview` as the default**, and an unknown value stops Caddy. Down is better than open by
   accident.
2. **The Stripe key must fit the mode.** So the real test order happens right after opening, not behind the login
   (Stripe's webhook can't get past the login). A full test-mode rehearsal on the preview is still possible: with an
   `sk_test_` key, forward the webhooks with the Stripe CLI and the preview login as a header (step 6).
3. **`shop-access.sh open` refuses without** the domain, a matching `APP_BASE_URL`, DNS, a live key plus webhook
   secret, real mail and the KVK number. Opening without any Stripe key is allowed with a warning.
4. **The bare domain is the main address, `www` redirects to it** (shorter in mails and on packaging). To keep `www`
   instead, swap the two in `SITE_ADDRESS` and `SITE_ALIASES`. The old sslip.io address stays as an alias, so the
   friend's bookmarks keep working.
5. **No AAAA (IPv6) record for now.** Docker on the server isn't set up for IPv6, so IPv6 visitors would reach Caddy
   through Docker's proxy and all look like one internal address: the rate limits and the checkout cap per IP would
   lump them together. IPv4 reaches everyone. Adding IPv6 needs Docker's IPv6 switched on first: a later task.
6. **R1:** 5 open checkouts per IP address. The checkout rate limit you chose in phase 9 (5 tries per 10 minutes per
   IP) is unchanged and is still the tighter of the two for a shared network; raise it if customers get refused after a
   release.
7. **R2 for guests:** accepted the small risk rather than counting email and IP together (the cap per IP and the rate
   limit already handle one visitor holding stock).
8. **R3:** a share page answers 503 with the app while the backend is away, so search engines come back instead of
   indexing an empty page.
9. **Backups elsewhere with restic** (encrypted before it leaves the server, works with S3-compatible storage and SFTP
   storage boxes), 30 days, settings in a root-only file. Choose storage in the EU for the privacy policy.
10. **Monitoring with heartbeat pings** instead of mail from the server: no SMTP login on the host, and a dead server
    also raises the alarm (the pings stop). The uptime check of the home page and the API needs an outside service.
11. **Security updates daily, no automatic reboot**, because Minecraft runs on the same server.
12. **Business details from the server's `.env`** instead of in the code or a page, so filling in the KVK number needs
    no release. The legal pages keep their `[nummer]` placeholders, which you fill in at `/admin/paginas`.
13. **Ads only on the free card and news pages**, never where the shop sells or a customer pays, and Google's own
    consent message instead of a banner of ours (2026-11-16). Say so if you want them in more places.
14. **HSTS stays one year without `includeSubDomains` or `preload`**, so other subdomains (for example for mail) are
    never forced onto HTTPS by the shop.

## Still open, and why

- **Waiting for the owner:** the legal check, the domain, DNS, the mail provider and shop mailbox, the KVK number and
  btw-id, Stripe's live activation, storage for backups elsewhere, a monitoring account, Search Console. In that order:
  see the checklist below.
- **This branch isn't deployed:** merge it into `main` and push (only the owner pushes). The preview then gets the new
  version and looks the same. Then copy `deploy/server/` to the server and run `setup.sh` again (step 1), which
  changes the server, so it needs your OK.
- **Phase 10's "Done when" checks** (valid certificate on the domain, a port scan, a real iDEAL payment and refund, the
  confirmation mail in a normal inbox, a restore from the copy elsewhere, a deploy and rollback on the domain) can only
  run after those steps.
- **From phase 9, still open:** N4 (change password and delete account in the account page), N6 (paging of a few short
  admin lists), N8 (order and ticket keys stored as plain text: easiest before real orders exist, needs a migration
  and a plan for links in sent mails), N9 (a warning in the order list for an amount that differs). R6 (multipart read
  before the login check, bounded) and R7 (look at the first card sync on Jackson 3).
- **Wero:** stripe-java 29.2.0 doesn't know `wero` as a payment method yet (phase 5). iDEAL keeps working; a library
  update is needed when Stripe switches iDEAL to Wero.
- **About a minute down per deploy** (phase 7). Fine for the start; zero-downtime deploys can come later.

---

## Owner checklist

Do the steps in this order: later steps need earlier ones. Every value goes into a file on the server, never into
the repository, the docs, a chat or a commit. "The `.env`" is `/opt/poketopia/.env`, "`ops.env`" is
`/opt/poketopia/ops.env`; edit them as root (`nano`), then run `/opt/poketopia/bin/compose.sh up -d` unless a step says
otherwise. `/opt/poketopia/bin/shop-access.sh status` shows at any moment what opening still needs.

### Step 0: the legal check

Before registering the domain or the trade name, and before opening. From phase 9 and earlier phases:

- The name **Poketopia** itself (Pokétopia is a place in a Pokémon game, "Poké" names are trademarks of The Pokémon
  Company), and the logo in `docs/Poketopia-logo.png` (Poké Balls, a Pokémon Center-like building, Pokémon-style
  lettering). Check this before you register the domain and the KVK trade name.
- The card data and pictures: TCGdex, Scryfall (its image rules and that its prices aren't for a storefront), Lorcast,
  OPTCG API, TCGplayer through tcgcsv.com ("careful if you seek to profit", also used by the picture search for every
  game), Cardmarket's product list; Riot Games' rules for Riftbound; the attribution lines in the footer.
- iDEAL and Dutch law: the KVK number on the site (step 5 puts it in the footer), the business address.
- The draft legal pages at `/admin/paginas`: terms, privacy policy, returns (the 14-day withdrawal right, and opened
  sealed products). Fill in their `[adres]`, `[nummer]` and `[e-mailadres]` placeholders; use the btw-id, not the
  omzetbelastingnummer.
- The privacy policy: Stripe, OpenAI (customer chat messages go to OpenAI: a processor agreement, and saying that it is
  AI), the mail provider and the storage for backups elsewhere (both processors), the newsletter, reviews, tickets
  and how long they are kept, drops (reminders, the waiting room, the email and address keys on orders), that your
  friend has full admin and can see customer data, backups kept 30 days, how to delete an account (through a ticket,
  N4), and that Caddy keeps no access logs.
- Mystery boxes and the Dutch gambling law (Kansspelwet). Generated hero images must not show Pokémon or other
  characters, logos or card art (OpenAI's terms too). The cookie notice is for functional cookies only.
- Ads (only if you switch them on): Google's AdSense program policies, in particular whether pages that mostly show
  other companies' card images and data may carry ads, and whether the card sources allow ads next to their data
  (TCGdex, Scryfall's and Wizards' fan content rules, Lorcast, OPTCG, TCGplayer). The Dutch cookie rules
  (Telecommunicatiewet): ad cookies need consent first, which Google's message asks. And an "Advertenties" section
  in the privacy policy: that Google places cookies and receives the visitor's IP address and the page they are on,
  for which purposes, and how to change the choice.

**Check:** you're done when you'd type `legal check done` in step 9 with a clear conscience.

### Step 1: deploy this branch and update the server scripts

1. Merge `feature/phase-10-go-live` into `main` and push. GitHub tests, builds and deploys it; the preview looks the
   same (the login, the robots.txt, no footer details).
2. Copy the scripts and run `setup.sh` again (changes the server, so only with your OK):

   ```sh
   scp deploy/server/* root@<server>:/root/poketopia-setup/
   ssh root@<server> "/root/poketopia-setup/setup.sh '$(cat ~/.ssh/poketopia_deploy.pub)'"
   ```

   If `~/.ssh/poketopia_deploy.pub` is gone, take the public key from `/home/deploy/.ssh/authorized_keys` on the server
   (the part starting with `ssh-`).

**Check:** `systemctl list-timers 'poketopia-*'` shows the backup and the disk check; `ls -l /opt/poketopia/ops.env`
shows `-rw------- root`; `/opt/poketopia/bin/shop-access.sh status` runs and says `SHOP_ACCESS preview`;
`cat /etc/apt/apt.conf.d/20auto-upgrades` has `Unattended-Upgrade "1"`.

### Step 2: the domain and DNS

1. Register the domain (after the name check of step 0). Any registrar works; the DNS can stay there.
2. A day before switching, set the TTL of the records you'll change to 300 seconds, so a mistake is fixed quickly.
3. Add these records at the registrar's DNS:

   | Type | Name | Value |
   |------|------|-------|
   | A | `@` (the domain itself) | the server's public IPv4 address (the same as in `DEPLOY_HOST`) |
   | A | `www` | the same IPv4 address |

   No AAAA record for now (decision 5). The mail records come in step 4.

**Check:** on your Mac, `dig +short <domain> A` and `dig +short www.<domain> A` both give the server's address (can
take up to the old TTL).

### Step 3: the shop on its domain, still a closed preview

In the `.env`:

```
SITE_ADDRESS=<domain>
SITE_ALIASES=www.<domain> <ip-with-dashes>.sslip.io
APP_BASE_URL=https://<domain>
```

Then `/opt/poketopia/bin/compose.sh up -d`. Caddy gets the certificates within a minute.

**Check:** `https://<domain>` asks for the preview login and has a valid certificate (the lock in the browser);
`http://<domain>`, `https://www.<domain>` and the old sslip.io address all end up at `https://<domain>`;
`compose.sh logs web | grep -i error` shows no certificate errors. Links in mails now use the domain.

### Step 4: mail provider, shop mailbox and DNS for mail

1. Choose a mail provider in the EU that gives you a mailbox on your domain and SMTP sending with a login (port 587,
   STARTTLS). Check its daily sending limit fits order mails and newsletters.
2. Create the shared mailbox (for example `klantenservice@<domain>`) for you and your friend, and decide the sender
   (for example `noreply@<domain>` or `bestellingen@<domain>`; it may be the same mailbox).
3. Add the provider's DNS records: `MX` for receiving, an `SPF` TXT record on `@` (one `v=spf1 … ~all` record with the
   provider's include), the `DKIM` records the provider gives (CNAME or TXT), and a DMARC TXT record on `_dmarc`, to
   start with `v=DMARC1; p=none; rua=mailto:<your mailbox>` (move to `p=quarantine` after a few weeks of clean
   reports).
4. In the `.env`:

   ```
   MAIL_HOST=<provider's SMTP host>
   MAIL_PORT=587
   MAIL_USERNAME=<SMTP login>
   MAIL_PASSWORD='<SMTP password>'
   MAIL_SMTP_AUTH=true
   MAIL_SMTP_STARTTLS=true
   MAIL_FROM=<sender on the domain>
   SHOP_EMAIL=<shared mailbox>
   ```

**Check:** ask for a password reset on `https://<domain>/wachtwoord-vergeten` with a Gmail address and with an Outlook
address: the mail arrives in the inbox, not in spam. In Gmail, "Show original" says `SPF: PASS`, `DKIM: PASS`,
`DMARC: PASS`. A ticket from `/info/contact` arrives in the shared mailbox. Mailpit gets nothing any more.

### Step 5: KVK number and business details

After the KVK registration (the btw-id comes from the Belastingdienst by letter, usually within two weeks):

```
COMPANY_KVK_NUMBER=<8 digits>
COMPANY_VAT_NUMBER=<btw-id, NL…B..>
COMPANY_ADDRESS="<street and number, postcode and city>"
COMPANY_LEGAL_NAME=<only if the registered name isn't just Poketopia>
```

Also fill in the same details in the legal pages at `/admin/paginas` (step 0).

**Check:** the footer of every page shows them (behind the preview login).

### Step 6: Stripe live

Needs the KVK registration and a business bank account.

1. Activate the Stripe account for live payments: business details, KVK number, bank account, identity. Website:
   `https://<domain>`. Statement descriptor: `POKETOPIA`. If Stripe wants to look at the site during the review while
   it is still closed, give them the preview login.
2. Live mode, Settings > Payment methods: iDEAL | Wero, Bancontact and cards on. Settings > Managed payments: off
   (README, "Orders and payments").
3. Developers > Webhooks > Add endpoint: `https://<domain>/api/v1/payments/stripe/webhook`, events
   `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`,
   `checkout.session.expired`. Copy its signing secret.
4. Developers > API keys: the live secret key.
5. In the `.env`: `STRIPE_SECRET_KEY=sk_live_…` and `STRIPE_WEBHOOK_SECRET=whsec_…`. While the shop is a preview the
   backend keeps payment off with a live key (the admin dashboard says so): that's expected.

Optional rehearsal before this step, in test mode: put a test key in the `.env`, and let the Stripe CLI on your Mac
forward the webhooks with the preview login as a header (its `--headers` option; check `stripe listen --help`):
`stripe listen --forward-to https://<domain>/api/v1/payments/stripe/webhook --headers "Authorization: Basic $(printf '%s' 'poketopia:<preview password>' | base64)"`.
Put the `whsec_` it prints in `STRIPE_WEBHOOK_SECRET`, run `compose.sh up -d`, and pay with card 4242 on the preview.
Remove the test key again afterwards.

**Check:** `/opt/poketopia/bin/shop-access.sh status` says `Stripe key live` and `Webhook secret set`.

### Step 7: backups elsewhere

1. Get storage in the EU: an S3-compatible bucket or an SFTP storage box. Make a login that can only reach that bucket
   or box.
2. In `ops.env`: `RESTIC_REPOSITORY` (for example `s3:https://<endpoint>/<bucket>/poketopia` or
   `sftp:<user>@<host>:poketopia`) and the login (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` for S3; for SFTP an SSH
   key for root and the host in `/root/.ssh/known_hosts`). Then, as root:

   ```sh
   openssl rand -base64 32 > /root/restic-password && chmod 600 /root/restic-password
   /opt/poketopia/bin/offsite.sh init
   systemctl start poketopia-backup.service && journalctl -u poketopia-backup.service -n 20
   ```

3. Put a copy of `/root/restic-password` in your password manager. Without it the copy elsewhere can't be read.

**Check:** `offsite.sh snapshots` lists today's backup. Restore test without touching the shop:

```sh
/opt/poketopia/bin/offsite.sh restore latest --target /root/restore-test
/opt/poketopia/bin/restore-check.sh /root/restore-test/var/backups/poketopia/db/<newest>.dump
rm -rf /root/restore-test
```

`restore-check.sh` starts the live backend version on the restored database and lists the sets per game.

### Step 8: monitoring

1. An account at a monitoring service with email alerts that offers both heartbeat checks and HTTP checks (for
   example healthchecks.io for heartbeats, or Better Stack or UptimeRobot; check their current free plans).
2. Two heartbeat checks: "backup" (period 1 day, grace a few hours) and "disk" (period 1 hour, grace 1 hour). Put their
   URLs in `ops.env` as `BACKUP_PING_URL` and `DISK_PING_URL`.
3. After opening (step 9; before that every page asks for the login): HTTP checks on `https://<domain>/` and on
   `https://<domain>/api/actuator/health` (expects `{"status":"UP"}`), every 5 minutes, mail to you.

**Check:** `systemctl start poketopia-disk-check.service` makes the disk check show a ping at the service; the backup
check gets its ping after the next 05:30 backup. Logs already rotate: containers keep 5 × 10 MB each, the journal has
Ubuntu's own limit, Caddy keeps no access log.

### Step 9: open the shop

Only when steps 0 to 6 are done (7 and 8 are strongly advised first):

```sh
/opt/poketopia/bin/shop-access.sh status   # nothing may block
/opt/poketopia/bin/shop-access.sh open     # asks you to type: legal check done
```

**Check right away:**

1. `https://<domain>` loads without a login; `https://<domain>/robots.txt` names the sitemap; response headers have no
   `X-Robots-Tag`.
2. A real order of a small amount (put a cheap product in stock, or a temporary one), paid with iDEAL: within seconds
   it is paid in `/admin/bestellingen`, the confirmation mail is in your normal inbox.
3. Refund it from the order page in admin: the refund mail arrives, the Stripe dashboard shows the refund, the money
   comes back.
4. A port scan of the server from your Mac only finds 22, 80, 443 and Minecraft's 25565, not Postgres or the
   backend: `nmap -Pn -p 1-10000 <server>`.

If anything is wrong: `/opt/poketopia/bin/shop-access.sh preview` closes it again within a minute.

### Step 10: after opening

1. Google Search Console: add the domain as a property (verify with the DNS TXT record it gives), then submit
   `https://<domain>/sitemap.xml`.
2. Raise the TTL of the DNS records back to an hour or a day.
3. Look at `/admin/sync` after the night (R7), and at the backup and disk checks the next day.
4. Ads, if you want them (they need the open shop, so they come last):
   1. Make an AdSense account at https://adsense.google.com with the domain. Google reviews the site; that can take
      days to weeks, and a new site with little own text is often refused the first time.
   2. In AdSense: Privacy & messaging → European regulations → create and publish the consent message for the site,
      in Dutch. Without it visitors here get no consent question. Keep "Auto ads" off: they would also put ads on
      shop and checkout pages.
   3. In AdSense: Ads → By ad unit → Display ads: make two responsive units, "kaartpagina's" and "nieuws". Note the
      number of each (`data-ad-slot`), and the publisher id (`ca-pub-…`) from Account → Account information.
   4. Add the "Advertenties" section to the privacy policy at `/admin/paginas`.
   5. In the `.env`: `ADS=adsense`, `ADSENSE_CLIENT=ca-pub-…`, `ADSENSE_SLOT_CATALOG=…`, `ADSENSE_SLOT_CONTENT=…`, then
      `/opt/poketopia/bin/compose.sh up -d`.

   **Check:** `https://<domain>/ads.txt` shows one line with your publisher id. Open a set page in a private window
   with the browser console open: Google's consent message comes up, an ad (or an empty place for the first hours)
   shows under the cards, and the console has no "Content Security Policy" error. If it has one, the address it names
   belongs in `(adsense_csp)` in `deploy/Caddyfile`. A product page and the checkout show no ad. `ADS=off` and
   `compose.sh up -d` switches everything off again.
5. Delete leftovers on your Mac once they are in GitHub: `~/poketopia-github-secrets.txt` and `~/.ssh/poketopia_deploy`
   (phase 7).

---

## Every production variable and where its value comes from

No values here. "`.env`" is `/opt/poketopia/.env` (deploy user and root), "`ops.env`" is `/opt/poketopia/ops.env` (root
only).

| Variable | File | Where the value comes from |
|----------|------|----------------------------|
| `SHOP_ACCESS` | `.env` | `shop-access.sh`; `preview` until step 9 |
| `SITE_ADDRESS` | `.env` | The domain (step 2); the sslip.io address until then |
| `SITE_ALIASES` | `.env` | `www.<domain>` and the old sslip.io address (step 3) |
| `APP_BASE_URL` | `.env` | `https://` plus `SITE_ADDRESS` |
| `PREVIEW_USER`, `PREVIEW_PASSWORD_HASH` | `.env` | Phase 7; the password itself in `/root/poketopia-preview-login.txt` and on your Mac. Keep them after opening |
| `POSTGRES_DB`, `POSTGRES_USER` | `.env` | `poketopia` (template) |
| `POSTGRES_PASSWORD` | `.env` | Generated by `setup.sh` in phase 7, never shown |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `.env` | The mail provider's SMTP settings and login (step 4) |
| `MAIL_FROM` | `.env` | Your choice of sender on the domain (step 4) |
| `SHOP_EMAIL` | `.env` | The shared mailbox (step 4) |
| `MAILPIT_PORT` | `.env` | `8025` (Mailpit's page on the server itself) |
| `STRIPE_SECRET_KEY` | `.env` | Stripe dashboard, live mode, Developers > API keys (step 6) |
| `STRIPE_WEBHOOK_SECRET` | `.env` | Stripe dashboard, the webhook endpoint's signing secret (step 6) |
| `COMPANY_KVK_NUMBER`, `COMPANY_ADDRESS`, `COMPANY_LEGAL_NAME` | `.env` | The KVK registration (step 5) |
| `ADS`, `ADSENSE_CLIENT`, `ADSENSE_SLOT_CATALOG`, `ADSENSE_SLOT_CONTENT` | `.env` | The AdSense account (step 10), optional |
| `COMPANY_VAT_NUMBER` | `.env` | The btw-id from the Belastingdienst's letter (step 5) |
| `CHECKOUT_MAX_OPEN_PER_CUSTOMER`, `CHECKOUT_MAX_OPEN_PER_IP` | `.env` | Defaults 2 and 5; your choice |
| `OPENAI_API_KEY` | `.env` | OpenAI dashboard, optional; set a monthly limit there too |
| `OPENAI_MONTHLY_LIMIT_USD`, `OPENAI_CHAT_MONTHLY_LIMIT_USD` | `.env` | Your choice (5 and 3) |
| `CATALOG_SYNC_CRON`, `RELEASE_SUGGESTION_CRON`, `COLLECTION_SNAPSHOT_CRON` | `.env` | Defaults (03:00, 04:45, 07:00 Dutch time) |
| `BACKEND_HEAP` | `.env` | `2g` (the Magic sync) |
| `HTTP_PORT`, `HTTPS_PORT` | `.env` | `80`, `443` |
| `TCGDEX_BASE_URL` and the other card source URLs, `OPENAI_TEXT_MODEL`, `OPENAI_IMAGE_MODEL`, `OPENAI_CHAT_MODEL` | `.env` (commented out) | Defaults in `deploy/compose.yml` |
| `RESTIC_REPOSITORY`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (or the provider's own) | `ops.env` | The storage provider (step 7) |
| `RESTIC_PASSWORD_FILE` | `ops.env` | `/root/restic-password`, made with `openssl rand` (step 7); a copy in your password manager |
| `BACKUP_OFFSITE_KEEP` | `ops.env` | `30d` |
| `BACKUP_PING_URL`, `DISK_PING_URL` | `ops.env` | The monitoring service's heartbeat checks (step 8) |
| `DISK_WARN_PERCENT` | `ops.env` | `80` |
| `SPRING_PROFILES_ACTIVE=prod`, `FORWARD_HEADERS_STRATEGY=native`, `MEDIA_DIRECTORY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TZ`, `JAVA_TOOL_OPTIONS` | `deploy/compose.yml` | Fixed in the compose file, from the values above |
| `AUTH_COOKIE_SECURE`, `SEO_INDEX_FILE` | none | Defaults of the `prod` profile (`true`, `/app/index.html`) |
| `IMAGE_TAG` | none | Set by `deploy.sh` to the commit |
| `DEPLOY_HOST`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` | GitHub, environment `preview` | Phase 7; unchanged by the domain (the deploy talks to the server's address) |

## The server, deploys, rollback and restore

Unchanged from phase 7 and described there: the server, where every secret lives, how a deploy works, going back to an
earlier version, and restoring a backup ([phase-07.md](phase-07.md)). What phase 10 adds:

- **A deploy** now also needs a product page to come back as the app before the new version counts as live.
- **Opening and closing** the shop: `shop-access.sh open` / `preview` (step 9). It restarts the containers, about a
  minute down.
- **Restoring from the copy elsewhere** when the server is lost: on a new server with Docker and this repository's
  `deploy/server/` set up (`setup.sh`, the `.env` and `ops.env` filled in again, `restic` installed),
  `offsite.sh restore latest --target /root/restore`, then the database and image restore of phase 7 with the dump and
  image folder from `/root/restore/var/backups/poketopia/`. The restic password from your password manager is needed.
- **A disk alarm:** `df -h`, then usually old images (`docker image prune`), old pre-deploy dumps in
  `/var/backups/poketopia/pre-deploy` (the last 10 are kept), or the backups folder.
