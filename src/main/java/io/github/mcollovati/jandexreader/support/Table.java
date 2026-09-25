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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain text table with left-aligned, space-separated columns.
 */
public class Table {

    private final List<String[]> rows = new ArrayList<>();

    public Table(String... header) {
        rows.add(header);
    }

    public Table row(Object... cells) {
        String[] row = new String[cells.length];
        for (int i = 0; i < cells.length; i++) {
            row[i] = cells[i] == null ? "-" : cells[i].toString();
        }
        rows.add(row);
        return this;
    }

    public void print(PrintWriter out) {
        int columns = rows.get(0).length;
        int[] widths = new int[columns];
        for (String[] row : rows) {
            for (int i = 0; i < columns; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < columns; i++) {
                line.append(row[i]);
                if (i < columns - 1) {
                    line.append(" ".repeat(widths[i] - row[i].length() + 2));
                }
            }
            out.println(line.toString().stripTrailing());
        }
    }
}
