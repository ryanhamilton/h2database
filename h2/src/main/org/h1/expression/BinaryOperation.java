/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression;

import org.h1.engine.Database;
import org.h1.engine.Session;
import org.h1.expression.IntervalOperation.IntervalOpType;
import org.h1.expression.function.Function;
import org.h1.message.DbException;
import org.h1.table.ColumnResolver;
import org.h1.table.TableFilter;
import org.h1.value.DataType;
import org.h1.value.TypeInfo;
import org.h1.value.Value;
import org.h1.value.ValueInt;
import org.h1.value.ValueNull;
import org.h1.value.ValueString;

/**
 * A mathematical expression, or string concatenation.
 */
public class BinaryOperation extends Expression {

    public enum OpType {
        /**
         * This operation represents an addition as in 1 + 2.
         */
        PLUS,

        /**
         * This operation represents a subtraction as in 2 - 1.
         */
        MINUS,

        /**
         * This operation represents a multiplication as in 2 * 3.
         */
        MULTIPLY,

        /**
         * This operation represents a division as in 4 * 2.
         */
        DIVIDE,

        /**
         * This operation represents a modulus as in 5 % 2.
         */
        MODULUS
    }

    private OpType opType;
    private Expression left, right;
    private TypeInfo type;
    private boolean convertRight = true;

    public BinaryOperation(OpType opType, Expression left, Expression right) {
        this.opType = opType;
        this.left = left;
        this.right = right;
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, boolean alwaysQuote) {
        // don't remove the space, otherwise it might end up some thing like
        // --1 which is a line remark
        builder.append('(');
        left.getSQL(builder, alwaysQuote).append(' ').append(getOperationToken()).append(' ');
        return right.getSQL(builder, alwaysQuote).append(')');
    }

    private String getOperationToken() {
        switch (opType) {
        case PLUS:
            return "+";
        case MINUS:
            return "-";
        case MULTIPLY:
            return "*";
        case DIVIDE:
            return "/";
        case MODULUS:
            return "%";
        default:
            throw DbException.throwInternalError("opType=" + opType);
        }
    }

