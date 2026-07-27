package com.cpz.processing.template.config;

import java.util.List;
import java.util.Objects;

/**
 * Defines a hot aisle and the datacenter columns associated with it.
 *
 * @param code stable identifier of the hot aisle
 * @param displayName text displayed in the user interface
 * @param columns columns associated with the hot aisle
 * @param layout physical layout of the hot aisle
 *
 * @author CPZ
 */
public record HotAisleDefinition(
        String code,
        String displayName,
        List<String> columns,
        Layout layout
) {

    /**
     * Creates a hot aisle definition.
     */
    public HotAisleDefinition {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Hot aisle code must not be null or blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Hot aisle display name must not be null or blank");
        Objects.requireNonNull(columns, "Hot aisle columns must not be null");
        if (columns.isEmpty()) throw new IllegalArgumentException("Hot aisle must contain at least one column");
        for (String column : columns)
            if (column == null || column.isBlank()) throw new IllegalArgumentException("Hot aisle columns must not contain null or blank values");
        if (columns.stream().distinct().count() != columns.size()) throw new IllegalArgumentException("Hot aisle columns must not contain duplicates");
        columns = List.copyOf(columns);
        Objects.requireNonNull(layout, "Hot aisle layout must not be null");
        validateLayout(columns, layout);
    }

    private static void validateLayout(
            List<String> columns,
            Layout layout
    ) {
        switch (layout) {
            case WALL -> {
                if (columns.size() != 1) throw new IllegalArgumentException("A WALL hot aisle must contain exactly one column");
            }
            case SHARED -> {
                if (columns.size() != 2) throw new IllegalArgumentException("A SHARED hot aisle must contain exactly two columns");
            }
        }
    }

    /**
     * Describes the physical arrangement of a hot aisle.
     */
    public enum Layout {

        /**
         * Hot aisle located between one datacenter column and a wall.
         */
        WALL,

        /**
         * Hot aisle shared by two datacenter columns.
         */
        SHARED
    }
}
