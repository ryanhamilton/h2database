/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.test.unit;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.h1.util.json.JSONByteArrayTarget;
import org.h1.util.json.JSONBytesSource;
import org.h1.util.json.JSONItemType;
import org.h1.util.json.JSONStringSource;
import org.h1.util.json.JSONStringTarget;
import org.h1.util.json.JSONTarget;
import org.h1.util.json.JSONValidationTargetWithUniqueKeys;
import org.h1.util.json.JSONValidationTargetWithoutUniqueKeys;
import org.h1.util.json.JSONValueTarget;
import org.h1.test.TestBase;

/**
 * Tests the classes from org.h1.util.json package.
 */
public class TestJsonUtils extends TestBase {

    private static final Charset[] CHARSETS = { StandardCharsets.UTF_8, StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE, Charset.forName("UTF-32BE"), Charset.forName("UTF-32LE") };

    private static final Callable<JSONTarget<?>> STRING_TARGET = new Callable<JSONTarget<?>>() {
        @Override
        public JSONTarget<?> call() throws Exception {
            return new JSONStringTarget();
        }
    };

    private static final Callable<JSONTarget<?>> BYTES_TARGET = new Callable<JSONTarget<?>>() {
        @Override
        public JSONTarget<?> call() throws Exception {
            return new JSONByteArrayTarget();
        }
    };

    private static final Callable<JSONTarget<?>> VALUE_TARGET = new Callable<JSONTarget<?>>() {
        @Override
        public JSONTarget<?> call() throws Exception {
            return new JSONValueTarget();
        }
    };

    private static final Callable<JSONTarget<?>> JSON_VALIDATION_TARGET_WITHOUT_UNIQUE_KEYS = //
            new Callable<JSONTarget<?>>() {
                @Override
                public JSONTarget<?> call() throws Exception {
                    return new JSONValidationTargetWithoutUniqueKeys();
                }
            };

    private static final Callable<JSONTarget<?>> JSON_VALIDATION_TARGET_WITH_UNIQUE_KEYS = //
            new Callable<JSONTarget<?>>() {
                @Override
                public JSONTarget<?> call() throws Exception {
                    return new JSONValidationTargetWithUniqueKeys();
                }
            };

