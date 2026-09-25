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
package io.github.mcollovati.jandexreader.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Classes packaged into test JARs to exercise the commands.
 */
public class Fixtures {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
    })
    public @interface Marker {
        String value() default "";

        int[] codes() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface NotNull {}

    public interface Greeter {
        String greet(@Marker("who") String name);
    }

    @Marker(
            value = "svc",
            codes = {1, 2})
    public static class Service implements Greeter {

        @Marker
        private List<@NotNull String> names;

        @Override
        public String greet(String name) {
            return "hi " + name;
        }

        @Marker("m")
        protected <T extends Number> T first(List<T> items, String... rest) {
            return items.get(0);
        }
    }

    public static class SpecialService extends Service {}

    public record Point(@Marker("x") int x, int y) {}

    public enum Color {
        RED,
        GREEN
    }
}
