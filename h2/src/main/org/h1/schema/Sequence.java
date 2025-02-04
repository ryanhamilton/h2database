/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.schema;

import java.math.BigDecimal;

import org.h1.api.ErrorCode;
import org.h1.command.ddl.SequenceOptions;
import org.h1.engine.DbObject;
import org.h1.engine.Session;
import org.h1.message.DbException;
import org.h1.message.Trace;
import org.h1.table.Table;
import org.h1.value.Value;
import org.h1.value.ValueDecimal;
import org.h1.value.ValueLong;

/**
 * A sequence is created using the statement
 * CREATE SEQUENCE
 */
public class Sequence extends SchemaObjectBase {

    /**
     * The default cache size for sequences.
     */
    public static final int DEFAULT_CACHE_SIZE = 32;

    private long value;
    private long valueWithMargin;
    private long increment;
    private long cacheSize;
    private long minValue;
    private long maxValue;
    private boolean cycle;
    private boolean belongsToTable;
    private boolean writeWithMargin;

    /**
     * Creates a new sequence.
     *
     * @param session the session
     * @param schema the schema
     * @param id the object id
     * @param name the sequence name
     * @param options the sequence options
     * @param belongsToTable whether this sequence belongs to a table (for
     *            auto-increment columns)
     */
    public Sequence(Session session, Schema schema, int id, String name, SequenceOptions options,
            boolean belongsToTable) {
        super(schema, id, name, Trace.SEQUENCE);
        Long t = options.getIncrement(session);
        long increment = t != null ? t : 1;
        Long start = options.getStartValue(session);
        Long min = options.getMinValue(null, session);
        Long max = options.getMaxValue(null, session);
        long minValue = min != null ? min : getDefaultMinValue(start, increment);
        long maxValue = max != null ? max : getDefaultMaxValue(start, increment);
        long value = start != null ? start : increment >= 0 ? minValue : maxValue;
        if (!isValid(value, minValue, maxValue, increment)) {
            throw DbException.get(ErrorCode.SEQUENCE_ATTRIBUTES_INVALID, name, Long.toString(value),
                    Long.toString(minValue), Long.toString(maxValue), Long.toString(increment));
        }
        this.valueWithMargin = this.value = value;
        this.increment = increment;
        t = options.getCacheSize(session);
        this.cacheSize = t != null ? Math.max(1, t) : DEFAULT_CACHE_SIZE;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.cycle = Boolean.TRUE.equals(options.getCycle());
        this.belongsToTable = belongsToTable;
    }

    /**
     * Allows the start value, increment, min value and max value to be updated
     * atomically, including atomic validation. Useful because setting these
     * attributes one after the other could otherwise result in an invalid
     * sequence state (e.g. min value > max value, start value < min value,
     * etc).
     *
     * @param startValue the new start value (<code>null</code> if no change)
     * @param minValue the new min value (<code>null</code> if no change)
     * @param maxValue the new max value (<code>null</code> if no change)
     * @param increment the new increment (<code>null</code> if no change)
     */
    public synchronized void modify(Long startValue, Long minValue,
            Long maxValue, Long increment) {
        if (startValue == null) {
            startValue = this.value;
        }
        if (minValue == null) {
            minValue = this.minValue;
        }
        if (maxValue == null) {
            maxValue = this.maxValue;
        }
        if (increment == null) {
            increment = this.increment;
        }
        if (!isValid(startValue, minValue, maxValue, increment)) {
            throw DbException.get(ErrorCode.SEQUENCE_ATTRIBUTES_INVALID,
                    getName(), String.valueOf(startValue),
                    String.valueOf(minValue),
                    String.valueOf(maxValue),
                    String.valueOf(increment));
        }
        this.value = startValue;
        this.valueWithMargin = startValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
    }

    /**
     * Validates the specified prospective start value, min value, max value and
     * increment relative to each other, since each of their respective
     * validities are contingent on the values of the other parameters.
     *
     * @param value the prospective start value
     * @param minValue the prospective min value
     * @param maxValue the prospective max value
     * @param increment the prospective increment
     */
    private static boolean isValid(long value, long minValue, long maxValue, long increment) {
        return minValue <= value &&
            maxValue >= value &&
            maxValue > minValue &&
            increment != 0 &&
            // Math.abs(increment) <= maxValue - minValue
            // Can use Long.compareUnsigned() on Java 8
            Math.abs(increment) + Long.MIN_VALUE <= maxValue - minValue + Long.MIN_VALUE;
    }

    /**
     * Calculates default min value.
     *
     * @param startValue the start value of the sequence.
     * @param increment the increment of the sequence value.
     * @return min value.
     */
    public static long getDefaultMinValue(Long startValue, long increment) {
        long v = increment >= 0 ? 1 : Long.MIN_VALUE;
        if (startValue != null && increment >= 0 && startValue < v) {
            v = startValue;
        }
        return v;
    }

