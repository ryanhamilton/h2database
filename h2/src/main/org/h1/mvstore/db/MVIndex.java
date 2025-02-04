/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.mvstore.db;

import java.util.List;

import org.h1.index.Index;
import org.h1.mvstore.MVMap;
import org.h1.result.Row;
import org.h1.value.VersionedValue;

/**
 * An index that stores the data in an MVStore.
 */
public interface MVIndex extends Index {

    /**
     * Add the rows to a temporary storage (not to the index yet). The rows are
     * sorted by the index columns. This is to more quickly build the index.
     *
     * @param rows the rows
     * @param bufferName the name of the temporary storage
     */
    void addRowsToBuffer(List<Row> rows, String bufferName);

    /**
     * Add all the index data from the buffers to the index. The index will
     * typically use merge sort to add the data more quickly in sorted order.
     *
     * @param bufferNames the names of the temporary storage
     */
    void addBufferedRows(List<String> bufferNames);

    MVMap<?, VersionedValue> getMVMap();
}