    @Override
    public Value getValue(Session session) {
        Database database = session.getDatabase();
        Value l = left.getValue(session).convertTo(type, database, true, null);
        Value r = right.getValue(session);
        if (convertRight) {
            r = r.convertTo(type, database, true, null);
        }
        switch (opType) {
        case PLUS:
            if (l == ValueNull.INSTANCE || r == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            return l.add(r);
        case MINUS:
            if (l == ValueNull.INSTANCE || r == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            return l.subtract(r);
        case MULTIPLY:
            if (l == ValueNull.INSTANCE || r == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            return l.multiply(r);
        case DIVIDE:
            if (l == ValueNull.INSTANCE || r == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            return l.divide(r);
        case MODULUS:
            if (l == ValueNull.INSTANCE || r == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            return l.modulus(r);
        default:
            throw DbException.throwInternalError("type=" + opType);
        }
    }

    @Override
    public void mapColumns(ColumnResolver resolver, int level, int state) {
        left.mapColumns(resolver, level, state);
        right.mapColumns(resolver, level, state);
    }

    @Override
    public Expression optimize(Session session) {
        left = left.optimize(session);
        right = right.optimize(session);
        switch (opType) {
        case PLUS:
        case MINUS:
        case MULTIPLY:
        case DIVIDE:
        case MODULUS:
            int l = left.getType().getValueType();
            int r = right.getType().getValueType();
            if ((l == Value.NULL && r == Value.NULL) ||
                    (l == Value.UNKNOWN && r == Value.UNKNOWN)) {
                // (? + ?) - use decimal by default (the most safe data type) or
                // string when text concatenation with + is enabled
                if (opType == OpType.PLUS && session.getDatabase().getMode().allowPlusForStringConcat) {
                    return new ConcatenationOperation(left, right).optimize(session);
                } else {
                    type = TypeInfo.TYPE_DECIMAL_DEFAULT;
                }
            } else if (DataType.isIntervalType(l) || DataType.isIntervalType(r)) {
                return optimizeInterval(session, l, r);
            } else if (DataType.isDateTimeType(l) || DataType.isDateTimeType(r)) {
                return optimizeDateTime(session, l, r);
            } else {
                int dataType = Value.getHigherOrder(l, r);
                if (dataType == Value.ENUM) {
                    type = TypeInfo.TYPE_INT;
                } else {
                    type = TypeInfo.getTypeInfo(dataType);
                    if (DataType.isStringType(dataType) && session.getDatabase().getMode().allowPlusForStringConcat) {
                        return new ConcatenationOperation(left, right).optimize(session);
                    }
                }
            }
            break;
        default:
            DbException.throwInternalError("type=" + opType);
        }
        if (left.isConstant() && right.isConstant()) {
            return ValueExpression.get(getValue(session));
        }
        return this;
    }

    private Expression optimizeInterval(Session session, int l, int r) {
        boolean lInterval = false, lNumeric = false, lDateTime = false;
        if (DataType.isIntervalType(l)) {
            lInterval = true;
        } else if (DataType.isNumericType(l)) {
            lNumeric = true;
        } else if (DataType.isDateTimeType(l)) {
            lDateTime = true;
        } else {
            throw getUnsupported(l, r);
        }
        boolean rInterval = false, rNumeric = false, rDateTime = false;
        if (DataType.isIntervalType(r)) {
            rInterval = true;
        } else if (DataType.isNumericType(r)) {
            rNumeric = true;
        } else if (DataType.isDateTimeType(r)) {
            rDateTime = true;
        } else {
            throw getUnsupported(l, r);
        }
        switch (opType) {
        case PLUS:
            if (lInterval && rInterval) {
                if (DataType.isYearMonthIntervalType(l) == DataType.isYearMonthIntervalType(r)) {
                    return new IntervalOperation(IntervalOpType.INTERVAL_PLUS_INTERVAL, left, right);
                }
            } else if (lInterval && rDateTime) {
                if (r == Value.TIME && DataType.isYearMonthIntervalType(l)) {
                    break;
                }
                return new IntervalOperation(IntervalOpType.DATETIME_PLUS_INTERVAL, right, left);
            } else if (lDateTime && rInterval) {
                if (l == Value.TIME && DataType.isYearMonthIntervalType(r)) {
                    break;
                }
                return new IntervalOperation(IntervalOpType.DATETIME_PLUS_INTERVAL, left, right);
            }
            break;
        case MINUS:
            if (lInterval && rInterval) {
                if (DataType.isYearMonthIntervalType(l) == DataType.isYearMonthIntervalType(r)) {
                    return new IntervalOperation(IntervalOpType.INTERVAL_MINUS_INTERVAL, left, right);
                }
            } else if (lDateTime && rInterval) {
                if (l == Value.TIME && DataType.isYearMonthIntervalType(r)) {
                    break;
                }
                return new IntervalOperation(IntervalOpType.DATETIME_MINUS_INTERVAL, left, right);
            }
            break;
        case MULTIPLY:
            if (lInterval && rNumeric) {
                return new IntervalOperation(IntervalOpType.INTERVAL_MULTIPLY_NUMERIC, left, right);
            } else if (lNumeric && rInterval) {
                return new IntervalOperation(IntervalOpType.INTERVAL_MULTIPLY_NUMERIC, right, left);
            }
            break;
        case DIVIDE:
            if (lInterval) {
                if (rNumeric) {
                    return new IntervalOperation(IntervalOpType.INTERVAL_DIVIDE_NUMERIC, left, right);
                } else if (rInterval && DataType.isYearMonthIntervalType(l) == DataType.isYearMonthIntervalType(r)) {
                    // Non-standard
                    return new IntervalOperation(IntervalOpType.INTERVAL_DIVIDE_INTERVAL, left, right);
                }
            }
            break;
        default:
        }
        throw getUnsupported(l, r);
    }

    private Expression optimizeDateTime(Session session, int l, int r) {
        switch (opType) {
        case PLUS:
            if (r != Value.getHigherOrder(l, r)) {
                // order left and right: INT < TIME < DATE < TIMESTAMP
                swap();
                int t = l;
                l = r;
                r = t;
            }
            switch (l) {
            case Value.INT: {
                // Oracle date add
                return Function.getFunctionWithArgs(session.getDatabase(), Function.DATEADD,
                        ValueExpression.get(ValueString.get("DAY")), left, right).optimize(session);
            }
            case Value.DECIMAL:
            case Value.FLOAT:
            case Value.DOUBLE: {
                // Oracle date add
                return Function.getFunctionWithArgs(session.getDatabase(), Function.DATEADD,
                        ValueExpression.get(ValueString.get("SECOND")),
                        new BinaryOperation(OpType.MULTIPLY, ValueExpression.get(ValueInt.get(60 * 60 * 24)), left),
                        right).optimize(session);
            }
            case Value.TIME:
            case Value.TIME_TZ:
                if (r == Value.TIME || r == Value.TIME_TZ || r == Value.TIMESTAMP_TZ) {
                    type = TypeInfo.getTypeInfo(r);
                    return this;
                } else { // DATE, TIMESTAMP
                    type = TypeInfo.TYPE_TIMESTAMP;
                    return this;
                }
            }
            break;
        case MINUS:
            switch (l) {
            case Value.DATE:
            case Value.TIMESTAMP:
            case Value.TIMESTAMP_TZ:
                switch (r) {
                case Value.INT: {
                    // Oracle date subtract
                    return Function.getFunctionWithArgs(session.getDatabase(), Function.DATEADD,
                            ValueExpression.get(ValueString.get("DAY")), //
                            new UnaryOperation(right), //
                            left).optimize(session);
                }
                case Value.DECIMAL:
                case Value.FLOAT:
                case Value.DOUBLE: {
                    // Oracle date subtract
                    return Function.getFunctionWithArgs(session.getDatabase(), Function.DATEADD,
                                ValueExpression.get(ValueString.get("SECOND")),
                                new UnaryOperation(new BinaryOperation(OpType.MULTIPLY, //
                                        ValueExpression.get(ValueInt.get(60 * 60 * 24)), right)), //
                                left).optimize(session);
                }
                case Value.TIME:
                case Value.TIME_TZ:
                    type = TypeInfo.TYPE_TIMESTAMP;
                    return this;
                case Value.DATE:
                case Value.TIMESTAMP:
                case Value.TIMESTAMP_TZ:
                    return new IntervalOperation(IntervalOpType.DATETIME_MINUS_DATETIME, left, right);
                }
                break;
            case Value.TIME:
            case Value.TIME_TZ:
                if (r == Value.TIME || r == Value.TIME_TZ) {
                    return new IntervalOperation(IntervalOpType.DATETIME_MINUS_DATETIME, left, right);
                }
                break;
            }
            break;
        case MULTIPLY:
            if (l == Value.TIME) {
                type = TypeInfo.TYPE_TIME;
                convertRight = false;
                return this;
            } else if (r == Value.TIME) {
                swap();
                type = TypeInfo.TYPE_TIME;
                convertRight = false;
                return this;
            }
            break;
        case DIVIDE:
            if (l == Value.TIME) {
                type = TypeInfo.TYPE_TIME;
                convertRight = false;
                return this;
            }
            break;
        default:
        }
        throw getUnsupported(l, r);
    }

    private DbException getUnsupported(int l, int r) {
        return DbException.getUnsupportedException(
                DataType.getDataType(l).name + ' ' + getOperationToken() + ' ' + DataType.getDataType(r).name);
    }

    private void swap() {
        Expression temp = left;
        left = right;
        right = temp;
    }

    @Override
    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        left.setEvaluatable(tableFilter, b);
        right.setEvaluatable(tableFilter, b);
    }

    @Override
    public TypeInfo getType() {
        return type;
    }

    @Override
    public void updateAggregate(Session session, int stage) {
        left.updateAggregate(session, stage);
        right.updateAggregate(session, stage);
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        return left.isEverything(visitor) && right.isEverything(visitor);
    }

    @Override
    public int getCost() {
        return left.getCost() + right.getCost() + 1;
    }

    @Override
    public int getSubexpressionCount() {
        return 2;
    }

    @Override
    public Expression getSubexpression(int index) {
        switch (index) {
        case 0:
            return left;
        case 1:
            return right;
        default:
            throw new IndexOutOfBoundsException();
        }
    }

}