    /**
     * Calculates default max value.
     *
     * @param startValue the start value of the sequence.
     * @param increment the increment of the sequence value.
     * @return min value.
     */
    public static long getDefaultMaxValue(Long startValue, long increment) {
        long v = increment >= 0 ? Long.MAX_VALUE : -1;
        if (startValue != null && increment < 0 && startValue > v) {
            v = startValue;
        }
        return v;
    }

    public boolean getBelongsToTable() {
        return belongsToTable;
    }

    public long getIncrement() {
        return increment;
    }

    public long getMinValue() {
        return minValue;
    }

    public long getMaxValue() {
        return maxValue;
    }

    public boolean getCycle() {
        return cycle;
    }

    public void setCycle(boolean cycle) {
        this.cycle = cycle;
    }

    @Override
    public String getDropSQL() {
        if (getBelongsToTable()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("DROP SEQUENCE IF EXISTS ");
        return getSQL(builder, true).toString();
    }

    @Override
    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw DbException.throwInternalError(toString());
    }

    @Override
    public synchronized String getCreateSQL() {
        long v = writeWithMargin ? valueWithMargin : value;
        StringBuilder buff = new StringBuilder("CREATE SEQUENCE ");
        getSQL(buff, true).append(" START WITH ").append(v);
        if (increment != 1) {
            buff.append(" INCREMENT BY ").append(increment);
        }
        if (minValue != getDefaultMinValue(v, increment)) {
            buff.append(" MINVALUE ").append(minValue);
        }
        if (maxValue != getDefaultMaxValue(v, increment)) {
            buff.append(" MAXVALUE ").append(maxValue);
        }
        if (cycle) {
            buff.append(" CYCLE");
        }
        if (cacheSize != DEFAULT_CACHE_SIZE) {
            if (cacheSize == 1) {
                buff.append(" NO CACHE");
            } else {
                buff.append(" CACHE ").append(cacheSize);
            }
        }
        if (belongsToTable) {
            buff.append(" BELONGS_TO_TABLE");
        }
        return buff.toString();
    }

    /**
     * Get the next value for this sequence.
     *
     * @param session the session
     * @return the next value
     */
    public Value getNext(Session session) {
        boolean needsFlush = false;
        long resultAsLong;
        synchronized (this) {
            if ((increment > 0 && value >= valueWithMargin) ||
                    (increment < 0 && value <= valueWithMargin)) {
                valueWithMargin += increment * cacheSize;
                needsFlush = true;
            }
            if ((increment > 0 && value > maxValue) ||
                    (increment < 0 && value < minValue)) {
                if (cycle) {
                    value = increment > 0 ? minValue : maxValue;
                    valueWithMargin = value + (increment * cacheSize);
                    needsFlush = true;
                } else {
                    throw DbException.get(ErrorCode.SEQUENCE_EXHAUSTED, getName());
                }
            }
            resultAsLong = value;
            value += increment;
        }
        if (needsFlush) {
            flush(session);
        }
        Value result;
        if (database.getMode().decimalSequences) {
            result = ValueDecimal.get(BigDecimal.valueOf(resultAsLong));
        } else {
            result = ValueLong.get(resultAsLong);
        }
        if (session != null) {
            session.setCurrentValueFor(this, result);
        }
        return result;
    }

    /**
     * Flush the current value to disk.
     */
    public void flushWithoutMargin() {
        if (valueWithMargin != value) {
            valueWithMargin = value;
            flush(null);
        }
    }

    /**
     * Flush the current value, including the margin, to disk.
     *
     * @param session the session
     */
    public void flush(Session session) {
        if (isTemporary()) {
            return;
        }
        if (session == null || !database.isSysTableLockedBy(session)) {
            // This session may not lock the sys table (except if it has already
            // locked it) because it must be committed immediately, otherwise
            // other threads can not access the sys table.
            Session sysSession = database.getSystemSession();
            synchronized (database.isMVStore() ? sysSession : database) {
                flushInternal(sysSession);
                sysSession.commit(false);
            }
        } else {
            synchronized (database.isMVStore() ? session : database) {
                flushInternal(session);
            }
        }
    }

    private void flushInternal(Session session) {
        final boolean metaWasLocked = database.lockMeta(session);
        // just for this case, use the value with the margin
        try {
            writeWithMargin = true;
            database.updateMeta(session, this);
        } finally {
            writeWithMargin = false;
            if (!metaWasLocked) {
                database.unlockMeta(session);
            }
        }
    }

    /**
     * Flush the current value to disk and close this object.
     */
    public void close() {
        flushWithoutMargin();
    }

    @Override
    public int getType() {
        return DbObject.SEQUENCE;
    }

    @Override
    public void removeChildrenAndResources(Session session) {
        database.removeMeta(session, getId());
        invalidate();
    }

    @Override
    public void checkRename() {
        // nothing to do
    }

    public synchronized long getCurrentValue() {
        return value - increment;
    }

    public void setBelongsToTable(boolean b) {
        this.belongsToTable = b;
    }

    public void setCacheSize(long cacheSize) {
        this.cacheSize = Math.max(1, cacheSize);
    }

    public long getCacheSize() {
        return cacheSize;
    }

}
