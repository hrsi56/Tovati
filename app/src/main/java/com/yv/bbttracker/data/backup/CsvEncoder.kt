package com.yv.bbttracker.data.backup

/** RFC 4180 CSV encoding tailored to the portable, human-readable export format. */
object CsvEncoder {
    val HEADER_COLUMNS: List<String> = listOf(
        "date",
        "cycle_day",
        "temperature_c",
        "time",
        "site",
        "selected_for_analysis",
        "disturbances",
        "sleep_minutes",
        "bleeding",
        "mucus",
        "mucus_sensation",
        "mucus_obscured",
        "lh_result",
        "lh_test_time",
        "lh_test_brand",
        "lh_test_sensitivity_miu",
        "ovulation_pain",
        "mood_flags",
        "mood_note",
        "libido_level",
        "sexual_contact",
        "sexual_contact_initiated_by_user",
        "physical_symptoms",
        "pain_relief_pill_count",
        "pain_relief_medication_note",
        "notes",
    )

    const val HEADER: String =
        "date,cycle_day,temperature_c,time,site,selected_for_analysis," +
            "disturbances,sleep_minutes,bleeding,mucus,mucus_sensation,mucus_obscured," +
            "lh_result,lh_test_time,lh_test_brand,lh_test_sensitivity_miu,ovulation_pain," +
            "mood_flags,mood_note,libido_level,sexual_contact,sexual_contact_initiated_by_user," +
            "physical_symptoms,pain_relief_pill_count,pain_relief_medication_note,notes"

    private const val UTF_8_BOM = '\uFEFF'
    private const val RECORD_SEPARATOR = "\r\n"

    /**
     * Encodes already-formatted values. In particular, callers remain responsible for formatting
     * decimal values with a dot rather than a locale-specific separator.
     */
    fun encode(rows: Iterable<List<String?>>): ByteArray = buildString {
        append(UTF_8_BOM)
        append(HEADER)
        append(RECORD_SEPARATOR)
        rows.forEach { row ->
            require(row.size == HEADER_COLUMNS.size) {
                "CSV row must contain exactly ${HEADER_COLUMNS.size} values"
            }
            append(row.joinToString(separator = ",") { value -> escape(value.orEmpty()) })
            append(RECORD_SEPARATOR)
        }
    }.toByteArray(Charsets.UTF_8)

    /** Escapes one field according to RFC 4180. */
    fun escape(value: String): String {
        val needsQuotes = value.any { character ->
            character == ',' || character == '"' || character == '\r' || character == '\n'
        }
        if (!needsQuotes) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                if (character == '"') append('"')
                append(character)
            }
            append('"')
        }
    }
}
