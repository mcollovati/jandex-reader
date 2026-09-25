package io.github.mcollovati.jandexreader.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void filter_substring() {
        Pattern pattern = Names.filter("acme");
        assertTrue(Names.matches(pattern, "com.acme.Foo"));
        assertFalse(Names.matches(pattern, "com.example.Foo"));
    }

    @Test
    void filter_glob() {
        Pattern pattern = Names.filter("com.acme.*Service");
        assertTrue(Names.matches(pattern, "com.acme.MyService"));
        assertTrue(Names.matches(pattern, "com.acme.impl.MyService"));
        assertFalse(Names.matches(pattern, "com.acme.MyServiceImpl"));
        assertTrue(Names.matches(Names.filter("get?"), "getX"));
    }

    @Test
    void filter_null_matchesAll() {
        assertTrue(Names.matches(Names.filter(null), "anything"));
    }

    @Test
    void matching_simpleAndNestedNames() {
        List<String> names = List.of("a.b.Outer", "a.b.Outer$Inner", "c.Inner");
        assertEquals(List.of("a.b.Outer$Inner"), Names.matching(names, "Outer.Inner"));
        assertEquals(List.of("a.b.Outer$Inner", "c.Inner"), Names.matching(names, "Inner"));
        assertEquals(List.of("a.b.Outer"), Names.matching(names, "a.b.Outer"));
    }
}
