/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.command.dml;

import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.h1.command.CommandInterface;
import org.h1.command.Prepared;
import org.h1.engine.Database;
import org.h1.engine.DbObject;
import org.h1.engine.Session;
import org.h1.expression.Expression;
import org.h1.expression.ExpressionColumn;
import org.h1.mvstore.db.MVTableEngine.Store;
import org.h1.pagestore.PageStore;
import org.h1.result.LocalResult;
import org.h1.result.ResultInterface;
import org.h1.table.Column;
import org.h1.value.Value;
import org.h1.value.ValueString;

/**
 * This class represents the statement
 * EXPLAIN
 */
public class Explain extends Prepared {

    private Prepared command;
    private LocalResult result;
    private boolean executeCommand;

    public Explain(Session session) {
        super(session);
    }

    public void setCommand(Prepared command) {
        this.command = command;
    }

    public Prepared getCommand() {
        return command;
    }

    @Override
    public void prepare() {
        command.prepare();
    }

    public void setExecuteCommand(boolean executeCommand) {
        this.executeCommand = executeCommand;
    }

    @Override
    public ResultInterface queryMeta() {
        return query(-1);
    }

    @Override
    protected void checkParameters() {
        // Check params only in case of EXPLAIN ANALYZE
        if (executeCommand) {
            super.checkParameters();
        }
    }

    @Override
    public ResultInterface query(int maxrows) {
        Column column = new Column("PLAN", Value.STRING);
        Database db = session.getDatabase();
        ExpressionColumn expr = new ExpressionColumn(db, column);
        Expression[] expressions = { expr };
        result = db.getResultFactory().create(session, expressions, 1, 1);
        boolean alwaysQuote = true;
        if (maxrows >= 0) {
            String plan;
            if (executeCommand) {
                PageStore store = null;
                Store mvStore = null;
                if (db.isPersistent()) {
                    store = db.getPageStore();
                    if (store != null) {
                        store.statisticsStart();
                    }
                    mvStore = db.getStore();
                    if (mvStore != null) {
                        mvStore.statisticsStart();
                    }
                }
                if (command.isQuery()) {
                    command.query(maxrows);
                } else {
                    command.update();
                }
                plan = command.getPlanSQL(alwaysQuote);
                Map<String, Integer> statistics = null;
                if (store != null) {
                    statistics = store.statisticsEnd();
                } else if (mvStore != null) {
                    statistics = mvStore.statisticsEnd();
                }
                if (statistics != null) {
                    int total = 0;
                    for (Entry<String, Integer> e : statistics.entrySet()) {
                        total += e.getValue();
                    }
                    if (total > 0) {
                        statistics = new TreeMap<>(statistics);
                        StringBuilder buff = new StringBuilder();
                        if (statistics.size() > 1) {
                            buff.append("total: ").append(total).append('\n');
                        }
                        for (Entry<String, Integer> e : statistics.entrySet()) {
                            int value = e.getValue();
                            int percent = (int) (100L * value / total);
                            buff.append(e.getKey()).append(": ").append(value);
                            if (statistics.size() > 1) {
                                buff.append(" (").append(percent).append("%)");
                            }
                            buff.append('\n');
                        }
                        plan += "\n/*\n" + buff.toString() + "*/";
                    }
                }
            } else {
                plan = command.getPlanSQL(alwaysQuote);
            }
            add(plan);
        }
        result.done();
        return result;
    }

    private void add(String text) {
        result.addRow(ValueString.get(text));
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
        return command.isReadOnly();
    }

    @Override
    public int getType() {
        return executeCommand ? CommandInterface.EXPLAIN_ANALYZE : CommandInterface.EXPLAIN;
    }

    @Override
    public void collectDependencies(HashSet<DbObject> dependencies) {
        command.collectDependencies(dependencies);
    }

}
