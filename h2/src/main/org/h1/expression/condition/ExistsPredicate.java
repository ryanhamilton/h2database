/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.expression.condition;

import org.h1.command.dml.Query;
import org.h1.engine.Session;
import org.h1.result.ResultInterface;
import org.h1.value.Value;
import org.h1.value.ValueBoolean;

/**
 * Exists predicate as in EXISTS(SELECT ...)
 */
public class ExistsPredicate extends PredicateWithSubquery {

    public ExistsPredicate(Query query) {
        super(query);
    }

    @Override
    public Value getValue(Session session) {
        query.setSession(session);
        ResultInterface result = query.query(1);
        session.addTemporaryResult(result);
        return ValueBoolean.get(result.hasNext());
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, boolean alwaysQuote) {
        return super.getSQL(builder.append("EXISTS"), alwaysQuote);
    }

}
