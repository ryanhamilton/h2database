/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression.analysis;

/**
 * Window frame bound type.
 */
public enum WindowFrameBoundType {

    /**
     * UNBOUNDED PRECEDING clause.
     */
    UNBOUNDED_PRECEDING("UNBOUNDED PRECEDING"),

    /**
     * PRECEDING clause.
     */
    PRECEDING("PRECEDING"),

    /**
     * CURRENT_ROW clause.
     */
    CURRENT_ROW("CURRENT ROW"),

    /**
     * FOLLOWING clause.
     */
    FOLLOWING("FOLLOWING"),

    /**
     * UNBOUNDED FOLLOWING clause.
     */
    UNBOUNDED_FOLLOWING("UNBOUNDED FOLLOWING");

    private final String sql;

    private WindowFrameBoundType(String sql) {
        this.sql = sql;
    }

    /**
     * Returns SQL representation.
     *
     * @return SQL representation.
     * @see org.h1.expression.Expression#getSQL(boolean)
     */
    public String getSQL() {
        return sql;
    }

}
