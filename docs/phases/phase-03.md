# Phase 3: Collection and Pokédex

Customers track what they own in all four games, see their progress and what it's worth, and fill a Pokédex.

## What was built

**Backend**

- `CollectionEntry` (user, card, variant, quantity, optional condition) with endpoints to add, change and remove, and a bulk add. Every change answers with the card's entries and the new progress and value of its set, so the page updates without a second request.
- Progress per set, per game and overall, for normal and master set. Totals come from the cards we really have, not from `CardSet.cardCount`, so a set can always reach 100%.
- Collection value: quantity × trend price of the owned variant, added up per currency in `BigDecimal` and converted and rounded once. Cards without a usable price are counted separately.
- `ExchangeRates` does all currency conversion, including the sort price from phase 2.
- `PokemonSpecies`, imported from PokéAPI right after startup and at the start of every Pokémon sync. `GET /api/v1/pokedex` gives all slots with progress overall and per generation, and `/pokedex/{number}/cards` the cards of one Pokémon.
- Card wishlist, separate from the product wishlist in phase 4.
- Public share link with a random slug, off by default. Stopping closes the link for good.
- `CollectionValueSnapshot`: one value per customer per day, saved by `CollectionSnapshotJob`. `GET /api/v1/collection/history` returns these with today's live value as the last point.
- Home binder: `GET /api/v1/binder` with five sources, the choice saved on the user, and a list of chosen cards for "own selection".
- `GET /api/v1/admin/catalog/images/cards` lists cards for the image page with filters and sorting, 25, 50 or 100 per page (`size`). Any other size means 25.
- `Card.customImageUrl` and `CardSet.customLogoUrl`: pictures the shop uploaded, which go before those of the source. `MediaStorage` keeps the files on disk, and `/api/v1/media/...` serves them. Phase 4 can use it for product images.
- Card lists now show what the viewer owns, and filter on owned and missing. `CardListOrchestrator` holds what all lists share. `@CurrentUser` can be nullable on public endpoints.

**Frontend**

- Card tiles: a + button next to the price for logged-in customers. Clicking it adds the card and shows the count with a - in the same element, while the + stays in its place.
- Set pages: a blue frame around owned cards, "In bezit" or "Gemist" below the image, a progress panel with a toggle between normal and master set, a collection filter, and a button to add a whole page.
- `/collectie`: total value, a value graph, progress per game, Pokédex progress, most valuable cards and all started sets.
- `/kaarten/:game/:setCode/nog-niet-in-bezit` (`/gemist` until 2026-11-10): the list of missing cards, with the usual sorting and filters.
- `/collectie/pokedex`: all 1,025 Pokémon with a filter and progress bar per generation. `/collectie/pokedex/:number` shows all cards of one Pokémon.
- `/verlanglijst`: the card wishlist.
- `/collectie/gedeeld/:slug`: the public page.
- Home: the binder in the hero opens like a real binder: page 1 next to the inside of the cover, then pages 2 and 3, then 4 and 5. A phone turns one page at a time. It says what it shows.
- Card page: what you own per variant with condition, the wishlist and the binder.
- My account: what the binder shows, the own selection, and the share link.
- `/admin/afbeeldingen`: browse all cards or sets, filter on name, game, set and "without a picture", sort by set, name or Pokédex number, upload a picture, or go back to the original. A dropdown sets 25, 50 or 100 per page, 25 by default, for cards and sets alike. Filters, page size and page live in the URL.
- Logged-out visitors get a short explanation and a register button on `/collectie`, `/collectie/pokedex` and `/verlanglijst`.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/CollectionEntry.kt`, `CollectionValueSnapshot.kt`, `CardWishlistEntry.kt`, `BinderSelection.kt`, `BinderSource.kt`, `CardCondition.kt`, `PokemonSpecies.kt` |
| Calculations | `service/CollectionMath.kt`, `PokedexMath.kt`, `ExchangeRates.kt`, `CollectionHistoryMath` in `CollectionHistoryService.kt` |
| Services | `service/CollectionService.kt`, `CollectionStatsService.kt`, `CollectionHistoryService.kt`, `CollectionSnapshotJob.kt`, `SetTotalsService.kt`, `BinderService.kt`, `PokedexService.kt`, `PokedexImportService.kt`, `CardWishlistService.kt`, `TimedCache.kt` |
| Sources | `service/provider/PokeApiProvider.kt`, `PokeApiMapper.kt` |
| API | `controller/CollectionController.kt`, `SharedCollectionController.kt`, `PokedexController.kt`, `CardWishlistController.kt`, `BinderController.kt`, their orchestrators, `orchestration/CardListOrchestrator.kt`, `repository/CollectionProjections.kt` |
| Images | `service/MediaStorage.kt`, `CatalogImageService.kt`, `controller/AdminCatalogImageController.kt`, `MediaController.kt`, `views/admin/AdminImagesView.vue` |
| Migration | `db/migration/V4__collection_and_pokedex.sql` |
| Pages | `views/collection/` |
| Building blocks | `components/collection/` (`ValueChart.vue`, `CollectionControls.vue`, `ProgressBar.vue`), `components/BinderPage.vue`, `components/account/BinderSettings.vue`, `CollectionShare.vue` |

## New environment variables

All are in `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `POKEAPI_BASE_URL` | PokéAPI | `https://pokeapi.co/api/v2` |
| `COLLECTION_SNAPSHOT_CRON` | When the value of every collection is saved, in Dutch time. Keep this after the card sync. `-` switches it off | `0 0 7 * * *` |
| `MEDIA_DIRECTORY` | Where uploaded images are stored. A relative path starts at the folder the backend runs in | `data/media` |

