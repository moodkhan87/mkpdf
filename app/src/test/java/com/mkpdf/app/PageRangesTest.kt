package com.mkpdf.app
import org.junit.Assert.*
import org.junit.Test
class PageRangesTest {
    @Test fun indexesAreZeroBased() {assertEquals(listOf(0,2,3,4),parsePageRange("1,3-5"))}
    @Test fun allIsNull() {assertNull(parsePageRange("all"));assertNull(parsePageRange(""))}
    @Test(expected=IllegalArgumentException::class) fun rejectsZero() {parsePageRange("0")}
    @Test(expected=IllegalArgumentException::class) fun rejectsReverse() {parsePageRange("4-2")}
}
