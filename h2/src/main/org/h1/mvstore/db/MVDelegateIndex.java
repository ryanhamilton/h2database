/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.mvstore.db;

import java.util.List;

import org.h1.command.dml.AllColumnsForPlan;
import org.h1.engine.Session;
import org.h1.index.BaseIndex;
import org.h1.index.Cursor;
import org.h1.index.IndexType;
import org.h1.message.DbException;
import org.h1.mvstore.MVMap;
import org.h1.result.Row;
import org.h1.result.SearchRow;
import org.h1.result.SortOrder;
import org.h1.table.Column;
import org.h1.table.IndexColumn;
import org.h1.table.TableFilter;
import org.h1.value.Value;
import org.h1.value.ValueLong;
import org.h1.value.VersionedValue;

/**
 * An index that delegates indexing to another index.
 */
public class MVDelegateIndex extends BaseIndex implements MVIndex {

    private final MVPrimaryIndex mainIndex;

    public MVDelegateIndex(MVTable table, int id, String name,
            MVPrimaryIndex mainIndex,
            IndexType indexType) {
        super(table, id, name,
                IndexColumn.wrap(new Column[] { table.getColumn(mainIndex.getMainIndexColumn()) }),
                indexType);
        this.mainIndex = mainIndex;
        if (id < 0) {
            throw DbException.throwInternalError(name);
        }
    }

    @Override
    public void addRowsToBuffer(List<Row> rows, String bufferName) {
        throw DbException.throwInternalError();
    }

    @Override
    public void addBufferedRows(List<String> bufferNames) {
        throw DbException.throwInternalError();
    }

    @Override
    public MVMap<Value, VersionedValue> getMVMap() {
        return mainIndex.getMVMap();
    }

    @Override
    public void add(Session session, Row row) {
        // nothing to do
    }

    @Override
    public Row getRow(Session session, long key) {
        return mainIndex.getRow(session, key);
    }

    @Override
    public boolean isRowIdIndex() {
        return true;
    }

    @Override
    public boolean canGetFirstOrLast() {
        return true;
    }

    @Override
    public void close(Session session) {
        // nothing to do
    }

    @Override
    public Cursor find(Session session, SearchRow first, SearchRow last) {
        ValueLong min = mainIndex.getKey(first, ValueLong.MIN, ValueLong.MIN);
        // ifNull is MIN as well, because the column is never NULL
        // so avoid returning all rows (returning one row is OK)
        ValueLong max = mainIndex.getKey(last, ValueLong.MAX, ValueLong.MIN);
        return mainIndex.find(session, min, max);
    }

    @Override
    public Cursor findFirstOrLast(Session session, boolean first) {
        return mainIndex.findFirstOrLast(session, first);
    }

    @Override
    public int getColumnIndex(Column col) {
        if (col.getColumnId() == mainIndex.getMainIndexColumn()) {
            return 0;
        }
        return -1;
    }

    @Override
    public boolean isFirstColumn(Column column) {
        return getColumnIndex(column) == 0;
    }

    @Override
    public double getCost(Session session, int[] masks,
            TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {
        return 10 * getCostRangeIndex(masks, mainIndex.getRowCountApproximation(),
                filters, filter, sortOrder, true, allColumnsSet);
    }

    @Override
    public boolean needRebuild() {
        return false;
    }

    @Override
    public void remove(Session session, Row row) {
        // nothing to do
    }

    @Override
    public void update(Session session, Row oldRow, Row newRow) {
        // nothing to do
    }

    @Override
    public void remove(Session session) {
        mainIndex.setMainIndexColumn(SearchRow.ROWID_INDEX);
    }

    @Override
    public void truncate(Session session) {
        // nothing to do
    }

    @Override
    public void checkRename() {
        // ok
    }

    @Override
    public long getRowCount(Session session) {
        return mainIndex.getRowCount(session);
    }

    @Override
    public long getRowCountApproximation() {
        return mainIndex.getRowCountApproximation();
    }

    @Override
    public long getDiskSpaceUsed() {
        return 0;
    }

}
