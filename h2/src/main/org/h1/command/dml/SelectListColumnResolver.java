/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.dml;

import java.util.ArrayList;

import org.h1.engine.Database;
import org.h1.expression.Expression;
import org.h1.expression.ExpressionColumn;
import org.h1.table.Column;
import org.h1.table.ColumnResolver;
import org.h1.table.TableFilter;
import org.h1.util.ColumnNamer;
import org.h1.value.Value;

/**
 * This class represents a column resolver for the column list of a SELECT
 * statement. It is used to resolve select column aliases in the HAVING clause.
 * Example:
 * <p>
 * SELECT X/3 AS A, COUNT(*) FROM SYSTEM_RANGE(1, 10) GROUP BY A HAVING A>2;
 * </p>
 *
 * @author Thomas Mueller
 */
public class SelectListColumnResolver implements ColumnResolver {

    private final Select select;
    private final Expression[] expressions;
    private final Column[] columns;

    SelectListColumnResolver(Select select) {
        this.select = select;
        int columnCount = select.getColumnCount();
        columns = new Column[columnCount];
        expressions = new Expression[columnCount];
        ArrayList<Expression> columnList = select.getExpressions();
        ColumnNamer columnNamer= new ColumnNamer(select.getSession());
        for (int i = 0; i < columnCount; i++) {
            Expression expr = columnList.get(i);
            String columnName = columnNamer.getColumnName(expr, i, expr.getAlias());
            Column column = new Column(columnName, Value.NULL);
            column.setTable(null, i);
            columns[i] = column;
            expressions[i] = expr.getNonAliasExpression();
        }
    }

    @Override
    public Column[] getColumns() {
        return columns;
    }

    @Override
    public Column findColumn(String name) {
        Database db = select.getSession().getDatabase();
        for (Column column : columns) {
            if (db.equalsIdentifiers(column.getName(), name)) {
                return column;
            }
        }
        return null;
    }

    @Override
    public String getColumnName(Column column) {
        return column.getName();
    }

    @Override
    public boolean hasDerivedColumnList() {
        return false;
    }

    @Override
    public String getSchemaName() {
        return null;
    }

    @Override
    public Select getSelect() {
        return select;
    }

    @Override
    public Column[] getSystemColumns() {
        return null;
    }

    @Override
    public Column getRowIdColumn() {
        return null;
    }

    @Override
    public String getTableAlias() {
        return null;
    }

    @Override
    public TableFilter getTableFilter() {
        return null;
    }

    @Override
    public Value getValue(Column column) {
        return null;
    }

    @Override
    public Expression optimize(ExpressionColumn expressionColumn, Column column) {
        return expressions[column.getColumnId()];
    }

}
