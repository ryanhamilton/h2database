/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.dml;

import org.h1.command.CommandInterface;
import org.h1.command.Prepared;
import org.h1.engine.Session;
import org.h1.expression.Expression;
import org.h1.expression.ExpressionVisitor;
import org.h1.result.LocalResult;
import org.h1.result.ResultInterface;
import org.h1.value.Value;

/**
 * This class represents the statement
 * CALL.
 */
public class Call extends Prepared {

    private boolean isResultSet;
    private Expression expression;
    private Expression[] expressions;

    public Call(Session session) {
        super(session);
    }

    @Override
    public ResultInterface queryMeta() {
        LocalResult result;
        if (isResultSet) {
            Expression[] expr = expression.getExpressionColumns(session);
            int count = expr.length;
            result = session.getDatabase().getResultFactory().create(session, expr, count, count);
        } else {
            result = session.getDatabase().getResultFactory().create(session, expressions, 1, 1);
        }
        result.done();
        return result;
    }

    @Override
    public int update() {
        Value v = expression.getValue(session);
        int type = v.getValueType();
        switch (type) {
        case Value.RESULT_SET:
            // this will throw an exception
            // methods returning a result set may not be called like this.
            return super.update();
        case Value.UNKNOWN:
        case Value.NULL:
            return 0;
        default:
            return v.getInt();
        }
    }

    @Override
    public ResultInterface query(int maxrows) {
        setCurrentRowNumber(1);
        Value v = expression.getValue(session);
        if (isResultSet) {
            return v.getResult();
        }
        LocalResult result = session.getDatabase().getResultFactory().create(session, expressions, 1, 1);
        result.addRow(v);
        result.done();
        return result;
    }

    @Override
    public void prepare() {
        expression = expression.optimize(session);
        expressions = new Expression[] { expression };
        isResultSet = expression.getType().getValueType() == Value.RESULT_SET;
        if (isResultSet) {
            prepareAlways = true;
        }
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    @Override
    public boolean isQuery() {
        return true;
    }

    @Override
    public boolean isTransactional() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return expression.isEverything(ExpressionVisitor.READONLY_VISITOR);

    }

    @Override
    public int getType() {
        return CommandInterface.CALL;
    }

    @Override
    public boolean isCacheable() {
        return !isResultSet;
    }

}
