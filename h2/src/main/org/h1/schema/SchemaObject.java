/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.schema;

import org.h1.engine.DbObject;

/**
 * Any database object that is stored in a schema.
 */
public interface SchemaObject extends DbObject {

    /**
     * Get the schema in which this object is defined
     *
     * @return the schema
     */
    Schema getSchema();

    /**
     * Check whether this is a hidden object that doesn't appear in the meta
     * data and in the script, and is not dropped on DROP ALL OBJECTS.
     *
     * @return true if it is hidden
     */
    boolean isHidden();

}
