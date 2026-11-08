package io.github.ukpratik.mukhar.domain.revise

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewEstimateTest {
    @Test fun `grows with the due count`() {
        assertEquals(3, ReviewEstimate.minutes(10))
        assertEquals(8, ReviewEstimate.minutes(30))
        assertEquals(13, ReviewEstimate.minutes(50))
    }

    @Test fun `at least a minute`() {
        assertEquals(1, ReviewEstimate.minutes(0))
        assertEquals(1, ReviewEstimate.minutes(1))
        assertEquals(1, ReviewEstimate.minutes(4))
        assertEquals(2, ReviewEstimate.minutes(5))
    }
}