    /**
     * Run just this test.
     *
     * @param a
     *            ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
        testTargetErrorDetection();
        testSourcesAndTargets();
        testUtfError();
        testLongNesting();
        testEncodeString();
    }

    private void testTargetErrorDetection() throws Exception {
        testTargetErrorDetection(STRING_TARGET);
        testTargetErrorDetection(BYTES_TARGET);
        testTargetErrorDetection(VALUE_TARGET);
        testTargetErrorDetection(JSON_VALIDATION_TARGET_WITHOUT_UNIQUE_KEYS);
        testTargetErrorDetection(JSON_VALIDATION_TARGET_WITH_UNIQUE_KEYS);
    }

    private void testTargetErrorDetection(final Callable<JSONTarget<?>> constructor) throws Exception {
        JSONTarget<?> target;
        // Unexpected end of object or array
        target = constructor.call();
        try {
            target.endObject();
            fail();
        } catch (RuntimeException expected) {
        }
        target = constructor.call();
        try {
            target.endArray();
            fail();
        } catch (RuntimeException expected) {
        }
        // Unexpected member without object
        target = constructor.call();
        try {
            target.member("1");
            fail();
        } catch (RuntimeException expected) {
        }
        // Unexpected member inside array
        target = constructor.call();
        target.startArray();
        try {
            target.member("1");
            fail();
        } catch (RuntimeException expected) {
        }
        // Unexpected member without value
        target = constructor.call();
        target.startObject();
        target.member("1");
        try {
            target.member("2");
            fail();
        } catch (RuntimeException expected) {
        }
        target = constructor.call();
        target.startObject();
        target.member("1");
        try {
            target.endObject();
            fail();
        } catch (RuntimeException expected) {
        }
        // Unexpected value without member name
        testJsonStringTargetErrorDetectionAllValues(new Callable<JSONTarget<?>>() {
            @Override
            public JSONTarget<?> call() throws Exception {
                JSONTarget<?> target = constructor.call();
                target.startObject();
                return target;
            }
        });
        // Unexpected second value
        testJsonStringTargetErrorDetectionAllValues(new Callable<JSONTarget<?>>() {
            @Override
            public JSONTarget<?> call() throws Exception {
                JSONTarget<?> target = constructor.call();
                target.valueNull();
                return target;
            }
        });
        // No value
        target = constructor.call();
        try {
            target.getResult();
            fail();
        } catch (RuntimeException expected) {
        }
        // Unclosed object
        target = constructor.call();
        target.startObject();
        try {
            target.getResult();
            fail();
        } catch (RuntimeException expected) {
        }
        // Unclosed array
        target = constructor.call();
        target.startObject();
        try {
            target.getResult();
            fail();
        } catch (RuntimeException expected) {
        }
        // End of array after start of object or vice versa
        target = constructor.call();
        target.startObject();
        try {
            target.endArray();
            fail();
        } catch (RuntimeException expected) {
        }
        target = constructor.call();
        target.startArray();
        try {
            target.endObject();
            fail();
        } catch (RuntimeException expected) {
        }
    }

    private void testJsonStringTargetErrorDetectionAllValues(Callable<JSONTarget<?>> initializer) throws Exception {
        JSONTarget<?> target;
        target = initializer.call();
        try {
            target.valueNull();
            fail();
        } catch (RuntimeException expected) {
        }
        target = initializer.call();
        try {
            target.valueFalse();
            fail();
        } catch (RuntimeException expected) {
        }
        target = initializer.call();
        try {
            target.valueTrue();
            fail();
        } catch (RuntimeException expected) {
        }
        target = initializer.call();
        try {
            target.valueNumber(BigDecimal.ONE);
            fail();
        } catch (RuntimeException expected) {
        }
        target = initializer.call();
        try {
            target.valueString("string");
            fail();
        } catch (RuntimeException expected) {
        }
    }

    private void testSourcesAndTargets() throws Exception {
        testSourcesAndTargets("1", "1");
        testSourcesAndTargets("\uFEFF0", "0");
        testSourcesAndTargets("\uFEFF-1", "-1");
        testSourcesAndTargets("null", "null");
        testSourcesAndTargets("true", "true");
        testSourcesAndTargets("false", "false");
        testSourcesAndTargets("1.2", "1.2");
        testSourcesAndTargets("1.2e+1", "12");
        testSourcesAndTargets("10000.0", "10000.0");
        testSourcesAndTargets("\t\r\n 1.2E-1 ", "0.12");
        testSourcesAndTargets("9.99e99", "9.99E99");
        testSourcesAndTargets("\"\"", "\"\"");
        testSourcesAndTargets("\"\\b\\f\\t\\r\\n\\\"\\/\\\\\\u0019\\u0020\"", "\"\\b\\f\\t\\r\\n\\\"/\\\\\\u0019 \"");
        testSourcesAndTargets("{ }", "{}");
        testSourcesAndTargets("{\"a\" : 1}", "{\"a\":1}");
        testSourcesAndTargets("{\"a\" : 1, \"b\":[], \"c\":{}}", "{\"a\":1,\"b\":[],\"c\":{}}");
        testSourcesAndTargets("{\"a\" : 1, \"b\":[1,null, true,false,{}]}", "{\"a\":1,\"b\":[1,null,true,false,{}]}");
        testSourcesAndTargets("{\"1\" : [[[[[[[[[[11.1e-100]]]], null]]], {\n\r}]]]}",
                "{\"1\":[[[[[[[[[[1.11E-99]]]],null]]],{}]]]}");
        testSourcesAndTargets("{\"b\":false,\"a\":1,\"a\":null}", "{\"b\":false,\"a\":1,\"a\":null}", true);
        testSourcesAndTargets("[[{\"b\":false,\"a\":1,\"a\":null}]]", "[[{\"b\":false,\"a\":1,\"a\":null}]]", true);
        testSourcesAndTargets("\"\uD800\uDFFF\"", "\"\uD800\uDFFF\"");
        testSourcesAndTargets("\"\\uD800\\uDFFF\"", "\"\uD800\uDFFF\"");
        testSourcesAndTargets("\"\u0700\"", "\"\u0700\"");
        testSourcesAndTargets("\"\\u0700\"", "\"\u0700\"");
        StringBuilder builder = new StringBuilder().append('"');
        for (int cp = 0x80; cp < Character.MIN_SURROGATE; cp++) {
            builder.appendCodePoint(cp);
        }
        for (int cp = Character.MAX_SURROGATE + 1; cp < 0xfffe; cp++) {
            builder.appendCodePoint(cp);
        }
        for (int cp = 0xffff; cp <= Character.MAX_CODE_POINT; cp++) {
            builder.appendCodePoint(cp);
        }
        String s = builder.append('"').toString();
        testSourcesAndTargets(s, s);
        testSourcesAndTargetsError("", true);
        testSourcesAndTargetsError("\"", true);
        testSourcesAndTargetsError("\"\\u", true);
        testSourcesAndTargetsError("\u0080", true);
        testSourcesAndTargetsError(".1", true);
        testSourcesAndTargetsError("1.", true);
        testSourcesAndTargetsError("1.1e", true);
        testSourcesAndTargetsError("1.1e+", true);
        testSourcesAndTargetsError("1.1e-", true);
        testSourcesAndTargetsError("\b1", true);
        testSourcesAndTargetsError("\"\\u", true);
        testSourcesAndTargetsError("\"\\u0", true);
        testSourcesAndTargetsError("\"\\u00", true);
        testSourcesAndTargetsError("\"\\u000", true);
        testSourcesAndTargetsError("\"\\u0000", true);
        testSourcesAndTargetsError("{,}", true);
        testSourcesAndTargetsError("{,,}", true);
        testSourcesAndTargetsError("{}}", true);
        testSourcesAndTargetsError("{\"a\":\"\":\"\"}", true);
        testSourcesAndTargetsError("[]]", true);
        testSourcesAndTargetsError("\"\\uZZZZ\"", true);
        testSourcesAndTargetsError("\"\\x\"", true);
        testSourcesAndTargetsError("\"\\", true);
        testSourcesAndTargetsError("[1,", true);
        testSourcesAndTargetsError("[1,,2]", true);
        testSourcesAndTargetsError("[1,]", true);
        testSourcesAndTargetsError("{\"a\":1,]", true);
        testSourcesAndTargetsError("[1 2]", true);
        testSourcesAndTargetsError("{\"a\"-1}", true);
        testSourcesAndTargetsError("[1;2]", true);
        testSourcesAndTargetsError("{\"a\":1,b:2}", true);
        testSourcesAndTargetsError("{\"a\":1;\"b\":2}", true);
        testSourcesAndTargetsError("fals", true);
        testSourcesAndTargetsError("falsE", true);
        testSourcesAndTargetsError("False", true);
        testSourcesAndTargetsError("nul", true);
        testSourcesAndTargetsError("nulL", true);
        testSourcesAndTargetsError("Null", true);
        testSourcesAndTargetsError("tru", true);
        testSourcesAndTargetsError("truE", true);
        testSourcesAndTargetsError("True", true);
        testSourcesAndTargetsError("\"\uD800\"", false);
        testSourcesAndTargetsError("\"\\uD800\"", true);
        testSourcesAndTargetsError("\"\uDC00\"", false);
        testSourcesAndTargetsError("\"\\uDC00\"", true);
        testSourcesAndTargetsError("\"\uDBFF \"", false);
        testSourcesAndTargetsError("\"\\uDBFF \"", true);
        testSourcesAndTargetsError("\"\uDBFF\\\"", true);
        testSourcesAndTargetsError("\"\\uDBFF\\\"", true);
        testSourcesAndTargetsError("\"\uDFFF\uD800\"", false);
        testSourcesAndTargetsError("\"\\uDFFF\\uD800\"", true);
    }

    private void testSourcesAndTargets(String src, String expected) throws Exception {
        testSourcesAndTargets(src, expected, false);
    }

    private void testSourcesAndTargets(String src, String expected, boolean hasNonUniqueKeys) throws Exception {
        JSONItemType itemType;
        switch (expected.charAt(0)) {
        case '[':
            itemType = JSONItemType.ARRAY;
            break;
        case '{':
            itemType = JSONItemType.OBJECT;
            break;
        default:
            itemType = JSONItemType.SCALAR;
        }
        assertEquals(expected, JSONStringSource.parse(src, new JSONStringTarget()));
        assertEquals(expected.getBytes(StandardCharsets.UTF_8), //
                JSONStringSource.parse(src, new JSONByteArrayTarget()));
        assertEquals(expected, JSONStringSource.parse(src, new JSONValueTarget()).toString());
        assertEquals(itemType, JSONStringSource.parse(src, new JSONValidationTargetWithoutUniqueKeys()));
        if (hasNonUniqueKeys) {
            testSourcesAndTargetsError(src, JSON_VALIDATION_TARGET_WITH_UNIQUE_KEYS, true);
        } else {
            assertEquals(itemType, JSONStringSource.parse(src, new JSONValidationTargetWithUniqueKeys()));
        }
        for (Charset charset : CHARSETS) {
            assertEquals(expected, JSONBytesSource.parse(src.getBytes(charset), new JSONStringTarget()));
        }
    }

    private void testSourcesAndTargetsError(String src, boolean testBytes) throws Exception {
        testSourcesAndTargetsError(src, STRING_TARGET, testBytes);
        testSourcesAndTargetsError(src, BYTES_TARGET, testBytes);
        testSourcesAndTargetsError(src, VALUE_TARGET, testBytes);
        testSourcesAndTargetsError(src, JSON_VALIDATION_TARGET_WITHOUT_UNIQUE_KEYS, testBytes);
        testSourcesAndTargetsError(src, JSON_VALIDATION_TARGET_WITH_UNIQUE_KEYS, testBytes);
    }

    private void testSourcesAndTargetsError(String src, Callable<JSONTarget<?>> constructor, boolean testBytes)
            throws Exception {
        check: {
            JSONTarget<?> target = constructor.call();
            try {
                JSONStringSource.parse(src, target);
            } catch (IllegalArgumentException | IllegalStateException expected) {
                // Expected
                break check;
            }
            fail();
        }
        /*
         * String.getBytes() replaces invalid characters, so some tests are
         * disabled.
         */
        if (testBytes) {
            JSONTarget<?> target = constructor.call();
            try {
                JSONBytesSource.parse(src.getBytes(StandardCharsets.UTF_8), target);
            } catch (IllegalArgumentException | IllegalStateException expected) {
                // Expected
                return;
            }
            fail();
        }
    }

    private void testUtfError() {
        // 2 bytes
        testUtfError(new byte[] { '"', (byte) 0xc2, (byte) 0xc0, '"' });
        testUtfError(new byte[] { '"', (byte) 0xc1, (byte) 0xbf, '"' });
        testUtfError(new byte[] { '"', (byte) 0xc2 });
        // 3 bytes
        testUtfError(new byte[] { '"', (byte) 0xe1, (byte) 0xc0, (byte) 0x80, '"' });
        testUtfError(new byte[] { '"', (byte) 0xe1, (byte) 0x80, (byte) 0xc0, '"' });
        testUtfError(new byte[] { '"', (byte) 0xe0, (byte) 0x9f, (byte) 0xbf, '"' });
        testUtfError(new byte[] { '"', (byte) 0xe1, (byte) 0x80 });
        // 4 bytes
        testUtfError(new byte[] { '"', (byte) 0xf1, (byte) 0xc0, (byte) 0x80, (byte) 0x80, '"' });
        testUtfError(new byte[] { '"', (byte) 0xf1, (byte) 0x80, (byte) 0xc0, (byte) 0x80, '"' });
        testUtfError(new byte[] { '"', (byte) 0xf1, (byte) 0x80, (byte) 0x80, (byte) 0xc0, '"' });
        testUtfError(new byte[] { '"', (byte) 0xf0, (byte) 0x8f, (byte) 0xbf, (byte) 0xbf, '"' });
        testUtfError(new byte[] { '"', (byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80, '"' });
        testUtfError(new byte[] { '"', (byte) 0xf1, (byte) 0x80, (byte) 0x80 });
    }

    private void testUtfError(byte[] bytes) {
        try {
            JSONBytesSource.parse(bytes, new JSONValidationTargetWithoutUniqueKeys());
        } catch (IllegalArgumentException expected) {
            // Expected
            return;
        }
        fail();
    }

    private void testLongNesting() {
        final int halfLevel = 2048;
        StringBuilder builder = new StringBuilder(halfLevel * 8);
        for (int i = 0; i < halfLevel; i++) {
            builder.append("{\"a\":[");
        }
        for (int i = 0; i < halfLevel; i++) {
            builder.append("]}");
        }
        String string = builder.toString();
        assertEquals(string, JSONStringSource.parse(string, new JSONStringTarget()));
        byte[] bytes = string.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(bytes, JSONBytesSource.normalize(bytes));
    }

    private void testEncodeString() {
        testEncodeString("abc \"\u0001\u007f\u0080\u1000\uabcd\n'\t",
                "\"abc \\\"\\u0001\u007f\u0080\u1000\uabcd\\n'\\t\"",
                "\"abc \\\"\\u0001\u007f\\u0080\\u1000\\uabcd\\n\\u0027\\t\"");
    }

    private void testEncodeString(String source, String expected, String expectedPrintable) {
        assertEquals(expected, JSONStringTarget.encodeString(new StringBuilder(), source, false).toString());
        assertEquals(expectedPrintable, JSONStringTarget.encodeString(new StringBuilder(), source, true).toString());
    }

}
