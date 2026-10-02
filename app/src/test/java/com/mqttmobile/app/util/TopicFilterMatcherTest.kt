package com.mqttmobile.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicFilterMatcherTest {
    @Test
    fun matchesExactAndSingleLevelWildcard() {
        assertTrue(TopicFilterMatcher.matches("device/01/report", "device/01/report"))
        assertTrue(TopicFilterMatcher.matches("device/01/report", "device/+/report"))
        assertFalse(TopicFilterMatcher.matches("device/01/status", "device/+/report"))
        assertFalse(TopicFilterMatcher.matches("device/01/report/raw", "device/+/report"))
    }

    @Test
    fun matchesMultiLevelWildcardOnlyAtEnd() {
        assertTrue(TopicFilterMatcher.matches("home/a/temperature", "home/#"))
        assertTrue(TopicFilterMatcher.matches("home", "home/#"))
        assertFalse(TopicFilterMatcher.matches("house/a/temperature", "home/#"))
    }

    @Test
    fun rejectsMalformedWildcardLevels() {
        assertTrue(TopicFilterMatcher.validate("device/#") == null)
        assertTrue(TopicFilterMatcher.validate("device/+/report") == null)
        assertTrue(TopicFilterMatcher.validate("device/foo#") != null)
        assertTrue(TopicFilterMatcher.validate("device/#/report") != null)
        assertTrue(TopicFilterMatcher.validate("device/foo+") != null)
    }
}