## Upgrading a local database

Nothing to do by hand, but the backend has to be restarted: a running Phase 2 build answers 404 on everything from this phase. Hibernate then adds the new tables and columns at startup, which was tested on a copy of a full database.

The Pokédex fills by itself after startup, in about 14 minutes the first time, while the rest of the site works normally. Later starts make one request. The value graph starts at the first morning snapshot.

## Checks

The tests with real data ran on a throwaway copy of the local database, with the new backend next to the running one. The copy was dropped afterwards.

| Check | Result |
|-------|--------|
| `./gradlew test` | 438 tests, 0 failed |
| `npm run build` | Passes |
| Browser, 360 and 1280px | 455 checks, 0 failed, no console errors or Vue warnings. No horizontal scroll on any page. The binder also at 768 and 1440px |
| Done when: progress and value update right away | Adding, changing and removing a card updates the bars and value without a reload, and it survives a reload |
| Done when: home binder | Source follows the setting, turning pages works both ways and keeps the place when the window changes size, a logged-out visitor sees only Pokémon cards |
| Done when: Pokédex matches owned cards | A test collection with Base Set Charizard and Pikachu filled exactly #006 and #025. The API and a separate SQL query both counted 2 |
| Done when: unit tests | Progress, value, Pokédex and history calculations are covered |
| Value on real cards | €372,92 + €33.768,47 + $7.191,99 at 1,1224 = €40.549,08, worked out by hand and the same on the page |
| Pokédex import | Through a real Pokémon sync: 1,025 species, every generation boundary right, names such as `Farfetch’d`, `Type: Null` and `Nidoran♀` correct |
| Upgrade of a full database | The `postgres` profile adds everything at startup, no manual SQL, and the Pokédex fills without a sync |
| Cards without a picture | The filter finds exactly as many cards as a direct database count, per game and per set. Sorting by Pokédex number puts cards without one last |
| Cards per page on the image page | 25 by default. 50 and 100 show 50 and 100 rows on a copy of the full catalog, for cards and sets. The size stays when turning pages, filtering, clearing filters and switching tabs. A changed size starts at page 1. A size typed into the URL (`33`, `abc`) shows 25, in the dropdown too. No horizontal scroll at 360px |
| Image upload | A real upload shows for a logged-out visitor, a sync leaves it alone, going back removes the file. A fake image and a file over 5 MB are refused with a Dutch message, also past the browser check |
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

After the first review the owner reported that adding cards, the binder choice, the Pokédex and the wishlist didn't work. All four came from one cause: the backend on port 8080 was still the Phase 2 build, so the new endpoints answered 404. The Pokédex now also fills without a sync, so a restart is all an upgrade needs.

## Decisions

