package com.emetain.pokemonwebshop.service.provider

import java.time.LocalDate

/**
 * English release dates of the One Piece sets, because the OPTCG API has none. From Bandai's product pages at
 * en.onepiece-cardgame.com/products, checked on 2026-11-08. The first four starter decks had a pre-release in
 * September 2022: this is the date of the regular release. Add a new set here when Bandai announces it. Until then
 * it has no date and goes by the order of the API.
 */
object OptcgReleaseDates {

    fun of(code: String): LocalDate? = DATES[code]

    private val DATES: Map<String, LocalDate> = listOf(
        "OP-01" to "2022-12-02",
        "OP-02" to "2023-03-10",
        "OP-03" to "2023-06-30",
        "OP-04" to "2023-09-22",
        "OP-05" to "2023-12-08",
        "OP-06" to "2024-03-15",
        "OP-07" to "2024-06-28",
        "OP-08" to "2024-09-13",
        "OP-09" to "2024-12-13",
        "OP-10" to "2025-03-21",
        "OP-11" to "2025-06-06",
        "OP-12" to "2025-08-22",
        "OP-13" to "2025-11-07",
        "OP14-EB04" to "2026-01-16",
        "OP15-EB04" to "2026-04-03",
        "OP-16" to "2026-06-12",
        "OP-17" to "2026-08-28",
        "OP-18" to "2026-11-20",
        "OP-19" to "2027-03-05",
        "EB-01" to "2024-05-03",
        "EB-02" to "2025-05-09",
        "EB-03" to "2026-02-20",
        "EB-05" to "2026-10-30",
        "EB-06" to "2027-01-29",
        "PRB-01" to "2024-11-08",
        "PRB-02" to "2025-10-03",
        "PEB-01" to "2027-04-23",
        "ST-01" to "2022-12-02",
        "ST-02" to "2022-12-02",
        "ST-03" to "2022-12-02",
        "ST-04" to "2022-12-02",
        "ST-05" to "2023-02-03",
        "ST-06" to "2023-03-10",
        "ST-07" to "2023-06-30",
        "ST-08" to "2023-08-11",
        "ST-09" to "2023-08-11",
        "ST-10" to "2023-11-10",
        "ST-11" to "2024-02-02",
        "ST-12" to "2024-03-15",
        "ST-13" to "2024-04-19",
        "ST-14" to "2024-08-16",
        "ST-15" to "2024-10-25",
        "ST-16" to "2024-10-25",
        "ST-17" to "2024-10-25",
        "ST-18" to "2024-10-25",
        "ST-19" to "2024-10-25",
        "ST-20" to "2024-10-25",
        "ST-21" to "2025-03-14",
        "ST-22" to "2025-09-05",
        "ST-23" to "2025-06-06",
        "ST-24" to "2025-06-06",
        "ST-25" to "2025-06-06",
        "ST-26" to "2025-06-06",
        "ST-27" to "2025-06-06",
        "ST-28" to "2025-06-06",
        "ST-29" to "2026-01-16",
        "ST-30" to "2026-06-12",
        "ST-31" to "2026-07-31",
        "ST-32" to "2026-07-31",
        "ST-33" to "2026-07-31",
        "ST-34" to "2026-07-31",
        "ST-35" to "2026-07-31",
        "ST-36" to "2026-07-31",
        "ST-37" to "2026-12-18",
        "ST-38" to "2026-12-19",
    ).associate { (code, date) -> code to LocalDate.parse(date) }
}
