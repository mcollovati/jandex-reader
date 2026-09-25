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
