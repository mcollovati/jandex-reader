package io.github.mcollovati.jandexreader.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesNestedStructures() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "a\"b\\c\nd\u0001");
        map.put("count", 3);
        map.put("flag", true);
        map.put("none", null);
        map.put("empty", List.of());
        map.put("items", List.of(Map.of("k", "v"), 1.5));
        assertEquals("""
                {
                  "name": "a\\"b\\\\c\\nd\\u0001",
                  "count": 3,
                  "flag": true,
                  "none": null,
                  "empty": [],
                  "items": [
                    {
                      "k": "v"
                    },
                    1.5
                  ]
                }""", Json.write(map));
    }

    @Test
    void outputIsValidJson() throws Exception {
        List<Object> list = new ArrayList<>();
        list.add("\t  unicode ✓");
        list.add(Map.of());
        list.add(null);
        JsonNode node = new ObjectMapper().readTree(Json.write(list));
        assertEquals("\t  unicode ✓", node.get(0).asText());
        assertEquals(3, node.size());
    }
}
