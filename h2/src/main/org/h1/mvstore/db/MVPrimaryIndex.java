/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.mvstore.db;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.h1.api.ErrorCode;
import org.h1.command.dml.AllColumnsForPlan;
import org.h1.engine.Database;
import org.h1.engine.Session;
import org.h1.index.BaseIndex;
import org.h1.index.Cursor;
import org.h1.index.IndexType;
import org.h1.message.DbException;
import org.h1.mvstore.DataUtils;
import org.h1.mvstore.MVMap;
import org.h1.mvstore.tx.Transaction;
import org.h1.mvstore.tx.TransactionMap;
import org.h1.result.Row;
import org.h1.result.SearchRow;
import org.h1.result.SortOrder;
import org.h1.table.Column;
import org.h1.table.IndexColumn;
import org.h1.table.TableFilter;
import org.h1.value.Value;
import org.h1.value.ValueArray;
import org.h1.value.ValueLong;
import org.h1.value.ValueNull;
import org.h1.value.VersionedValue;

/**
 * A table stored in a MVStore.
 */
public class MVPrimaryIndex extends BaseIndex implements MVIndex {

    private final MVTable mvTable;
    private final String mapName;
    private final TransactionMap<Value, Value> dataMap;
    private final AtomicLong lastKey = new AtomicLong();
    private int mainIndexColumn = SearchRow.ROWID_INDEX;

