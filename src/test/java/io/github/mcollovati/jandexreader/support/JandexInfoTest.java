/*
 * Copyright 2026 Marco Collovati
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
