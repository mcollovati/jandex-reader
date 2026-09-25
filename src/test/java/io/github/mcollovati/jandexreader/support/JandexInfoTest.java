package io.github.mcollovati.jandexreader.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class JandexInfoTest {

    @Test
    void supportedFormatVersions_includeKnownVersions() {
        List<Integer> versions = JandexInfo.supportedFormatVersions();
        assertTrue(versions.containsAll(List.of(2, 3, 6, 10, 13)), versions.toString());
    }

    @Test
    void ranges() {
        assertEquals("2-3, 6-13", JandexInfo.ranges(List.of(2, 3, 6, 7, 8, 9, 10, 11, 12, 13)));
        assertEquals("1, 3-4, 7", JandexInfo.ranges(List.of(1, 3, 4, 7)));
        assertEquals("", JandexInfo.ranges(List.of()));
    }
}
