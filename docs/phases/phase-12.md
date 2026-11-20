# Phase 12: More Premium

Built on 2026-11-21 on branch `feature/phase-12-more-premium` (from `main` at `549759f`, which already holds phase 11).
Every feature below is Premium and the server checks it, except dark mode, which is free for everyone.

## Before you try it

- **Restart your backend.** Your backend on 8080 still runs the phase 11 jar. Stop it and start it again in `backend/`
  with `SPRING_PROFILES_ACTIVE=postgres SPRING_JPA_SHOW_SQL=false ./gradlew bootRun`. Hibernate adds the two new user
  columns and the three new tables by itself.
- **The price graphs need saved prices.** Your local database has them from 2026-11-19. The server has none yet: the
  phase 11 code arrived there after the night's sync, so it saves its first prices at the sync of 2026-11-21 03:00.
  Until then (and for the first days after) the graphs and the risers and fallers have little to show.
- **Price alerts are mailed at 07:15** (`PRICE_ALERT_CRON`), after the sync (03:00, about an hour and a half) and the
  value snapshot (07:00). Locally the mails land in Mailpit.

## What was built

### No ads for Premium

- `/api/v1/shop/ads` answers `enabled: false` to a Premium member (`AdsOrchestrator.ads(viewer)`), and `AdSlot.vue`
  waits until it knows who is looking, so Google's script is never loaded for them. The "Advertentiekeuzes" link in the
  footer is hidden for them too. `ads.txt` stays: it's about the site, not about the visitor.

### Price history per card, and risers and fallers

- **Card page, "Prijsverloop"** (`CardPriceHistory.vue`, `GET /api/v1/catalog/cards/{id}/history`): one line per
  variant with buttons to switch, drawn by the same `ValueChart.vue` as the value graph, so the periods (1D to Max) and
  the 300 ms morph are the same. The texts come from `priceHistory.chart` (the chart got a `textKey` prop).
- One point per day: a price is only saved on the days it changes (phase 11), so each day gets the last price saved on
  or before it, and today is the price the card page shows (`PriceTrendMath.daily`). Dollar prices are converted at
  today's rate, like the value graph, so a moving exchange rate doesn't look like a moving price.
- Only the source the card page shows now: a card whose price moved from Cardmarket to TCGplayer in phase 11 would
  otherwise jump between two markets.
- **Without Premium** a faded line with a lock, one line about Premium and a button that opens the Premium popup. The
  data isn't fetched at all.
- **Risers and fallers** (`CollectionMovers.vue`, `GET /api/v1/collection/movers?period=1D|1W|1M`), on the collection
  page right under the value graph: the five owned cards that rose most and the five that fell most, per copy, with the
  price then and now and the percentage. Sorted by **money**, in euros, not by percentage: a common going from €0.02
  to €0.10 (+400%) would otherwise push out a card that gained €40. The page says which day it compares with; when the
  history began later than the start of the period it says "since" that day.
- Plain JDBC on `card_price_history`, whose key starts with the card, so every lookup is an index lookup
  (`CardPriceTrendService`). Checked on a copy of the local Postgres database.

### Price alerts

- **On the card page** (`PriceAlertPanel.vue`): "Als de prijs onder een bedrag zakt" (in the customer's currency) or
  "Als de prijs meer dan een percentage stijgt of daalt", per variant. The owner's examples (below a price for wishlist
  cards, a percentage for owned cards) are shown as hints; both work on any card.
- **All alerts** at `/prijsalerts` (`PriceAlertsView.vue`), also in the Collectie menu and on the collection page.
- **The rules** (`PriceAlertRules`, unit tested):
  - Below a price goes off the first day the trend price is **under** the line (equal isn't under), then waits until
    the price was back at or above it. A price that stays low sends one mail, not one every day.
  - A percentage is measured from the price when the alert was set. When it goes off, that day's price becomes the new
    starting point, so the next mail needs the next move of that size. An alert set while the card had no price starts
    at its first price. A card that moved from euros to dollars is compared in one currency.
- **The daily check** (`PriceAlertJob`, 07:15): per customer, with Premium only. A customer gets **at most one mail a
  day** with every alert that went off: the job skips anyone whose alert already went off today, also when it runs
  twice. One customer failing doesn't stop the others.
- **The mail** links to each card and has a link per alert that switches it off without logging in
  (`/prijsalerts/uit?token=…`, `PriceAlertOffView.vue`). The token is made per mail and only its hash is stored, so the
  link of an older mail for the same alert stops working once a newer one went out.
- **Limit:** 50 alerts per customer (`app.premium.max-price-alerts`).
- **When Premium ends** the alerts stay but aren't checked. The customer can still see them and switch them off:
  nothing should keep mailing someone who wants out.

### Export

- **CSV and PDF** from the collection page (`CollectionTools.vue`; `GET /api/v1/collection/export.csv` and `.pdf`):
  card, game, set, set code, number, variant, condition, quantity, price per copy, source with the price's date, and
  value, then the total value, the number of cards, the cards without a price and the date (`CollectionExport`).
- CSV the way the product CSV already works: semicolons, decimal commas and a byte order mark for Dutch Excel, and text
  that starts like a formula gets a quote so a spreadsheet shows it instead of running it.
- **PDF** (`CollectionPdf`) with [Apache PDFBox](https://pdfbox.apache.org) 3.0.5 (Apache License 2.0, the only new
  dependency): A4 landscape, the total on top and below, page numbers, and "een indicatie van de marktwaarde, geen
  taxatie". It uses the standard Helvetica of every PDF reader, so no font file is shipped. Helvetica lacks a few
  characters of card names (the local catalog has δ 192 times, then ◇ ☆ ♀ ♂): they're written out (`Mew delta`,
  `Nidoran(F)`, `*`), letters with a mark it lacks lose the mark (ā), and anything else becomes "?". The CSV keeps
  every character.
