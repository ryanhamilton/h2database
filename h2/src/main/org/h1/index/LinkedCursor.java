/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.index;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.h1.engine.Session;
import org.h1.message.DbException;
import org.h1.result.Row;
import org.h1.result.SearchRow;
import org.h1.table.Column;
import org.h1.table.TableLink;
import org.h1.value.DataType;
import org.h1.value.Value;

/**
 * The cursor implementation for the linked index.
 */
public class LinkedCursor implements Cursor {

    private final TableLink tableLink;
    private final PreparedStatement prep;
    private final String sql;
    private final Session session;
    private final ResultSet rs;
    private Row current;

    LinkedCursor(TableLink tableLink, ResultSet rs, Session session,
            String sql, PreparedStatement prep) {
        this.session = session;
        this.tableLink = tableLink;
        this.rs = rs;
        this.sql = sql;
        this.prep = prep;
    }

    @Override
    public Row get() {
        return current;
    }

    @Override
    public SearchRow getSearchRow() {
        return current;
    }

    @Override
    public boolean next() {
        try {
            boolean result = rs.next();
            if (!result) {
                rs.close();
                tableLink.reusePreparedStatement(prep, sql);
                current = null;
                return false;
            }
        } catch (SQLException e) {
            throw DbException.convert(e);
        }
        current = tableLink.getTemplateRow();
        for (int i = 0; i < current.getColumnCount(); i++) {
            Column col = tableLink.getColumn(i);
            Value v = DataType.readValue(session, rs, i + 1, col.getType().getValueType());
            current.setValue(i, v);
        }
        return true;
    }

    @Override
    public boolean previous() {
        throw DbException.throwInternalError(toString());
    }

}
