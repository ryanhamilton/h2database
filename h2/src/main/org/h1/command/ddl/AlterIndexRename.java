/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.ddl;

import org.h1.api.ErrorCode;
import org.h1.command.CommandInterface;
import org.h1.engine.Database;
import org.h1.engine.Right;
import org.h1.engine.Session;
import org.h1.index.Index;
import org.h1.message.DbException;
import org.h1.schema.Schema;

/**
 * This class represents the statement
 * ALTER INDEX RENAME
 */
public class AlterIndexRename extends DefineCommand {

    private boolean ifExists;
    private Schema oldSchema;
    private String oldIndexName;
    private String newIndexName;

    public AlterIndexRename(Session session) {
        super(session);
    }

    public void setIfExists(boolean b) {
        ifExists = b;
    }

    public void setOldSchema(Schema old) {
        oldSchema = old;
    }

    public void setOldName(String name) {
        oldIndexName = name;
    }

    public void setNewName(String name) {
        newIndexName = name;
    }

    @Override
    public int update() {
        session.commit(true);
        Database db = session.getDatabase();
        Index oldIndex = oldSchema.findIndex(session, oldIndexName);
        if (oldIndex == null) {
            if (!ifExists) {
                throw DbException.get(ErrorCode.INDEX_NOT_FOUND_1,
                        newIndexName);
            }
            return 0;
        }
        if (oldSchema.findIndex(session, newIndexName) != null ||
                newIndexName.equals(oldIndexName)) {
            throw DbException.get(ErrorCode.INDEX_ALREADY_EXISTS_1,
                    newIndexName);
        }
        session.getUser().checkRight(oldIndex.getTable(), Right.ALL);
        db.renameSchemaObject(session, oldIndex, newIndexName);
        return 0;
    }

    @Override
    public int getType() {
        return CommandInterface.ALTER_INDEX_RENAME;
    }

}
