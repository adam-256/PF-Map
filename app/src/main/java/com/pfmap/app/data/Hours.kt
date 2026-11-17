package com.pfmap.app.data

/** Turns OSM-style opening_hours ("Mo-Th 05:00-23:00; Fr 05:00-21:00") into readable lines. */
object Hours {
    private val days = mapOf("Mo" to "Mon", "Tu" to "Tue", "We" to "Wed", "Th" to "Thu", "Fr" to "Fri", "Sa" to "Sat", "Su" to "Sun", "PH" to "Holidays")
    private val time = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val nextRule = Regex(""",\s+(?=(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)\b)""")
    private val day = Regex("""\b(Mo|Tu|We|Th|Fr|Sa|Su|PH)\b""")

    fun humanize(raw: String): List<String> {
        val s = raw.trim()
        if (s.isEmpty()) return emptyList()
        if (s == "24/7" || s.equals("Mo-Su 00:00-24:00", true)) return listOf("Open 24/7")
        // Rules are separated by ";" (and, in some OSM data, by ", " before the next day range).
        return s.split(';').flatMap { it.split(nextRule) }.map { it.trim() }.filter { it.isNotEmpty() }.map { rule ->
            var r = day.replace(rule) { days.getValue(it.value) }
            r = r.replace("off", "Closed").replace("-", "–")
            time.replace(r) { m -> clock(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        }
    }

    private fun clock(h: Int, m: Int): String {
        if (h == 24 || (h == 0 && m == 0)) return "12 AM"
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return if (m == 0) "$h12 $suffix" else "$h12:%02d $suffix".format(m)
    }
}
