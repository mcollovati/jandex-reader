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
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER,
            ElementType.RECORD_COMPONENT})
    public @interface Marker {
        String value() default "";

        int[] codes() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface NotNull {
    }

    public interface Greeter {
        String greet(@Marker("who") String name);
    }

    @Marker(value = "svc", codes = {1, 2})
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

    public static class SpecialService extends Service {
    }

    public record Point(@Marker("x") int x, int y) {
    }

    public enum Color { RED, GREEN }
}
