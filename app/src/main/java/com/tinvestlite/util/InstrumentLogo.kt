package com.tinvestlite.util

import com.tinvestlite.data.remote.dto.Brand

/**
 * Builds the logo URL served by the T-Invest brand CDN.
 *
 * The API returns a [Brand.logoName] like "TCSG.png"; the CDN expects the base
 * name with a size suffix, e.g. ".../TCSGx160.png". Returns null when there is
 * no logo so the UI can fall back to a monogram.
 */
object InstrumentLogo {

    private const val CDN = "https://invest-brands.cdn-tinkoff.ru/"
    private const val SIZE = "x160.png"

    fun url(logoName: String?): String? {
        if (logoName.isNullOrBlank()) return null
        val base = logoName.removeSuffix(".png").removeSuffix(".svg")
        if (base.isBlank()) return null
        return "$CDN$base$SIZE"
    }

    fun url(brand: Brand?): String? = url(brand?.logoName)
}
