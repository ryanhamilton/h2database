/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.ddl;

import org.h1.api.ErrorCode;
import org.h1.command.CommandInterface;
import org.h1.engine.Database;
import org.h1.engine.Session;
import org.h1.engine.UserAggregate;
import org.h1.message.DbException;
import org.h1.schema.Schema;

/**
 * This class represents the statement
 * CREATE AGGREGATE
 */
public class CreateAggregate extends DefineCommand {

    private Schema schema;
    private String name;
    private String javaClassMethod;
    private boolean ifNotExists;
    private boolean force;

    public CreateAggregate(Session session) {
        super(session);
    }

    @Override
    public int update() {
        session.commit(true);
        session.getUser().checkAdmin();
        Database db = session.getDatabase();
        if (db.findAggregate(name) != null || schema.findFunction(name) != null) {
            if (!ifNotExists) {
                throw DbException.get(
                        ErrorCode.FUNCTION_ALIAS_ALREADY_EXISTS_1, name);
            }
        } else {
            int id = getObjectId();
            UserAggregate aggregate = new UserAggregate(
                    db, id, name, javaClassMethod, force);
            db.addDatabaseObject(session, aggregate);
        }
        return 0;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setJavaClassMethod(String string) {
        this.javaClassMethod = string;
    }

    public void setIfNotExists(boolean ifNotExists) {
        this.ifNotExists = ifNotExists;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public int getType() {
        return CommandInterface.CREATE_AGGREGATE;
    }

}
