# Handover: phase 10 (Go live) is prepared, not finished

Written on 2026-11-15 at the end of the night session. This file only exists while phase 10 is unfinished; the details
are in [phases/phase-10.md](phases/phase-10.md).

## Where things stand

- **Branch:** `feature/phase-10-go-live`, made from `main` at `71b8d5d` (tag `phase-9`). Not pushed, not merged into
  `main`, no `phase-10` tag: the phase isn't done.
- **The shop is still a closed preview** on the VPS, at `<ip-with-dashes>.sslip.io` behind the shared login. Nothing
  on the server, in GitHub, at Stripe, at a registrar or at a mail provider was touched tonight.
- **The legal check isn't confirmed.** The shop must not open before it is. The list is in step 0 of the checklist in
  phase-10.md.
- **Built and tested:** the `SHOP_ACCESS` switch (Caddy and the Stripe key rules in the backend, off by default),
  `shop-access.sh` to open the shop as one checked step, `SITE_ALIASES` for `www` and the old address, the business
  details in the footer, backups elsewhere with restic, monitoring pings and the disk check, automatic security
  updates, and the 9c findings R1 to R5, R8 and R9. 1035 backend tests pass, the frontend builds, the Caddyfile is
  valid in both modes, and the `prod` profile starts cleanly on an empty Postgres with migration V14.

## On your own computer

Your backend on 8080 still runs a copy of the phase 9 jar (started by Claude from its scratchpad). Your Vite on 5173
shows whatever branch is checked out; on this branch the footer asks the backend for `/api/v1/shop/company`, gets a 404
from the old jar and simply shows nothing, so nothing breaks. To run the phase 10 backend, stop that process, then in
`backend/`: `SPRING_PROFILES_ACTIVE=postgres SPRING_JPA_SHOW_SQL=false ./gradlew bootRun`. Hibernate adds the new
index to your local database by itself.

## Next steps, in order

1. **Read and confirm the decisions** in phase-10.md ("Decisions made for the owner"), especially: the Stripe key must
   fit `SHOP_ACCESS` (so the real test order comes right after opening), the bare domain as the main address, no IPv6
   record for now, 5 open checkouts per IP address, and restic for backups elsewhere.
2. **Merge and push** `feature/phase-10-go-live` into `main` (the preview looks the same afterwards), then run
   `setup.sh` again on the server with the new scripts (step 1 of the checklist; it changes the server, so it needs
   your OK if Claude runs it).
3. **Once the domain exists** (step 2 and 3): A records for `@` and `www`, then `SITE_ADDRESS`, `SITE_ALIASES` and
   `APP_BASE_URL` in `/opt/poketopia/.env`, and `compose.sh up -d`. The shop is then on its domain, still behind the
   login.
4. **Once the mailbox exists** (step 4): the provider's MX, SPF, DKIM and DMARC records, the `MAIL_*` and `SHOP_EMAIL`
   lines, and a test mail to Gmail and Outlook.
5. **Once the KVK number exists** (step 5 and 6): the `COMPANY_*` lines and the legal pages in admin, then Stripe's live
   activation, the webhook endpoint, and the live keys in the `.env`.
6. **Backups elsewhere and monitoring** (step 7 and 8), any time before opening.
7. **After the legal check:** `/opt/poketopia/bin/shop-access.sh open`, then a real iDEAL order and a refund, the port
   scan, and Search Console (step 9 and 10).
8. **Finish the phase:** run phase 10's "Done when" checks on the live shop, update phase-10.md with the results, the
   domain and the DNS records (never a secret), delete this file, commit, and tag `phase-10`.

## For the next session

- Never write the server's IP address or the sslip.io address into the repository, the docs or a commit.
- Every command that changes something on the server needs the owner's OK first; read-only checks are fine.
- Customers never see stock counts: not on a page, in an API answer or in a message.
- Still open from phase 9: N4, N6, N8 (order and ticket keys in plain text, easiest before real orders exist), N9, R6,
  and R7 (look at `/admin/sync` after the first night with the new version).
