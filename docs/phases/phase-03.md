# Phase 3: Collection and Pokédex

Customers track what they own in all four games, see their progress and what it's worth, and fill a Pokédex.

## What was built

**Backend**

- `CollectionEntry` (user, card, variant, quantity, optional condition) with endpoints to add, change and remove, and a bulk add. Every change answers with the card's entries and the new progress and value of its set, so the page updates without a second request.
- Progress per set, per game and overall, for normal and master set. Totals come from the cards we really have, not from `CardSet.cardCount`, so a set can always reach 100%.
- Collection value: quantity × trend price of the owned variant, added up per currency in `BigDecimal` and converted and rounded once. Cards without a usable price are counted separately.
- `ExchangeRates` does all currency conversion, including the sort price from phase 2.
- `PokemonSpecies`, imported from PokéAPI at the start of every Pokémon sync. `GET /api/v1/pokedex` gives all slots with progress overall and per generation, and `/pokedex/{number}/cards` the cards of one Pokémon.
- Card wishlist, separate from the product wishlist in phase 4.
- Public share link with a random slug, off by default. Stopping closes the link for good.
- `CollectionValueSnapshot`: one value per customer per day, saved by `CollectionSnapshotJob`. `GET /api/v1/collection/history` returns these with today's live value as the last point.
- Home binder: `GET /api/v1/binder` with five sources, the choice saved on the user, and a list of chosen cards for "own selection".
- Card lists now show what the viewer owns, and filter on owned and missing. `CardListOrchestrator` holds what all lists share. `@CurrentUser` can be nullable on public endpoints.

**Frontend**

- Set pages: a blue frame around owned cards, "In bezit" or "Gemist" below the image, one-click add, quantity +/-, a progress panel with a toggle between normal and master set, a collection filter, and a button to add a whole page.
- `/collectie`: total value, a value graph, progress per game, Pokédex progress, most valuable cards and all started sets.
- `/kaarten/:game/:setCode/gemist`: the list of missing cards, with the usual sorting and filters.
- `/collectie/pokedex`: all 1,025 Pokémon with a filter and progress bar per generation. `/collectie/pokedex/:number` shows all cards of one Pokémon.
- `/verlanglijst`: the card wishlist.
- `/collectie/gedeeld/:slug`: the public page.
- Home: the binder in the hero shows real cards with page buttons.
- Card page: what you own per variant with condition, the wishlist and the binder.
- My account: what the binder shows, the own selection, and the share link.
- Logged-out visitors get a short explanation and a register button on `/collectie`, `/collectie/pokedex` and `/verlanglijst`.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/CollectionEntry.kt`, `CollectionValueSnapshot.kt`, `CardWishlistEntry.kt`, `BinderSelection.kt`, `BinderSource.kt`, `CardCondition.kt`, `PokemonSpecies.kt` |
| Calculations | `service/CollectionMath.kt`, `PokedexMath.kt`, `ExchangeRates.kt`, `CollectionHistoryMath` in `CollectionHistoryService.kt` |
| Services | `service/CollectionService.kt`, `CollectionStatsService.kt`, `CollectionHistoryService.kt`, `CollectionSnapshotJob.kt`, `SetTotalsService.kt`, `BinderService.kt`, `PokedexService.kt`, `PokedexImportService.kt`, `CardWishlistService.kt`, `TimedCache.kt` |
| Sources | `service/provider/PokeApiProvider.kt`, `PokeApiMapper.kt` |
| API | `controller/CollectionController.kt`, `SharedCollectionController.kt`, `PokedexController.kt`, `CardWishlistController.kt`, `BinderController.kt`, their orchestrators, `orchestration/CardListOrchestrator.kt`, `repository/CollectionProjections.kt` |
| Migration | `db/migration/V4__collection_and_pokedex.sql` |
| Pages | `views/collection/` |
| Building blocks | `components/collection/` (`ValueChart.vue`, `CollectionControls.vue`, `ProgressBar.vue`), `components/BinderPage.vue`, `components/account/BinderSettings.vue`, `CollectionShare.vue` |

## New environment variables

Both are in `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `POKEAPI_BASE_URL` | PokéAPI | `https://pokeapi.co/api/v2` |
| `COLLECTION_SNAPSHOT_CRON` | When the value of every collection is saved, in Dutch time. Keep this after the card sync. `-` switches it off | `0 0 7 * * *` |