- Prices are converted to the customer's currency; a converted price says where it came from ("TCGplayer (USD)").

### Extra binders

- `/collectie/binders` (list and "Binder maken") and `/collectie/binders/{id}` (cards, value, rename, remove), and on
  the card page "In een binder" (`CardBinderPanel.vue`): pick a binder and a variant and add it; it also says which
  binders hold the card. Each binder has its own value (`CollectionValueCalculator`, like the collection).
- The collection stays what counts: binders don't change the progress, the Pokédex, the value graph or the home
  binder (checked in the integration test). The code calls them `ExtraBinder`, because "binder" already means the home
  page binder (`/api/v1/binder`); the new API is `/api/v1/binders`.
- **When Premium ends** the binders stay visible with a line about it, but can't be changed: no adding, no new
  quantities, no renaming and no removing (the plan says "can't be changed, and nothing is deleted").
- Limits: 20 binders per customer, 2000 different cards per binder, 9999 copies per card (`app.premium`).

### Themes

- **My account, "Weergave"** (`AppearanceSettings.vue`): "Licht", "Donker" or "Zoals je apparaat", free for everyone,
  and the colour themes **Poketopia** (free), **Vuur**, **Gras**, **Bliksem** and **Psychisch** (Premium; a locked
  theme opens the popup). Named after Pokémon types as the Dutch cards call them.
- Saved on the account (`users.color_mode`, `users.color_theme`), so they follow the customer to other devices. While
  Premium has ended the server sends Poketopia instead of a Premium theme; the choice itself is kept and comes back with
  Premium.
- Built on the tokens in `main.css`: `data-theme` on `<html>` swaps the brand colours, `data-mode="dark"` the surfaces
  and text, and the dark version of each theme is a more specific block. Every theme keeps white text on its brand
  colour at 4.5:1 or more (lightning yellow can't, so Bliksem's buttons are deep gold and the yellow is in the tints).
- A new token, `--color-brand-ink`, for text and icons in the brand colour: the same as `--color-brand` in light mode,
  lighter in dark mode, where `--color-brand` stays a background for white text. The 63 places that used
  `color: var(--color-brand)` now use it.
- **No white flash:** `public/appearance.js`, a small script in the `<head>`, puts the last choice on the page before
  it's drawn (from this browser's storage); `stores/appearance.js` takes over once the session is known. A separate
  file because the Content-Security-Policy allows no inline scripts. "Zoals je apparaat" follows the device when it
  switches.
- **The default stays light** for everyone, also for visitors who aren't logged in: the white and blue look is the
  shop's own, and a visitor can't choose (the plan puts the choice in My account).

### Premium texts

