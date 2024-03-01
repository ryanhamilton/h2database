/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.test.unit;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.h1.util.MathUtils;
import org.h1.value.ValueInt;
import org.h1.test.TestBase;

/**
 * Tests if Tomcat would clear static fields when re-loading a web application.
 * See also
 * http://svn.apache.org/repos/asf/tomcat/trunk/java/org/apache/catalina
 * /loader/WebappClassLoader.java
 */
public class TestClearReferences extends TestBase {

    private static final String[] KNOWN_REFRESHED = {
        "org.h1.compress.CompressLZF.cachedHashTable",
        "org.h1.engine.DbSettings.defaultSettings",
        "org.h1.engine.SessionRemote.sessionFactory",
        "org.h1.expression.function.DateTimeFunctions.MONTHS_AND_WEEKS",
        "org.h1.expression.function.ToChar.NAMES",
        "org.h1.jdbcx.JdbcDataSourceFactory.cachedTraceSystem",
        "org.h1.store.RecoverTester.instance",
        "org.h1.store.fs.FilePath.defaultProvider",
        "org.h1.store.fs.FilePath.providers",
        "org.h1.store.fs.FilePath.tempRandom",
        "org.h1.store.fs.FilePathRec.recorder",
        "org.h1.store.fs.FileMemData.data",
        "org.h1.tools.CompressTool.cachedBuffer",
        "org.h1.util.CloseWatcher.queue",
        "org.h1.util.CloseWatcher.refs",
        "org.h1.util.DateTimeUtils.LOCAL",
        "org.h1.util.MathUtils.cachedSecureRandom",
        "org.h1.util.NetUtils.cachedLocalAddress",
        "org.h1.util.StringUtils.softCache",
        "org.h1.util.TimeZoneProvider.CACHE",
        "org.h1.util.JdbcUtils.allowedClassNames",
        "org.h1.util.JdbcUtils.allowedClassNamePrefixes",
        "org.h1.util.JdbcUtils.userClassFactories",
        "org.h1.util.Task.counter",
        "org.h1.value.CompareMode.lastUsed",
        "org.h1.value.Value.softCache",
        "org.h1.value.ValueBytes.type",
        "org.h1.value.ValueCollectionBase.type",
        "org.h1.value.ValueDecimal.type",
        "org.h1.value.ValueInterval.type",
        "org.h1.value.ValueLob.type",
        "org.h1.value.ValueLobDb.type",
        "org.h1.value.ValueString.type",
    };

    /**
     * Path to main sources. In IDE project may be located either in the root
     * directory of repository or in the h2 subdirectory.
     */
    private final String SOURCE_PATH = new File("h2/src/main/org/h2/Driver.java").exists()
            ? "h2/src/main/" : "src/main/";

    private boolean hasError;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        // initialize the known classes
        MathUtils.secureRandomLong();
        ValueInt.get(1);
        Class.forName("org.h1.store.fs.FileMemData");

        clear();

        if (hasError) {
            fail("Tomcat may clear the field above when reloading the web app");
        }
        for (String s : KNOWN_REFRESHED) {
            String className = s.substring(0, s.lastIndexOf('.'));
            String fieldName = s.substring(s.lastIndexOf('.') + 1);
            Class<?> clazz = Class.forName(className);
            try {
                clazz.getDeclaredField(fieldName);
            } catch (Exception e) {
                fail(s);
            }
        }
    }

    private void clear() throws Exception {
        ArrayList<Class <?>> classes = new ArrayList<>();
        findClasses(classes, new File("bin/org/h2"));
        findClasses(classes, new File("temp/org/h2"));
        for (Class<?> clazz : classes) {
            clearClass(clazz);
        }
    }

    private void findClasses(ArrayList<Class <?>> classes, File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            if (name.equals("CVS") || name.equals(".svn")) {
                return;
            }
            for (File f : file.listFiles()) {
                findClasses(classes, f);
            }
        } else {
            if (!name.endsWith(".class")) {
                return;
            }
            if (name.indexOf('$') >= 0) {
                return;
            }
            String className = file.getAbsolutePath().replace('\\', '/');
            className = className.substring(className.lastIndexOf("org/h2"));
            String packageName = className.substring(0, className.lastIndexOf('/'));
            if (!new File(SOURCE_PATH + packageName).exists()) {
                return;
            }
            className = className.replace('/', '.');
            className = className.substring(0, className.length() - ".class".length());
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (NoClassDefFoundError e) {
                if (e.toString().contains("lucene")) {
                    // Lucene is not in the classpath, OK
                }
            } catch (ClassNotFoundException e) {
                fail("Could not load " + className + ": " + e.toString());
            }
            if (clazz != null) {
                classes.add(clazz);
            }
        }
    }

    /**
     * This is how Tomcat resets the fields as of 2009-01-30.
     *
     * @param clazz the class to clear
     */
    private void clearClass(Class<?> clazz) throws Exception {
        Field[] fields;
        try {
            fields = clazz.getDeclaredFields();
        } catch (NoClassDefFoundError e) {
            if (e.toString().contains("lucene")) {
                // Lucene is not in the classpath, OK
                return;
            } else if (e.toString().contains("jts")) {
                // JTS is not in the classpath, OK
                return;
            } else if (e.toString().contains("slf4j")) {
                // slf4j is not in the classpath, OK
                return;
            }
            throw e;
        }
        for (Field field : fields) {
            if (field.getType().isPrimitive() || field.getName().contains("$")) {
                continue;
            }
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            Object o = field.get(null);
            if (o == null) {
                continue;
            }
            if (Modifier.isFinal(modifiers)) {
                if (field.getType().getName().startsWith("java.")) {
                    continue;
                }
                if (field.getType().getName().startsWith("javax.")) {
                    continue;
                }
                clearInstance(o);
            } else {
                clearField(clazz.getName() + "." + field.getName() + " = " + o);
            }
        }
    }

    private void clearInstance(Object instance) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType().isPrimitive() || field.getName().contains("$")) {
                continue;
            }
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            Object o = field.get(instance);
            if (o == null) {
                continue;
            }
            // loadedByThisOrChild
            if (o.getClass().getName().startsWith("java.lang.")) {
                continue;
            }
            if (o.getClass().isArray() && o.getClass().getComponentType().isPrimitive()) {
                continue;
            }
            clearField(instance.getClass().getName() + "." + field.getName() + " = " + o);
        }
    }

    private void clearField(String s) {
        for (String k : KNOWN_REFRESHED) {
            if (s.startsWith(k)) {
                return;
            }
        }
        hasError = true;
        System.out.println(s);
    }

}
