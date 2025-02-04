/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.pagestore.db;

import org.h1.index.Cursor;
import org.h1.message.DbException;
import org.h1.result.Row;
import org.h1.result.SearchRow;

/**
 * The cursor implementation for the scan index.
 */
public class ScanCursor implements Cursor {
    private final ScanIndex scan;
    private Row row;

    ScanCursor(ScanIndex scan) {
        this.scan = scan;
        row = null;
    }

    @Override
    public Row get() {
        return row;
    }

    @Override
    public SearchRow getSearchRow() {
        return row;
    }

    @Override
    public boolean next() {
        row = scan.getNextRow(row);
        return row != null;
    }

    @Override
    public boolean previous() {
        throw DbException.throwInternalError(toString());
    }

}
