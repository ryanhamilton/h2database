/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.index;

import org.h1.command.dml.AllColumnsForPlan;
import org.h1.engine.Session;
import org.h1.message.DbException;
import org.h1.result.SearchRow;
import org.h1.result.SortOrder;
import org.h1.table.FunctionTable;
import org.h1.table.IndexColumn;
import org.h1.table.TableFilter;
import org.h1.table.VirtualConstructedTable;

/**
 * An index for a virtual table that returns a result set. Search in this index
 * performs scan over all rows and should be avoided.
 */
public class VirtualConstructedTableIndex extends VirtualTableIndex {

    private final VirtualConstructedTable table;

    public VirtualConstructedTableIndex(VirtualConstructedTable table, IndexColumn[] columns) {
        super(table, null, columns);
        this.table = table;
    }

    @Override
    public boolean isFindUsingFullTableScan() {
        return true;
    }

    @Override
    public Cursor find(Session session, SearchRow first, SearchRow last) {
        return new VirtualTableCursor(this, first, last, session, table.getResult(session));
    }

    @Override
    public double getCost(Session session, int[] masks, TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {
        if (masks != null) {
            throw DbException.getUnsupportedException("Virtual table");
        }
        long expectedRows;
        if (table.canGetRowCount()) {
            expectedRows = table.getRowCountApproximation();
        } else {
            expectedRows = database.getSettings().estimatedFunctionTableRows;
        }
        return expectedRows * 10;
    }

    @Override
    public boolean canGetFirstOrLast() {
        return false;
    }

    @Override
    public String getPlanSQL() {
        return table instanceof FunctionTable ? "function" : "table scan";
    }

    @Override
    public boolean canScan() {
        return false;
    }

}
