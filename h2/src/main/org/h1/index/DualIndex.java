/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.index;

import org.h1.command.dml.AllColumnsForPlan;
import org.h1.engine.Session;
import org.h1.result.SearchRow;
import org.h1.result.SortOrder;
import org.h1.table.DualTable;
import org.h1.table.IndexColumn;
import org.h1.table.TableFilter;

/**
 * An index for the DUAL table.
 */
public class DualIndex extends VirtualTableIndex {

    public DualIndex(DualTable table) {
        super(table, "DUAL_INDEX", new IndexColumn[0]);
    }

    @Override
    public Cursor find(Session session, SearchRow first, SearchRow last) {
        return new DualCursor(session);
    }

    @Override
    public double getCost(Session session, int[] masks, TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {
        return 1d;
    }

    @Override
    public String getCreateSQL() {
        return null;
    }

    @Override
    public boolean canGetFirstOrLast() {
        return true;
    }

    @Override
    public Cursor findFirstOrLast(Session session, boolean first) {
        return new DualCursor(session);
    }

    @Override
    public String getPlanSQL() {
        return "dual index";
    }

}
