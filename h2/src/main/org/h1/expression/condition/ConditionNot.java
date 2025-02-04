/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression.condition;

import org.h1.engine.Session;
import org.h1.expression.Expression;
import org.h1.expression.ExpressionVisitor;
import org.h1.expression.TypedValueExpression;
import org.h1.expression.ValueExpression;
import org.h1.table.ColumnResolver;
import org.h1.table.TableFilter;
import org.h1.value.Value;
import org.h1.value.ValueNull;

/**
 * A NOT condition.
 */
public class ConditionNot extends Condition {

    private Expression condition;

    public ConditionNot(Expression condition) {
        this.condition = condition;
    }

    @Override
    public Expression getNotIfPossible(Session session) {
        return castToBoolean(session, condition);
    }

    @Override
    public Value getValue(Session session) {
        Value v = condition.getValue(session);
        if (v == ValueNull.INSTANCE) {
            return v;
        }
        return v.convertTo(Value.BOOLEAN).negate();
    }

    @Override
    public void mapColumns(ColumnResolver resolver, int level, int state) {
        condition.mapColumns(resolver, level, state);
    }

    @Override
    public Expression optimize(Session session) {
        Expression e2 = condition.getNotIfPossible(session);
        if (e2 != null) {
            return e2.optimize(session);
        }
        Expression expr = condition.optimize(session);
        if (expr.isConstant()) {
            Value v = expr.getValue(session);
            if (v == ValueNull.INSTANCE) {
                return TypedValueExpression.getUnknown();
            }
            return ValueExpression.getBoolean(!v.getBoolean());
        }
        condition = expr;
        return this;
    }

    @Override
    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        condition.setEvaluatable(tableFilter, b);
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, boolean alwaysQuote) {
        builder.append("(NOT ");
        return condition.getSQL(builder, alwaysQuote).append(')');
    }

    @Override
    public void updateAggregate(Session session, int stage) {
        condition.updateAggregate(session, stage);
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        return condition.isEverything(visitor);
    }

    @Override
    public int getCost() {
        return condition.getCost();
    }

    @Override
    public int getSubexpressionCount() {
        return 1;
    }

    @Override
    public Expression getSubexpression(int index) {
        if (index == 0) {
            return condition;
        }
        throw new IndexOutOfBoundsException();
    }

}
