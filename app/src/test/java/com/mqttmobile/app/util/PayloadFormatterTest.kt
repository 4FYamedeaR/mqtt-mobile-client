package com.mqttmobile.app.util

import com.mqttmobile.app.data.model.PayloadFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadFormatterTest {
    @Test
    fun formatsAndCompactsJson() {
        val payload = "{\"device\":{\"id\":\"01\"},\"values\":[1,2]}".toByteArray()
        val pretty = PayloadFormatter.prettyJson(payload)
        assertNotNull(pretty)
        assertTrue(pretty!!.contains("\n"))
        val compact = PayloadFormatter.compactJson(pretty)
        assertNotNull(compact)
        assertTrue(compact!!.contains("\"device\""))
        assertTrue(compact.contains("\"values\""))
        assertNotNull(PayloadFormatter.jsonValue(payload))
    }

    @Test
    fun parsesHexAndTextPayloads() {
        assertArrayEquals(byteArrayOf(0x01, 0x2A), PayloadFormatter.parse("01 2A", PayloadFormat.HEX).getOrThrow())
        org.junit.Assert.assertEquals("hello", PayloadFormatter.parse("hello", PayloadFormat.TEXT).getOrThrow().toString(Charsets.UTF_8))
    }
}
