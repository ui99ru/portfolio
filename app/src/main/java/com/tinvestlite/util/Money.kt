package com.tinvestlite.util

import com.tinvestlite.data.remote.dto.MoneyValue
import com.tinvestlite.data.remote.dto.Quotation
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val NANO = 1_000_000_000.0

fun Quotation.toBigDecimal(): BigDecimal =
    BigDecimal(units).add(BigDecimal(nano).movePointLeft(9))

fun MoneyValue.toBigDecimal(): BigDecimal =
    BigDecimal(units).add(BigDecimal(nano).movePointLeft(9))

fun Quotation.toDouble(): Double = units + nano / NANO

fun MoneyValue.toDouble(): Double = units + nano / NANO

/** Build a Quotation from a decimal value (used when sending limit-order prices). */
fun BigDecimal.toQuotation(): Quotation {
    val units = this.toBigInteger().toLong()
    val nano = this.subtract(BigDecimal(units))
        .movePointRight(9)
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()
    return Quotation(units = units, nano = nano)
}

object MoneyFormat {

    private val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    private val amountFormat = DecimalFormat("#,##0.00", symbols)
    private val priceFormat = DecimalFormat("#,##0.####", symbols)
    private val percentFormat = DecimalFormat("+#,##0.00;-#,##0.00", symbols)

    fun currencySymbol(code: String): String = when (code.uppercase()) {
        "RUB", "RUR" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "CNY" -> "¥"
        else -> code.uppercase()
    }

    fun amount(value: BigDecimal, currency: String? = null): String {
        val text = amountFormat.format(value)
        return if (currency.isNullOrBlank()) text else "$text ${currencySymbol(currency)}"
    }

    fun money(value: MoneyValue): String = amount(value.toBigDecimal(), value.currency)

    fun price(value: BigDecimal): String = priceFormat.format(value)

    fun price(value: Quotation): String = priceFormat.format(value.toBigDecimal())

    fun percent(value: Double): String = percentFormat.format(value) + "%"
}
