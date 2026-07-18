package com.yv.bbttracker.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CsvEncoderTest {
    @Test
    fun `output starts with UTF-8 BOM and exact header`() {
        val bytes = CsvEncoder.encode(emptyList())
        val requiredHeader =
            "date,cycle_day,temperature_c,time,site,selected_for_analysis," +
                "disturbances,sleep_minutes,bleeding,mucus,mucus_sensation,mucus_obscured," +
                "lh_result,lh_test_time,lh_test_brand,lh_test_sensitivity_miu,ovulation_pain," +
                "mood_flags,mood_note,libido_level,sexual_contact," +
                "sexual_contact_initiated_by_user,physical_symptoms,pain_relief_pill_count," +
                "pain_relief_medication_note,notes"

        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3).toByteArray())
        assertEquals("\uFEFF$requiredHeader\r\n", bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `Hebrew commas quotes and newlines use RFC 4180 escaping`() {
        val row = listOf(
            "2026-07-16",
            "3",
            "36.55",
            "06:45",
            "פה",
            "true",
            "שינה, קצרה",
            "420",
            "קל",
            "קרמי",
            "חלקלק",
            "false",
            "חיובי",
            "13:25",
            "מותג, בדיקה",
            "25",
            "לא נרשם",
            "HAPPY|ENERGETIC",
            "מצב רוח טוב",
            "HIGH",
            "SOME",
            "true",
            "BLOATING|HEADACHE",
            "2",
            "איבופרופן",
            "אמרה \"בוקר טוב\"\nואז מדדה",
        )

        val encoded = CsvEncoder.encode(listOf(row)).toString(Charsets.UTF_8)
        val requiredHeader =
            "date,cycle_day,temperature_c,time,site,selected_for_analysis," +
                "disturbances,sleep_minutes,bleeding,mucus,mucus_sensation,mucus_obscured," +
                "lh_result,lh_test_time,lh_test_brand,lh_test_sensitivity_miu,ovulation_pain," +
                "mood_flags,mood_note,libido_level,sexual_contact," +
                "sexual_contact_initiated_by_user,physical_symptoms,pain_relief_pill_count," +
                "pain_relief_medication_note,notes"

        assertEquals(
            "\uFEFF$requiredHeader\r\n" +
                "2026-07-16,3,36.55,06:45,פה,true,\"שינה, קצרה\",420,קל,קרמי,חלקלק,false," +
                "חיובי,13:25,\"מותג, בדיקה\",25,לא נרשם,HAPPY|ENERGETIC,מצב רוח טוב,HIGH,SOME,true," +
                "BLOATING|HEADACHE,2,איבופרופן," +
                "\"אמרה \"\"בוקר טוב\"\"\nואז מדדה\"\r\n",
            encoded,
        )
    }

    @Test
    fun `carriage returns and CRLF are quoted without normalization`() {
        assertEquals("\"a\rb\"", CsvEncoder.escape("a\rb"))
        assertEquals("\"a\r\nb\"", CsvEncoder.escape("a\r\nb"))
    }

    @Test
    fun `quote only fields are quoted and every quote is doubled`() {
        assertEquals("\"a\"\"b\"\"c\"", CsvEncoder.escape("a\"b\"c"))
        assertEquals("plain value", CsvEncoder.escape("plain value"))
    }

    @Test
    fun `multiple records preserve order and use CRLF record separators`() {
        val first = List<String?>(CsvEncoder.HEADER_COLUMNS.size) { index -> "first-$index" }
        val second = List<String?>(CsvEncoder.HEADER_COLUMNS.size) { index -> "second-$index" }

        val encoded = CsvEncoder.encode(listOf(first, second)).toString(Charsets.UTF_8)
        val records = encoded.removePrefix("\uFEFF").split("\r\n")

        assertEquals(4, records.size)
        assertEquals(CsvEncoder.HEADER, records[0])
        assertEquals(first.joinToString(","), records[1])
        assertEquals(second.joinToString(","), records[2])
        assertEquals("", records[3])
    }

    @Test
    fun `null fields are encoded as empty values`() {
        val row = List<String?>(CsvEncoder.HEADER_COLUMNS.size) { null }
        val encoded = CsvEncoder.encode(listOf(row)).toString(Charsets.UTF_8)

        assertTrue(encoded.endsWith(",".repeat(CsvEncoder.HEADER_COLUMNS.size - 1) + "\r\n"))
    }

    @Test
    fun `row width must match header`() {
        try {
            CsvEncoder.encode(listOf(listOf("only one value")))
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message.orEmpty().contains(CsvEncoder.HEADER_COLUMNS.size.toString()))
        }
    }
}