- The popup and the Premium page list the eight Premium features (prijsgeschiedenis, prijsalerts, geen advertenties,
  exporteren, extra binders, kleurthema's, onbeperkt scannen, pre-grade check), and every new feature opens the popup
  with its own heading (`cardHistory`, `movers`, `alerts`, `export`, `binders`, `themes`).
- A fourth home page tile, "Prijsalerts en prijsverloop", right under "Nieuw binnen" with the other tools (your wish to
  put new things high on the home page). It goes to the alerts for a Premium member and opens the popup for anyone
  else.
- **The default terms and privacy texts** (`InfoPageDefaults.kt`) got the new features, what happens to alerts and
  binders when Premium ends, and the data the alerts, binders and appearance keep. **The pages on the server are your
  own edited copies**, so copy those lines there yourself (part of the legal check).

## Main files

| Part | Backend | Frontend |
|---|---|---|
| Ads | `AdsOrchestrator`, `ShopController` | `AdSlot.vue`, `AppFooter.vue` |
| Price history | `CardPriceTrendService` (`PriceTrendMath`), `PriceTrendOrchestrator` | `CardPriceHistory.vue`, `CollectionMovers.vue`, `ValueChart.vue` |
| Alerts | `PriceAlert`, `PriceAlertRules`, `PriceAlertService`, `PriceAlertJob` (`PriceAlertMailText`), `PriceAlertController` | `PriceAlertPanel.vue`, `PriceAlertsView.vue`, `PriceAlertOffView.vue` |
| Export | `CollectionExport`, `CollectionPdf` (`PdfText`), `CollectionController` | `CollectionTools.vue`, `download()` in `api.js` |
| Binders | `ExtraBinder`, `ExtraBinderService`, `ExtraBinderController` | `BindersView.vue`, `BinderView.vue`, `CardBinderPanel.vue` |
| Themes | `ColorMode`, `ColorTheme`, `AccountOrchestrator` | `main.css`, `stores/appearance.js`, `public/appearance.js`, `AppearanceSettings.vue` |

Migration `V17__more_premium.sql`: two nullable user columns and three new tables, so the previous version keeps
working on the new schema.

## New environment variables

| Variable | Default | What it does |
|---|---|---|
| `PRICE_ALERT_CRON` | `0 15 7 * * *` | When the price alerts are checked and mailed, in Dutch time; `-` switches it off |

Added to `.env.example`, `deploy/server/env.example` and both compose files. New defaults in `app.premium`:
`max-price-alerts` (50), `max-binders` (20), `max-binder-cards` (2000), `movers-shown` (5).

## Checks

- **"Done when", in `MorePremiumIntegrationTest`** (10 tests): without Premium the API refuses the card graph, the
  movers, setting an alert, both exports, making a binder and a colour theme (403 `premium_required`), while dark mode
  saves; logged out they ask for a login; a Premium member gets the graph (one point per day), the risers and fallers,
  two alerts that go off after a "sync" in **one** mail with both, no second mail that day, and a working switch-off
  link (a second use says it no longer works); alerts wait while Premium has ended and can still be removed; the CSV
  and PDF hold the cards, the total and the date; binders have their own value, don't change the collection, and are
  read only after Premium; another customer can't see or change someone's binder; a theme falls back when Premium ends.
- **Unit tests** for the alert rules (`PriceAlertRulesTest`, 10), the export (`CollectionExportTest`, 7: columns,
  the reverse holo's own price, conversion, totals, Dutch CSV, the PDF's text and pages, the character fallback), the
  price graph and movers (`PriceTrendMathTest`, 8) and ads for Premium (`AdsOrchestratorTest`).
- `./gradlew test`: **all 1175 tests pass**. `npm run build` passes.
- **In the browser** on a copy of the local database (backend on 8095, Vite on 5174, Playwright; some made-up older
  prices in the copy only): the card graph with all seven periods and the morph; an alert set on the card page and
  listed at `/prijsalerts`; a binder made, a reverse holo added from the card page, its value; risers and fallers on
  the collection page; CSV and PDF downloaded and opened; dark mode with each theme on the home page, a card page and
  the collection; after a reload the colours are on `<html>` before the app runs; the Premium member's `/shop/ads`
  says `enabled: false`; without Premium the locked graph, the alert button, a theme and "Binder maken" each open the
  popup with their own heading; at 375 and 390 px wide nothing scrolls sideways. No console errors. The copy was
  dropped afterwards.
- Found and fixed on the way: the faded line of the locked graph caught the click meant for its button; the PDF's
  "Bron" column cut off the date.

## Also fixed

- **`CardmarketLinkIntegrationTest` failed about one run in four**, which is likely what stopped a GitHub run on
  2026-11-20 even after yesterday's fix. Its random product ids could fit in an `Int`; JSON then reads them back as an
  `Int`, which never equals the `Long` the test expects ("expected 999877481 but was 999877481"). The ids now stay above
  the `Int` range. Ran it three times in a row to be sure.

## Decisions and open issues

- **History size.** Phase 11 estimated at most 2.7 GB a year and suggested thinning old rows in phase 12. The server
  has no rows yet (see above), and the local database has 184,671 rows for its first two syncs (mostly the first
  sync, which saves every price once). Not thinned: look at the real numbers after a week,
  `SELECT price_date, count(*) FROM card_price_history GROUP BY price_date ORDER BY price_date;`. The server has 29 GB
  free (70% used).
- **Mail volume.** At most one alert mail per customer per day. Zoho allows 50 to 500 mails an hour depending on the
  plan; plenty for now, but worth a look if Premium grows to hundreds of members with alerts.
- **Push messages** for alerts come with the app in phase 13, as planned.
- **Reverse holos as their own tile** in the set lists were mentioned on 2026-11-21. Not built: the reverse holo
  already shows on the card page (variants, price table, collection, and now the price graph, alerts and binders), and
  for Delta Reign after the next Pokémon sync. Ask the owner whether they want separate tiles.
