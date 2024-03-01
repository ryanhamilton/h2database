/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.ddl;

import org.h1.api.ErrorCode;
import org.h1.command.CommandInterface;
import org.h1.engine.Right;
import org.h1.engine.Session;
import org.h1.message.DbException;
import org.h1.schema.Sequence;
import org.h1.table.Column;
import org.h1.table.Table;

/**
 * This class represents the statement
 * TRUNCATE TABLE
 */
public class TruncateTable extends DefineCommand {

    private Table table;

    private boolean restart;

    public TruncateTable(Session session) {
        super(session);
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public void setRestart(boolean restart) {
        this.restart = restart;
    }

    @Override
    public int update() {
        session.commit(true);
        if (!table.canTruncate()) {
            throw DbException.get(ErrorCode.CANNOT_TRUNCATE_1, table.getSQL(false));
        }
        session.getUser().checkRight(table, Right.DELETE);
        table.lock(session, true, true);
        table.truncate(session);
        if (restart) {
            for (Column column : table.getColumns()) {
                Sequence sequence = column.getSequence();
                if (sequence != null) {
                    long min = sequence.getMinValue();
                    if (min != sequence.getCurrentValue()) {
                        sequence.modify(min, null, null, null);
                        session.getDatabase().updateMeta(session, sequence);
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public int getType() {
        return CommandInterface.TRUNCATE_TABLE;
    }

}
