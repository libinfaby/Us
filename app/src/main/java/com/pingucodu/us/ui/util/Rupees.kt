package com.pingucodu.us.ui.util

import java.text.NumberFormat
import java.util.Locale

private val IndianGrouping = Locale("en", "IN")

/** "₹18,000" for whole rupees, "₹18,000.50" otherwise - Indian digit grouping (₹1,50,000). */
fun formatRupees(cents: Long): String {
    val format = NumberFormat.getNumberInstance(IndianGrouping).apply {
        minimumFractionDigits = if (cents % 100 == 0L) 0 else 2
        maximumFractionDigits = minimumFractionDigits
    }
    return "₹" + format.format(cents / 100.0)
}