- **Owned cards get a blue frame,** chosen by the owner. There is a white gap between the frame and the image, so nothing touches the card. Every tile keeps room for the frame, so a row stays aligned. Missing cards showed "Gemist" below the image. On 2026-11-10 the owner had that label taken out, and the filter and the list were renamed "Nog niet in bezit".
- **The share page shows the first name, progress, cards and value,** chosen by the owner. The profile name is also the name on orders, so the full name stays private. It has no value graph, because that shows when and how much someone added.
- **The value graph is the owner's idea,** after the portfolio graph in the Collectr app. Card prices have no history, and saving all of them would add about 190,000 rows a day. So one value per customer per day is saved, and only from the day someone starts: this can't be worked out afterwards. Today's point is live, so a new card moves the line right away.
- **The graph has no 1D button,** unlike Collectr, because there's one measurement a day. The periods are 7D, 1M, 3M, 6M, 1J and Alles.
- **The line includes cards added or removed.** It shows the value of the collection, not a market gain. A small line below the graph says so.
- **The scale of the graph doesn't start at zero,** like a stock chart, or a small move would be a flat line. The numbers along the side show the real size.
- **Saved values are in euros and converted at today's rate,** so a moving exchange rate can't look like a moving collection.
- **The binder shows the most expensive Pokémon cards to logged-out visitors,** and a customer's own most expensive cards after logging in, as the owner asked. This replaces the popular cards the plan had for visitors. A customer who chooses "De duurste kaarten" sees every game.
- **The binder opens like a real one,** the owner's idea: page 1 alone on the right with the inside of the cover on the left, then two pages at a time. Below 560 pixels of binder width the pockets would be too small, so a phone turns one page at a time. Changing the window size keeps the page you were on.
- **A choice for the binder always stays.** A customer who chose something sees that source, even while it's empty, with a line saying how to fill it. Only a customer who chose nothing and has no cards gets the most expensive cards in general. The choice is made in My account only: the owner tried a picker on the home page and didn't want it there.
- **"Popular" means the cards in the most collections,** counted per customer and not per copy. A new shop has no collections, so the most expensive Pokémon cards fill up what's left, because this is mainly a Pokémon shop. Cards without an image are left out. The list has 45 cards and stays in memory for 10 minutes.
- **Normal progress counts card numbers,** as the plan says. Only One Piece has cards sharing a number: 4,988 cards with 3,711 numbers. The "Gemist" filter follows this: a card isn't missing when another card with its number is owned. The tile still shows "Gemist" for the alternate art itself.
- **Master progress counts every variant of every card,** and only variants the card still has, so a source that drops one can't push it past 100%.
- **A variant without a price stays without a price,** even if another variant has one. Guessing would make the value look more exact than it is.
- **Prices that can't be converted count as cards without a price,** never as if a dollar is a euro.
- **Bulk add leaves cards you already have alone,** so doing it twice changes nothing. It works per page: a page has 60 cards.
- **Catalog totals stay in memory for 10 minutes.** Counting them takes about 1.7 seconds. A single set is always counted live, so set pages are exact.
- **Uploaded pictures sit next to the source's images, not over them.** A sync keeps updating the source's URL, and going back shows it again. The rule that card images are never changed is about the source's images: replacing one with your own is not changing it. The owner has to have the right to use what they upload.
- **"Without a picture" means no image link at all.** A link that doesn't work, like the three Mews, can't be checked without fetching every image, so those show as "Geen afbeelding" in the list instead.
- **Only JPG, PNG and WebP, up to 5 MB.** The type comes from the first bytes of the file and not from its name. SVG is left out because it can hold scripts. Files get a random name, and only names we made can be fetched.
- **The Pokédex loads right after startup,** because waiting for a sync left the page empty after an upgrade. Not in `dev`, where the database starts empty every time and it would cost 1,025 requests per restart.
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

- **Three Mew cards of set `30th` link to images that don't exist** (404 at TCGdex, cards R, G and B). The owner can upload a picture for these at `/admin/afbeeldingen`. In the binder such a card shows as an empty pocket with its name. Broken images can't be checked during a customer request.
- **The value graph starts empty for everyone.** Nothing can fill in the past, so a customer sees a first line on their second day.
- **A price history per card,** like the card page in Collectr, isn't built. It needs a daily copy of all prices, and can't be filled in afterwards either. The owner should decide if it's worth starting.
- **One Piece alternate arts** can show "Gemist" while their number counts as owned, see Decisions.

## Notes for later phases

- **Phase 4:** the list of missing cards needs a link to the matching single per row, in `SetMissingView.vue`.
- **Phase 4:** `/verlanglijst` exists for cards. Add products there, as a second part of the page.
- **Phase 4:** `CardCondition` is ready for singles.
- **Phase 4:** use `MediaStorage` and `ImageFormat` for product images.
- **Phase 4:** the owner wants every product in the shop, also without stock. The plan now has a product list import and the sources checked for it.
- **Phase 7:** the buylist form can pick cards from the collection through `collection=owned` on the card endpoints.
- **Phase 8:** keep `/collectie/gedeeld/` out of the sitemap and add `noindex`, because these pages show a first name and a value.
- **Phase 8:** the privacy page has to mention the share link and the saved value history. Deleting an account has to remove collection data too.
- **Production:** the snapshot job runs at 07:00. Keep it after the sync, also if the sync takes longer on a server.