## Upgrading a local database

Nothing to do by hand. Hibernate adds the new tables and the two new columns on `users` at startup, which was tested on a copy of a full database.

The Pokédex stays empty until the next Pokémon sync, the nightly one or one started at `/admin/sync`. The first import makes 1,025 requests and takes about 14 minutes, before the cards start. Later runs make one request. The value graph starts at the first morning snapshot.

## Checks

The tests with real data ran on a throwaway copy of the local database, with the new backend next to the running one. The copy was dropped afterwards.

| Check | Result |
|-------|--------|
| `./gradlew test` | 420 tests, 0 failed |
| `npm run build` | Passes |
| Browser, 360 and 1280px | 274 checks, 0 failed, no console errors or Vue warnings. No horizontal scroll on any page |
| Done when: progress and value update right away | Adding, changing and removing a card updates the bars and value without a reload, and it survives a reload |
| Done when: home binder | Source follows the setting, page buttons work, a logged-out visitor sees popular cards |
| Done when: Pokédex matches owned cards | A test collection with Base Set Charizard and Pikachu filled exactly #006 and #025. The API and a separate SQL query both counted 2 |
| Done when: unit tests | Progress, value, Pokédex and history calculations are covered |
| Value on real cards | €372,92 + €33.768,47 + $7.191,99 at 1,1224 = €40.549,08, worked out by hand and the same on the page |
| Pokédex import | Through a real Pokémon sync: 1,025 species, every generation boundary right, names such as `Farfetch’d`, `Type: Null` and `Nidoran♀` correct |
| Upgrade of a full database | The `postgres` profile adds everything at startup, no manual SQL |
| `prod` on an empty Postgres database | Flyway applies V1 to V4, Hibernate validation passes. A full Lorcana sync, the collection flow, the binder, sharing and the scheduled snapshot job all work on that schema |
| Simultaneous requests on Postgres | 20 additions of a new card at once: all succeed, quantity 20. Double clicks on the wishlist and binder succeed |
| Speed | Overview: 1.9s on the first load, then about 40ms. Most expensive cards: under 7ms with the new index |

The browser checks ran with temporary Playwright scripts that are not part of the repo. They cover logged-out visitors, the whole customer flow, the image rules, both progress modes, sharing from a second browser, currency switching, the keyboard, and the graph with 200 days of made-up history.

Checks and screenshots found these bugs, all fixed:

- A Pokédex slot with a priced and an unpriced card crashed, because a `Long` was compared with an `Int`.
- A same-day snapshot update would have been lost, because `@Transactional` doesn't apply to calls within a class.
- A double click could hit a unique constraint on the wishlist, the binder and the bulk add.
- Routes passed props that views don't declare.
- Tabular figures put wide gaps in percentages on this typeface.
- A broken card image showed a grey error tile in the hero.
- The "added" message appeared before the numbers refreshed.
- The line of the graph ran through the axis label in the corner.

## Decisions

