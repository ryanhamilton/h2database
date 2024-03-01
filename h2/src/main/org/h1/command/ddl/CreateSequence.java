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
import org.h1.message.DbException;
import org.h1.schema.Schema;
import org.h1.schema.Sequence;

/**
 * This class represents the statement CREATE SEQUENCE.
 */
public class CreateSequence extends SchemaCommand {

    private String sequenceName;

    private boolean ifNotExists;

    private SequenceOptions options;

    private boolean belongsToTable;

    public CreateSequence(Session session, Schema schema) {
        super(session, schema);
    }

    public void setSequenceName(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    public void setIfNotExists(boolean ifNotExists) {
        this.ifNotExists = ifNotExists;
    }

    public void setOptions(SequenceOptions options) {
        this.options = options;
    }

    @Override
    public int update() {
        session.commit(true);
        Database db = session.getDatabase();
        if (getSchema().findSequence(sequenceName) != null) {
            if (ifNotExists) {
                return 0;
            }
            throw DbException.get(ErrorCode.SEQUENCE_ALREADY_EXISTS_1, sequenceName);
        }
        int id = getObjectId();
        Sequence sequence = new Sequence(session, getSchema(), id, sequenceName, options, belongsToTable);
        db.addSchemaObject(session, sequence);
        return 0;
    }

    public void setBelongsToTable(boolean belongsToTable) {
        this.belongsToTable = belongsToTable;
    }

    @Override
    public int getType() {
        return CommandInterface.CREATE_SEQUENCE;
    }

}