    public MVPrimaryIndex(Database db, MVTable table, int id,
            IndexColumn[] columns, IndexType indexType) {
        super(table, id, table.getName() + "_DATA", columns, indexType);
        this.mvTable = table;
        int[] sortTypes = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            sortTypes[i] = SortOrder.ASCENDING;
        }
        ValueDataType keyType = new ValueDataType();
        ValueDataType valueType = new ValueDataType(db, sortTypes);
        mapName = "table." + getId();
        assert db.isStarting() || !db.getStore().getMvStore().getMetaMap().containsKey(DataUtils.META_NAME + mapName);
        Transaction t = mvTable.getTransactionBegin();
        dataMap = t.openMap(mapName, keyType, valueType);
        dataMap.map.setVolatile(!table.isPersistData() || !indexType.isPersistent());
        t.commit();
        Value k = dataMap.map.lastKey();    // include uncommitted keys as well
        lastKey.set(k == null ? 0 : k.getLong());
    }

    @Override
    public String getCreateSQL() {
        return null;
    }

    @Override
    public String getPlanSQL() {
        return table.getSQL(new StringBuilder(), false).append(".tableScan").toString();
    }

    public void setMainIndexColumn(int mainIndexColumn) {
        this.mainIndexColumn = mainIndexColumn;
    }

    public int getMainIndexColumn() {
        return mainIndexColumn;
    }

    @Override
    public void close(Session session) {
        // ok
    }

    @Override
    public void add(Session session, Row row) {
        if (mainIndexColumn == SearchRow.ROWID_INDEX) {
            if (row.getKey() == 0) {
                row.setKey(lastKey.incrementAndGet());
            }
        } else {
            long c = row.getValue(mainIndexColumn).getLong();
            row.setKey(c);
        }

        if (mvTable.getContainsLargeObject()) {
            for (int i = 0, len = row.getColumnCount(); i < len; i++) {
                Value v = row.getValue(i);
                Value v2 = v.copy(database, getId());
                if (v2.isLinkedToTable()) {
                    session.removeAtCommitStop(v2);
                }
                if (v != v2) {
                    row.setValue(i, v2);
                }
            }
        }

        TransactionMap<Value, Value> map = getMap(session);
        long rowKey = row.getKey();
        Value key = ValueLong.get(rowKey);
        try {
            Value oldValue = map.putIfAbsent(key, ValueArray.get(row.getValueList()));
            if (oldValue != null) {
                int errorCode = ErrorCode.CONCURRENT_UPDATE_1;
                if (map.getImmediate(key) != null) {
                    // committed
                    errorCode = ErrorCode.DUPLICATE_KEY_1;
                }
                DbException e = DbException.get(errorCode,
                        getDuplicatePrimaryKeyMessage(mainIndexColumn).append(' ').append(oldValue).toString());
                e.setSource(this);
                throw e;
            }
        } catch (IllegalStateException e) {
            throw mvTable.convertException(e);
        }
        // because it's possible to directly update the key using the _rowid_
        // syntax
        long last;
        while (rowKey > (last = lastKey.get())) {
            if(lastKey.compareAndSet(last, rowKey)) break;
        }
    }

    @Override
    public void remove(Session session, Row row) {
        if (mvTable.getContainsLargeObject()) {
            for (int i = 0, len = row.getColumnCount(); i < len; i++) {
                Value v = row.getValue(i);
                if (v.isLinkedToTable()) {
                    session.removeAtCommit(v);
                }
            }
        }
        TransactionMap<Value, Value> map = getMap(session);
        try {
            Value old = map.remove(ValueLong.get(row.getKey()));
            if (old == null) {
                StringBuilder builder = new StringBuilder();
                getSQL(builder, false).append(": ").append(row.getKey());
                throw DbException.get(ErrorCode.ROW_NOT_FOUND_WHEN_DELETING_1, builder.toString());
            }
        } catch (IllegalStateException e) {
            throw mvTable.convertException(e);
        }
    }

    @Override
    public void update(Session session, Row oldRow, Row newRow) {
        if (mainIndexColumn != SearchRow.ROWID_INDEX) {
            long c = newRow.getValue(mainIndexColumn).getLong();
            newRow.setKey(c);
        }
        long key = oldRow.getKey();
        assert mainIndexColumn != SearchRow.ROWID_INDEX || key != 0;
        assert key == newRow.getKey() : key + " != " + newRow.getKey();
        if (mvTable.getContainsLargeObject()) {
            for (int i = 0, len = oldRow.getColumnCount(); i < len; i++) {
                Value oldValue = oldRow.getValue(i);
                Value newValue = newRow.getValue(i);
                if(oldValue != newValue) {
                    if (oldValue.isLinkedToTable()) {
                        session.removeAtCommit(oldValue);
                    }
                    Value v2 = newValue.copy(database, getId());
                    if (v2.isLinkedToTable()) {
                        session.removeAtCommitStop(v2);
                    }
                    if (newValue != v2) {
                        newRow.setValue(i, v2);
                    }
                }
            }
        }

        TransactionMap<Value,Value> map = getMap(session);
        try {
            Value existing = map.put(ValueLong.get(key), ValueArray.get(newRow.getValueList()));
            if (existing == null) {
                StringBuilder builder = new StringBuilder();
                getSQL(builder, false).append(": ").append(key);
                throw DbException.get(ErrorCode.ROW_NOT_FOUND_WHEN_DELETING_1, builder.toString());
            }
        } catch (IllegalStateException e) {
            throw mvTable.convertException(e);
        }


        // because it's possible to directly update the key using the _rowid_
        // syntax
        if (newRow.getKey() > lastKey.get()) {
            lastKey.set(newRow.getKey());
        }
    }

    /**
     * Lock a single row.
     *
     * @param session database session
     * @param row to lock
     * @return row object if it exists
     */
    Row lockRow(Session session, Row row) {
        TransactionMap<Value, Value> map = getMap(session);
        long key = row.getKey();
        ValueArray array = (ValueArray) lockRow(map, key);
        return array == null ? null : getRow(session, key, array);
    }

    private Value lockRow(TransactionMap<Value, Value> map, long key) {
        try {
            return map.lock(ValueLong.get(key));
        } catch (IllegalStateException ex) {
            throw mvTable.convertException(ex);
        }
    }

    @Override
    public Cursor find(Session session, SearchRow first, SearchRow last) {
        ValueLong min = first == null ? ValueLong.MIN : ValueLong.get(first.getKey());
        ValueLong max = last == null ? ValueLong.MAX : ValueLong.get(last.getKey());
        TransactionMap<Value, Value> map = getMap(session);
        return new MVStoreCursor(session, map.entryIterator(min, max));
    }

    @Override
    public MVTable getTable() {
        return mvTable;
    }

    @Override
    public Row getRow(Session session, long key) {
        TransactionMap<Value, Value> map = getMap(session);
        Value v = map.getFromSnapshot(ValueLong.get(key));
        if (v == null) {
            throw DbException.get(ErrorCode.ROW_NOT_FOUND_IN_PRIMARY_INDEX,
                    getSQL(false), String.valueOf(key));
        }
        return getRow(session, key, (ValueArray) v);
    }

    private static Row getRow(Session session, long key, ValueArray array) {
        Row row = session.createRow(array.getList(), 0);
        row.setKey(key);
        return row;
    }

    @Override
    public double getCost(Session session, int[] masks,
            TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {
        try {
            return 10 * getCostRangeIndex(masks, dataMap.sizeAsLongMax(),
                    filters, filter, sortOrder, true, allColumnsSet);
        } catch (IllegalStateException e) {
            throw DbException.get(ErrorCode.OBJECT_CLOSED, e);
        }
    }

    @Override
    public int getColumnIndex(Column col) {
        // can not use this index - use the delegate index instead
        return SearchRow.ROWID_INDEX;
    }

    @Override
    public boolean isFirstColumn(Column column) {
        return false;
    }

    @Override
    public void remove(Session session) {
        TransactionMap<Value, Value> map = getMap(session);
        if (!map.isClosed()) {
            Transaction t = session.getTransaction();
            t.removeMap(map);
        }
    }

    @Override
    public void truncate(Session session) {
        TransactionMap<Value, Value> map = getMap(session);
        if (mvTable.getContainsLargeObject()) {
            database.getLobStorage().removeAllForTable(table.getId());
        }
        map.clear();
    }

    @Override
    public boolean canGetFirstOrLast() {
        return true;
    }

    @Override
    public Cursor findFirstOrLast(Session session, boolean first) {
        TransactionMap<Value, Value> map = getMap(session);
        ValueLong v = (ValueLong) (first ? map.firstKey() : map.lastKey());
        if (v == null) {
            return new MVStoreCursor(session,
                    Collections.<Entry<Value, Value>> emptyIterator());
        }
        Value value = map.getFromSnapshot(v);
        Entry<Value, Value> e = new AbstractMap.SimpleImmutableEntry<Value, Value>(v, value);
        List<Entry<Value, Value>> list = Collections.singletonList(e);
        MVStoreCursor c = new MVStoreCursor(session, list.iterator());
        c.next();
        return c;
    }

    @Override
    public boolean needRebuild() {
        return false;
    }

    @Override
    public long getRowCount(Session session) {
        TransactionMap<Value, Value> map = getMap(session);
        return map.sizeAsLong();
    }

    /**
     * The maximum number of rows, including uncommitted rows of any session.
     *
     * @return the maximum number of rows
     */
    public long getRowCountMax() {
        return dataMap.sizeAsLongMax();
    }

    @Override
    public long getRowCountApproximation() {
        return getRowCountMax();
    }

    @Override
    public long getDiskSpaceUsed() {
        return dataMap.map.getRootPage().getDiskSpaceUsed();
    }

    public String getMapName() {
        return mapName;
    }

    @Override
    public void checkRename() {
        // ok
    }

    @Override
    public void addRowsToBuffer(List<Row> rows, String bufferName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBufferedRows(List<String> bufferNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the key from the row.
     *
     * @param row the row
     * @param ifEmpty the value to use if the row is empty
     * @param ifNull the value to use if the column is NULL
     * @return the key
     */
    ValueLong getKey(SearchRow row, ValueLong ifEmpty, ValueLong ifNull) {
        if (row == null) {
            return ifEmpty;
        }
        Value v = row.getValue(mainIndexColumn);
        if (v == null) {
            throw DbException.throwInternalError(row.toString());
        } else if (v == ValueNull.INSTANCE) {
            return ifNull;
        }
        return (ValueLong) v.convertTo(Value.LONG);
    }

    /**
     * Search for a specific row or a set of rows.
     *
     * @param session the session
     * @param first the key of the first row
     * @param last the key of the last row
     * @return the cursor
     */
    Cursor find(Session session, ValueLong first, ValueLong last) {
        TransactionMap<Value, Value> map = getMap(session);
        return new MVStoreCursor(session, map.entryIterator(first, last));
    }

    @Override
    public boolean isRowIdIndex() {
        return true;
    }

    /**
     * Get the map to store the data.
     *
     * @param session the session
     * @return the map
     */
    TransactionMap<Value, Value> getMap(Session session) {
        if (session == null) {
            return dataMap;
        }
        Transaction t = session.getTransaction();
        return dataMap.getInstance(t);
    }

    @Override
    public MVMap<Value, VersionedValue> getMVMap() {
        return dataMap.map;
    }

    /**
     * A cursor.
     */
    static class MVStoreCursor implements Cursor {

        private final Session session;
        private final Iterator<Entry<Value, Value>> it;
        private Entry<Value, Value> current;
        private Row row;

        public MVStoreCursor(Session session, Iterator<Entry<Value, Value>> it) {
            this.session = session;
            this.it = it;
        }

        @Override
        public Row get() {
            if (row == null) {
                if (current != null) {
                    ValueArray array = (ValueArray) current.getValue();
                    row = session.createRow(array.getList(), 0);
                    row.setKey(current.getKey().getLong());
                }
            }
            return row;
        }

        @Override
        public SearchRow getSearchRow() {
            return get();
        }

        @Override
        public boolean next() {
            current = it.hasNext() ? it.next() : null;
            row = null;
            return current != null;
        }

        @Override
        public boolean previous() {
            throw DbException.getUnsupportedException("previous");
        }

    }
}