- **Owned cards get a blue frame,** chosen by the owner. There is a white gap between the frame and the image, so nothing touches the card. Every tile keeps room for the frame, so a row stays aligned. Missing cards show "Gemist" below the image.
- **The share page shows the first name, progress, cards and value,** chosen by the owner. The profile name is also the name on orders, so the full name stays private. It has no value graph, because that shows when and how much someone added.
- **The value graph is the owner's idea,** after the portfolio graph in the Collectr app. Card prices have no history, and saving all of them would add about 190,000 rows a day. So one value per customer per day is saved, and only from the day someone starts: this can't be worked out afterwards. Today's point is live, so a new card moves the line right away.
- **The graph has no 1D button,** unlike Collectr, because there's one measurement a day. The periods are 7D, 1M, 3M, 6M, 1J and Alles.
- **The line includes cards added or removed.** It shows the value of the collection, not a market gain. A small line below the graph says so.
- **The scale of the graph doesn't start at zero,** like a stock chart, or a small move would be a flat line. The numbers along the side show the real size.
- **Saved values are in euros and converted at today's rate,** so a moving exchange rate can't look like a moving collection.
- **"Popular" means the cards in the most collections,** counted per customer and not per copy. A new shop has no collections, so the most expensive Pokémon cards fill up what's left, because this is mainly a Pokémon shop. Cards without an image are left out. The list has 45 cards and stays in memory for 10 minutes.
- **Normal progress counts card numbers,** as the plan says. Only One Piece has cards sharing a number: 4,988 cards with 3,711 numbers. The "Gemist" filter follows this: a card isn't missing when another card with its number is owned. The tile still shows "Gemist" for the alternate art itself.
- **Master progress counts every variant of every card,** and only variants the card still has, so a source that drops one can't push it past 100%.
- **A variant without a price stays without a price,** even if another variant has one. Guessing would make the value look more exact than it is.
- **Prices that can't be converted count as cards without a price,** never as if a dollar is a euro.
- **Bulk add leaves cards you already have alone,** so doing it twice changes nothing. It works per page: a page has 60 cards.
- **Catalog totals stay in memory for 10 minutes.** Counting them takes about 1.7 seconds. A single set is always counted live, so set pages are exact.
- **Reference-style pages stay pages:** a Pokédex slot opens its own URL rather than a modal, so it can be shared.
- **"Own selection" can hold any card,** owned or not, up to 81.

## Source notes

### PokéAPI

- `GET /pokemon-species?limit=5000` lists all species, but only with slug and link. English names are per species, so `GET /pokemon-species/{id}` is needed. Slugs can't be turned into names: `mr-mime`, `type-null`, `farfetchd`.
- Terms (`pokeapi.co/docs/v2`): "rate limiting has been removed entirely, but we still encourage you to limit the frequency of requests", "Locally cache resources whenever you request them", and breaking the fair use policy means a permanent IP ban. Known species are never requested again, and there's a 200ms pause between requests.
- The pages read don't mention a licence or attribution. "Pokémon and Pokémon character names are trademarks of Nintendo." The footer credits PokéAPI anyway.

### Collectr

The graph follows the idea of their portfolio screen: a large total, the change below it, a line chart and range buttons. Their site says holdings are "charted over time like a stock portfolio". Only the idea was used, not their design, images or text.

## Open issues

- **Three Mew cards of set `30th` link to images that don't exist** (404 at TCGdex, cards R, G and B). They're expensive, so a logged-out visitor sees them on page 1 of the home binder as empty pockets with the name. It goes away once customers have collections, or when TCGdex adds the images. Broken images can't be checked during a customer request.
- **The value graph starts empty for everyone.** Nothing can fill in the past, so a customer sees a first line on their second day.
- **A price history per card,** like the card page in Collectr, isn't built. It needs a daily copy of all prices, and can't be filled in afterwards either. The owner should decide if it's worth starting.
- **One Piece alternate arts** can show "Gemist" while their number counts as owned, see Decisions.

## Notes for later phases

- **Phase 4:** the list of missing cards needs a link to the matching single per row, in `SetMissingView.vue`.
- **Phase 4:** `/verlanglijst` exists for cards. Add products there, as a second part of the page.
- **Phase 4:** `CardCondition` is ready for singles.
- **Phase 7:** the buylist form can pick cards from the collection through `collection=owned` on the card endpoints.
- **Phase 8:** keep `/collectie/gedeeld/` out of the sitemap and add `noindex`, because these pages show a first name and a value.
- **Phase 8:** the privacy page has to mention the share link and the saved value history. Deleting an account has to remove collection data too.
- **Production:** the snapshot job runs at 07:00. Keep it after the sync, also if the sync takes longer on a server.
