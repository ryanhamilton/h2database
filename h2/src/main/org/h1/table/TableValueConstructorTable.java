/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.table;

import java.util.ArrayList;

import org.h1.command.dml.TableValueConstructor;
import org.h1.engine.Session;
import org.h1.expression.Expression;
import org.h1.result.ResultInterface;
import org.h1.result.SimpleResult;
import org.h1.schema.Schema;

/**
 * A table for table value constructor.
 */
public class TableValueConstructorTable extends VirtualConstructedTable {

    private final ArrayList<ArrayList<Expression>> rows;

    public TableValueConstructorTable(Schema schema, Session session, Column[] columns,
            ArrayList<ArrayList<Expression>> rows) {
        super(schema, 0, "VALUES");
        setColumns(columns);
        this.rows = rows;
    }

    @Override
    public boolean canGetRowCount() {
        return true;
    }

    @Override
    public long getRowCount(Session session) {
        return rows.size();
    }

    @Override
    public long getRowCountApproximation() {
        return rows.size();
    }

    @Override
    public ResultInterface getResult(Session session) {
        SimpleResult simple = new SimpleResult();
        int columnCount = columns.length;
        for (int i = 0; i < columnCount; i++) {
            Column column = columns[i];
            String name = column.getName();
            simple.addColumn(name, name, column.getType());
        }
        TableValueConstructor.getVisibleResult(session, simple, columns, rows);
        return simple;
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, boolean alwaysQuote) {
        builder.append('(');
        TableValueConstructor.getValuesSQL(builder, alwaysQuote, rows);
        return builder.append(')');
    }

    @Override
    public boolean isDeterministic() {
        return true;
    }

}
