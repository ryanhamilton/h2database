/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression.aggregate;

import org.h1.engine.Database;
import org.h1.value.Value;
import org.h1.value.ValueLong;
import org.h1.value.ValueNull;

/**
 * Data stored while calculating a COUNT aggregate.
 */
class AggregateDataCount extends AggregateData {

    private final boolean all;

    private long count;

    AggregateDataCount(boolean all) {
        this.all = all;
    }

    @Override
    void add(Database database, Value v) {
        if (all || v != ValueNull.INSTANCE) {
            count++;
        }
    }

    @Override
    Value getValue(Database database, int dataType) {
        return ValueLong.get(count).convertTo(dataType);
    }

}
