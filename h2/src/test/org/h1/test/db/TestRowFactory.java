/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.test.db;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.h1.result.Row;
import org.h1.result.RowFactory;
import org.h1.result.RowImpl;
import org.h1.value.Value;
import org.h1.test.TestBase;
import org.h1.test.TestDb;

/**
 * Test {@link RowFactory} setting.
 *
 * @author Sergi Vladykin
 */
public class TestRowFactory extends TestDb {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String[] a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        deleteDb("rowFactory");
        Connection conn = getConnection("rowFactory;ROW_FACTORY=\"" +
                MyTestRowFactory.class.getName() + '"');
        Statement stat = conn.createStatement();
        stat.execute("create table t1(id int, name varchar)");
        for (int i = 0; i < 1000; i++) {
            stat.execute("insert into t1 values(" + i + ", 'name')");
        }
        assertTrue(MyTestRowFactory.COUNTER.get() >= 1000);
        conn.close();
        deleteDb("rowFactory");
    }

    /**
     * Test row factory.
     */
    public static class MyTestRowFactory extends RowFactory {

        /**
         * A simple counter.
         */
        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public Row createRow(Value[] data, int memory) {
            COUNTER.incrementAndGet();
            return new RowImpl(data, memory);
        }
    }
}
