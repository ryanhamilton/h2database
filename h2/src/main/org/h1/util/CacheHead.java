/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.util;

/**
 * The head element of the linked list.
 */
public class CacheHead extends CacheObject {

    @Override
    public boolean canRemove() {
        return false;
    }

    @Override
    public int getMemory() {
        return 0;
    }

}
