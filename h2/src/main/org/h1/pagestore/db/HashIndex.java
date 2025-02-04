/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.pagestore.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.h1.command.dml.AllColumnsForPlan;
import org.h1.engine.Session;
import org.h1.engine.Mode.UniqueIndexNullsHandling;
import org.h1.index.BaseIndex;
import org.h1.index.Cursor;
import org.h1.index.IndexCondition;
import org.h1.index.IndexType;
import org.h1.index.SingleRowCursor;
import org.h1.message.DbException;
import org.h1.result.Row;
import org.h1.result.SearchRow;
import org.h1.result.SortOrder;
import org.h1.table.Column;
import org.h1.table.IndexColumn;
import org.h1.table.TableFilter;
import org.h1.value.DataType;
import org.h1.value.Value;
import org.h1.value.ValueNull;

/**
 * A unique index based on an in-memory hash map.
 */
public class HashIndex extends BaseIndex {

    /**
     * The index of the indexed column.
     */
    private final int indexColumn;
    private final boolean totalOrdering;
    private final PageStoreTable tableData;
    private Map<Value, Long> rows;
    private final ArrayList<Long> nullRows = new ArrayList<>();

    public HashIndex(PageStoreTable table, int id, String indexName, IndexColumn[] columns, IndexType indexType) {
        super(table, id, indexName, columns, indexType);
        Column column = columns[0].column;
        indexColumn = column.getColumnId();
        totalOrdering = DataType.hasTotalOrdering(column.getType().getValueType());
        this.tableData = table;
        reset();
    }

    private void reset() {
        rows = totalOrdering ? new HashMap<Value, Long>() : new TreeMap<Value, Long>(database.getCompareMode());
    }

    @Override
    public void truncate(Session session) {
        reset();
    }

    @Override
    public void add(Session session, Row row) {
        Value key = row.getValue(indexColumn);
        if (key != ValueNull.INSTANCE
                || database.getMode().uniqueIndexNullsHandling == UniqueIndexNullsHandling.FORBID_ANY_DUPLICATES) {
            Object old = rows.get(key);
            if (old != null) {
                // TODO index duplicate key for hash indexes: is this allowed?
                throw getDuplicateKeyException(key.toString());
            }
            rows.put(key, row.getKey());
        } else {
            nullRows.add(row.getKey());
        }
    }

    @Override
    public void remove(Session session, Row row) {
        Value key = row.getValue(indexColumn);
        if (key != ValueNull.INSTANCE
                || database.getMode().uniqueIndexNullsHandling == UniqueIndexNullsHandling.FORBID_ANY_DUPLICATES) {
            rows.remove(key);
        } else {
            nullRows.remove(row.getKey());
        }
    }

    @Override
    public Cursor find(Session session, SearchRow first, SearchRow last) {
        if (first == null || last == null) {
            // TODO hash index: should additionally check if values are the same
            throw DbException.throwInternalError(first + " " + last);
        }
        Value v = first.getValue(indexColumn);
        if (v == ValueNull.INSTANCE
                && database.getMode().uniqueIndexNullsHandling != UniqueIndexNullsHandling.FORBID_ANY_DUPLICATES) {
            return new NonUniqueHashCursor(session, tableData, nullRows);
        }
        /*
         * Sometimes the incoming search is a similar, but not the same type
         * e.g. the search value is INT, but the index column is LONG. In which
         * case we need to convert, otherwise the HashMap will not find the
         * result.
         */
        v = v.convertTo(tableData.getColumn(indexColumn).getType(), database, true, null);
        Row result;
        Long pos = rows.get(v);
        if (pos == null) {
            result = null;
        } else {
            result = tableData.getRow(session, pos.intValue());
        }
        return new SingleRowCursor(result);
    }

    @Override
    public long getRowCount(Session session) {
        return getRowCountApproximation();
    }

    @Override
    public long getRowCountApproximation() {
        return rows.size() + nullRows.size();
    }

    @Override
    public long getDiskSpaceUsed() {
        return 0;
    }

    @Override
    public void close(Session session) {
        // nothing to do
    }

    @Override
    public void remove(Session session) {
        // nothing to do
    }

    @Override
    public double getCost(Session session, int[] masks,
            TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {
        for (Column column : columns) {
            int index = column.getColumnId();
            int mask = masks[index];
            if ((mask & IndexCondition.EQUALITY) != IndexCondition.EQUALITY) {
                return Long.MAX_VALUE;
            }
        }
        return 2;
    }

    @Override
    public void checkRename() {
        // ok
    }

    @Override
    public boolean needRebuild() {
        return true;
    }

    @Override
    public boolean canGetFirstOrLast() {
        return false;
    }

    @Override
    public Cursor findFirstOrLast(Session session, boolean first) {
        throw DbException.getUnsupportedException("HASH");
    }

    @Override
    public boolean canScan() {
        return false;
    }

}
