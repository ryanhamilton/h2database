/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression;

import java.util.ArrayList;
import java.util.Arrays;

import org.h1.api.ErrorCode;
import org.h1.command.dml.Query;
import org.h1.engine.Session;
import org.h1.message.DbException;
import org.h1.result.ResultInterface;
import org.h1.table.ColumnResolver;
import org.h1.table.TableFilter;
import org.h1.value.TypeInfo;
import org.h1.value.Value;
import org.h1.value.ValueNull;
import org.h1.value.ValueRow;

/**
 * A query returning a single value.
 * Subqueries are used inside other statements.
 */
public class Subquery extends Expression {

    private final Query query;
    private Expression expression;

    public Subquery(Query query) {
        this.query = query;
    }

    @Override
    public Value getValue(Session session) {
        query.setSession(session);
        try (ResultInterface result = query.query(2)) {
            Value v;
            if (!result.next()) {
                v = ValueNull.INSTANCE;
            } else {
                v = readRow(result);
                if (result.hasNext()) {
                    throw DbException.get(ErrorCode.SCALAR_SUBQUERY_CONTAINS_MORE_THAN_ONE_ROW);
                }
            }
            return v;
        }
    }

    /**
     * Evaluates and returns all rows of the subquery.
     *
     * @param session
     *            the session
     * @return values in all rows
     */
    public ArrayList<Value> getAllRows(Session session) {
        ArrayList<Value> list = new ArrayList<>();
        query.setSession(session);
        try (ResultInterface result = query.query(Integer.MAX_VALUE)) {
            while (result.next()) {
                list.add(readRow(result));
            }
        }
        return list;
    }

    private static Value readRow(ResultInterface result) {
        Value[] values = result.currentRow();
        int visible = result.getVisibleColumnCount();
        return visible == 1 ? values[0]
                : ValueRow.get(visible == values.length ? values : Arrays.copyOf(values, visible));
    }

    @Override
    public TypeInfo getType() {
        return getExpression().getType();
    }

    @Override
    public void mapColumns(ColumnResolver resolver, int level, int state) {
        query.mapColumns(resolver, level + 1);
    }

    @Override
    public Expression optimize(Session session) {
        session.optimizeQueryExpression(query);
        return this;
    }

    @Override
    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        query.setEvaluatable(tableFilter, b);
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, boolean alwaysQuote) {
        return builder.append('(').append(query.getPlanSQL(alwaysQuote)).append(')');
    }

    @Override
    public void updateAggregate(Session session, int stage) {
        query.updateAggregate(session, stage);
    }

    private Expression getExpression() {
        if (expression == null) {
            ArrayList<Expression> expressions = query.getExpressions();
            int columnCount = query.getColumnCount();
            if (columnCount == 1) {
                expression = expressions.get(0);
            } else {
                Expression[] list = new Expression[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    list[i] = expressions.get(i);
                }
                expression = new ExpressionList(list, false);
            }
        }
        return expression;
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        return query.isEverything(visitor);
    }

    public Query getQuery() {
        return query;
    }

    @Override
    public int getCost() {
        return query.getCostAsExpression();
    }

    @Override
    public Expression[] getExpressionColumns(Session session) {
        return getExpression().getExpressionColumns(session);
    }
}
