package com.example.kulapro.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Prices, in and out.
 *
 * Money is stored as whole cents rather than a decimal, because a price held as a floating
 * point number eventually charges someone 1249.9999 for a 1250 dish. Everything that turns a
 * typed price into storage, or storage back into something a person reads, comes through
 * here so the two cannot drift apart.
 */
object Money {

    /**
     * Reads a price a person typed, or null if it is not a price.
     *
     * Null rather than zero: a form that quietly stores nothing when it cannot read what was
     * typed puts a free dish on the menu, and nobody finds out until someone orders it.
     */
    fun parseToCents(typed: String): Long? {
        val digitsAndMarks = typed.filter { it.isDigit() || it == '.' || it == ',' }
        if (digitsAndMarks.none { it.isDigit() }) return null

        val units = decimalForm(digitsAndMarks)
        val (whole, fraction) = units.split('.').let { it[0] to it.getOrElse(1) { "" } }
        if (fraction.length > FRACTION_DIGITS) return null

        val wholeCents = whole.ifEmpty { "0" }.toLongOrNull() ?: return null
        val fractionCents = fraction.padEnd(FRACTION_DIGITS, '0').toLongOrNull() ?: return null
        return wholeCents * CENTS_PER_UNIT + fractionCents
    }

    /**
     * Writes a stored price for a person to read.
     *
     * An unrecognised currency code is bad data, not a reason to show a crash or a blank, so
     * it falls back to printing the code beside the amount.
     */
    fun format(priceCents: Long, currencyCode: String): String {
        val units = priceCents.toBigDecimal().movePointLeft(FRACTION_DIGITS)
        return runCatching {
            NumberFormat.getCurrencyInstance(Locale.getDefault())
                .apply { currency = Currency.getInstance(currencyCode) }
                .format(units)
        }.getOrElse { "$currencyCode ${units.toPlainString()}" }
    }

    /** The plain number to put back in an edit field, with no symbol and no grouping. */
    fun toEditableText(priceCents: Long): String =
        priceCents.toBigDecimal().movePointLeft(FRACTION_DIGITS).toPlainString()

    /**
     * Rewrites a typed amount with one full stop for the decimal mark and nothing else.
     *
     * A comma is the decimal mark in half the world and the thousands separator in the other
     * half, so position decides rather than the character: the last mark is the decimal point
     * unless exactly three digits follow it, which is the shape of a thousands group. That
     * reads "1,250" and "1.250" both as one thousand two hundred and fifty, and "1250,50"
     * and "1250.50" both as twelve fifty and a half.
     */
    private fun decimalForm(value: String): String {
        val decimalAt = maxOf(value.lastIndexOf(','), value.lastIndexOf('.'))
        val digitsOnly = value.filter { it.isDigit() }
        if (decimalAt < 0) return digitsOnly

        val trailingDigits = value.length - decimalAt - 1
        if (trailingDigits == GROUP_SIZE || trailingDigits == 0) return digitsOnly

        val whole = value.take(decimalAt).filter { it.isDigit() }
        val fraction = value.substring(decimalAt + 1).filter { it.isDigit() }
        return "$whole.$fraction"
    }
}

private const val CENTS_PER_UNIT = 100L
private const val FRACTION_DIGITS = 2
private const val GROUP_SIZE = 3
