/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.ddl;

import java.util.ArrayList;

import org.h1.command.CommandInterface;
import org.h1.command.Prepared;
import org.h1.engine.Database;
import org.h1.engine.Right;
import org.h1.engine.Session;
import org.h1.expression.Parameter;
import org.h1.result.ResultInterface;
import org.h1.table.Column;
import org.h1.table.Table;
import org.h1.table.TableType;
import org.h1.value.DataType;
import org.h1.value.Value;
import org.h1.value.ValueInt;
import org.h1.value.ValueNull;

/**
 * This class represents the statements
 * ANALYZE and ANALYZE TABLE
 */
public class Analyze extends DefineCommand {

    /**
     * The sample size.
     */
    private int sampleRows;
    /**
     * used in ANALYZE TABLE...
     */
    private Table table;

    public Analyze(Session session) {
        super(session);
        sampleRows = session.getDatabase().getSettings().analyzeSample;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    @Override
    public int update() {
        session.commit(true);
        session.getUser().checkAdmin();
        Database db = session.getDatabase();
        if (table != null) {
            analyzeTable(session, table, sampleRows, true);
        } else {
            for (Table table : db.getAllTablesAndViews(false)) {
                analyzeTable(session, table, sampleRows, true);
            }
        }
        return 0;
    }

    /**
     * Analyze this table.
     *
     * @param session the session
     * @param table the table
     * @param sample the number of sample rows
     * @param manual whether the command was called by the user
     */
    public static void analyzeTable(Session session, Table table, int sample,
                                    boolean manual) {
        if (table.getTableType() != TableType.TABLE ||
                table.isHidden() || session == null) {
            return;
        }
        if (!manual) {
            if (session.getDatabase().isSysTableLocked()) {
                return;
            }
            if (table.hasSelectTrigger()) {
                return;
            }
        }
        if (table.isTemporary() && !table.isGlobalTemporary()
                && session.findLocalTempTable(table.getName()) == null) {
            return;
        }
        if (table.isLockedExclusively() && !table.isLockedExclusivelyBy(session)) {
            return;
        }
        if (!session.getUser().hasRight(table, Right.SELECT)) {
            return;
        }
        if (session.getCancel() != 0) {
            // if the connection is closed and there is something to undo
            return;
        }
        Column[] columns = table.getColumns();
        if (columns.length == 0) {
            return;
        }
        Database db = session.getDatabase();
        StringBuilder buff = new StringBuilder("SELECT ");
        for (int i = 0, l = columns.length; i < l; i++) {
            if (i > 0) {
                buff.append(", ");
            }
            Column col = columns[i];
            if (DataType.isLargeObject(col.getType().getValueType())) {
                // can not index LOB columns, so calculating
                // the selectivity is not required
                buff.append("MAX(NULL)");
            } else {
                buff.append("SELECTIVITY(");
                col.getSQL(buff, true).append(')');
            }
        }
        buff.append(" FROM ");
        table.getSQL(buff, true);
        if (sample > 0) {
            buff.append(" FETCH FIRST ROW ONLY SAMPLE_SIZE ? ");
        }
        String sql = buff.toString();
        Prepared command = session.prepare(sql);
        if (sample > 0) {
            ArrayList<Parameter> params = command.getParameters();
            params.get(0).setValue(ValueInt.get(sample));
        }
        ResultInterface result = command.query(0);
        result.next();
        for (int j = 0; j < columns.length; j++) {
            Value v = result.currentRow()[j];
            if (v != ValueNull.INSTANCE) {
                int selectivity = v.getInt();
                columns[j].setSelectivity(selectivity);
            }
        }
        db.updateMeta(session, table);
    }

    public void setTop(int top) {
        this.sampleRows = top;
    }

    @Override
    public int getType() {
        return CommandInterface.ANALYZE;
    }

}
