/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h1.test.unit;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Random;

import org.h1.api.IntervalQualifier;
import org.h1.api.JavaObjectSerializer;
import org.h1.engine.Constants;
import org.h1.result.SimpleResult;
import org.h1.store.DataHandler;
import org.h1.store.FileStore;
import org.h1.store.LobStorageFrontend;
import org.h1.util.DateTimeUtils;
import org.h1.util.SmallLRUCache;
import org.h1.util.TempFileDeleter;
import org.h1.util.Utils;
import org.h1.value.CompareMode;
import org.h1.value.Value;
import org.h1.value.ValueArray;
import org.h1.value.ValueBoolean;
import org.h1.value.ValueByte;
import org.h1.value.ValueBytes;
import org.h1.value.ValueDate;
import org.h1.value.ValueDecimal;
import org.h1.value.ValueDouble;
import org.h1.value.ValueFloat;
import org.h1.value.ValueGeometry;
import org.h1.value.ValueInt;
import org.h1.value.ValueInterval;
import org.h1.value.ValueJavaObject;
import org.h1.value.ValueJson;
import org.h1.value.ValueLong;
import org.h1.value.ValueNull;
import org.h1.value.ValueResultSet;
import org.h1.value.ValueRow;
import org.h1.value.ValueShort;
import org.h1.value.ValueString;
import org.h1.value.ValueStringFixed;
import org.h1.value.ValueStringIgnoreCase;
import org.h1.value.ValueTime;
import org.h1.value.ValueTimeTimeZone;
import org.h1.value.ValueTimestamp;
import org.h1.value.ValueTimestampTimeZone;
import org.h1.value.ValueUuid;
import org.h1.test.TestBase;
import org.h1.test.utils.MemoryFootprint;

/**
 * Tests the memory consumption of values. Values can estimate how much memory
 * they occupy, and this tests if this estimation is correct.
 */
public class TestValueMemory extends TestBase implements DataHandler {

    private static final long MIN_ABSOLUTE_DAY = DateTimeUtils.absoluteDayFromDateValue(DateTimeUtils.MIN_DATE_VALUE);

    private static final long MAX_ABSOLUTE_DAY = DateTimeUtils.absoluteDayFromDateValue(DateTimeUtils.MAX_DATE_VALUE);

    private final Random random = new Random(1);
    private final SmallLRUCache<String, String[]> lobFileListCache = SmallLRUCache
            .newInstance(128);
    private LobStorageFrontend lobStorage;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        // run using -javaagent:ext/h2-1.2.139.jar
        TestBase test = TestBase.createCaller().init();
        test.config.traceTest = true;
        test.test();
    }

    @Override
    public void test() throws SQLException {
        testCompare();
        for (int i = 0; i < Value.TYPE_COUNT; i++) {
            if (i == 23) {
                // this used to be "TIMESTAMP UTC", which was a short-lived
                // experiment
                continue;
            }
            if (i == Value.ENUM) {
                // TODO ENUM
                continue;
            }
            Value v = create(i);
            String s = "type: " + v.getValueType() +
                    " calculated: " + v.getMemory() +
                    " real: " + MemoryFootprint.getObjectSize(v) + " " +
                    v.getClass().getName() + ": " + v.toString();
            trace(s);
        }
        for (int i = 0; i < Value.TYPE_COUNT; i++) {
            if (i == 23) {
                // this used to be "TIMESTAMP UTC", which was a short-lived
                // experiment
                continue;
            }
            if (i == Value.ENUM) {
                // TODO ENUM
                continue;
            }
            Value v = create(i);
            if (v == ValueNull.INSTANCE && i == Value.GEOMETRY) {
                // jts not in the classpath, OK
                continue;
            }
            assertEquals(i, v.getValueType());
            testType(i);
        }
    }

    private void testCompare() {
        ValueDecimal a = ValueDecimal.get(new BigDecimal("0.0"));
        ValueDecimal b = ValueDecimal.get(new BigDecimal("-0.00"));
        assertTrue(a.hashCode() != b.hashCode());
        assertFalse(a.equals(b));
    }

    private void testType(int type) throws SQLException {
        System.gc();
        System.gc();
        long first = Utils.getMemoryUsed();
        ArrayList<Value> list = new ArrayList<>();
        long memory = 0;
        while (memory < 1000000) {
            Value v = create(type);
            memory += v.getMemory() + Constants.MEMORY_POINTER;
            list.add(v);
        }
        Object[] array = list.toArray();
        IdentityHashMap<Object, Object> map = new IdentityHashMap<>();
        for (Object a : array) {
            map.put(a, a);
        }
        int size = map.size();
        map.clear();
        map = null;
        list = null;
        System.gc();
        System.gc();
        long used = Utils.getMemoryUsed() - first;
        memory /= 1024;
        if (config.traceTest || used > memory * 3) {
            String msg = "Type: " + type + " Used memory: " + used +
                    " calculated: " + memory + " length: " + array.length + " size: " + size;
            if (config.traceTest) {
                trace(msg);
            }
            if (used > memory * 3) {
                fail(msg);
            }
        }
    }
    private Value create(int type) throws SQLException {
        switch (type) {
        case Value.NULL:
            return ValueNull.INSTANCE;
        case Value.BOOLEAN:
            return ValueBoolean.FALSE;
        case Value.BYTE:
            return ValueByte.get((byte) random.nextInt());
        case Value.SHORT:
            return ValueShort.get((short) random.nextInt());
        case Value.INT:
            return ValueInt.get(random.nextInt());
        case Value.LONG:
            return ValueLong.get(random.nextLong());
        case Value.DECIMAL:
            return ValueDecimal.get(new BigDecimal(random.nextInt()));
            // + "12123344563456345634565234523451312312"
        case Value.DOUBLE:
            return ValueDouble.get(random.nextDouble());
        case Value.FLOAT:
            return ValueFloat.get(random.nextFloat());
        case Value.TIME:
            return ValueTime.fromNanos(randomTimeNanos());
        case Value.TIME_TZ:
            return ValueTimeTimeZone.fromNanos(randomTimeNanos(), randomZoneOffset());
        case Value.DATE:
            return ValueDate.fromDateValue(randomDateValue());
        case Value.TIMESTAMP:
            return ValueTimestamp.fromDateValueAndNanos(randomDateValue(), randomTimeNanos());
        case Value.TIMESTAMP_TZ:
            return ValueTimestampTimeZone.fromDateValueAndNanos(
                    randomDateValue(), randomTimeNanos(), randomZoneOffset());
        case Value.BYTES:
            return ValueBytes.get(randomBytes(random.nextInt(1000)));
        case Value.STRING:
            return ValueString.get(randomString(random.nextInt(100)));
        case Value.STRING_IGNORECASE:
            return ValueStringIgnoreCase.get(randomString(random.nextInt(100)));
        case Value.BLOB: {
            int len = (int) Math.abs(random.nextGaussian() * 10);
            byte[] data = randomBytes(len);
            return getLobStorage().createBlob(new ByteArrayInputStream(data), len);
        }
        case Value.CLOB: {
            int len = (int) Math.abs(random.nextGaussian() * 10);
            String s = randomString(len);
            return getLobStorage().createClob(new StringReader(s), len);
        }
        case Value.ARRAY:
            return ValueArray.get(createArray());
        case Value.ROW:
            return ValueRow.get(createArray());
        case Value.RESULT_SET:
            return ValueResultSet.get(new SimpleResult());
        case Value.JAVA_OBJECT:
            return ValueJavaObject.getNoCopy(null, randomBytes(random.nextInt(100)), this);
        case Value.UUID:
            return ValueUuid.get(random.nextLong(), random.nextLong());
        case Value.STRING_FIXED:
            return ValueStringFixed.get(randomString(random.nextInt(100)));
        case Value.GEOMETRY:
            return ValueGeometry.get("POINT (" + random.nextInt(100) + ' ' + random.nextInt(100) + ')');
        case Value.INTERVAL_YEAR:
        case Value.INTERVAL_MONTH:
        case Value.INTERVAL_DAY:
        case Value.INTERVAL_HOUR:
        case Value.INTERVAL_MINUTE:
            return ValueInterval.from(IntervalQualifier.valueOf(type - Value.INTERVAL_YEAR),
                    random.nextBoolean(), random.nextInt(Integer.MAX_VALUE), 0);
        case Value.INTERVAL_SECOND:
        case Value.INTERVAL_DAY_TO_SECOND:
        case Value.INTERVAL_HOUR_TO_SECOND:
        case Value.INTERVAL_MINUTE_TO_SECOND:
            return ValueInterval.from(IntervalQualifier.valueOf(type - Value.INTERVAL_YEAR),
                    random.nextBoolean(), random.nextInt(Integer.MAX_VALUE), random.nextInt(1_000_000_000));
        case Value.INTERVAL_YEAR_TO_MONTH:
        case Value.INTERVAL_DAY_TO_HOUR:
        case Value.INTERVAL_DAY_TO_MINUTE:
        case Value.INTERVAL_HOUR_TO_MINUTE:
            return ValueInterval.from(IntervalQualifier.valueOf(type - Value.INTERVAL_YEAR),
                    random.nextBoolean(), random.nextInt(Integer.MAX_VALUE), random.nextInt(12));
        case Value.JSON:
            return ValueJson.fromJson("{\"key\":\"value\"}");
        default:
            throw new AssertionError("type=" + type);
        }
    }

    private long randomDateValue() {
        return DateTimeUtils.dateValueFromAbsoluteDay(
                (random.nextLong() & Long.MAX_VALUE) % (MAX_ABSOLUTE_DAY - MIN_ABSOLUTE_DAY + 1) + MIN_ABSOLUTE_DAY);
    }

    private long randomTimeNanos() {
        return (random.nextLong() & Long.MAX_VALUE) % DateTimeUtils.NANOS_PER_DAY;
    }

    private short randomZoneOffset() {
        return (short) (random.nextInt() % (18 * 60));
    }

    private Value[] createArray() throws SQLException {
        int len = random.nextInt(20);
        Value[] list = new Value[len];
        for (int i = 0; i < list.length; i++) {
            list[i] = create(Value.STRING);
        }
        return list;
    }

    private byte[] randomBytes(int len) {
        byte[] data = new byte[len];
        if (random.nextBoolean()) {
            // don't initialize always (compression)
            random.nextBytes(data);
        }
        return data;
    }

    private String randomString(int len) {
        char[] chars = new char[len];
        if (random.nextBoolean()) {
            // don't initialize always (compression)
            for (int i = 0; i < chars.length; i++) {
                chars[i] = (char) (random.nextGaussian() * 100);
            }
        }
        return new String(chars);
    }

    @Override
    public void checkPowerOff() {
        // nothing to do
    }

    @Override
    public void checkWritingAllowed() {
        // nothing to do
    }

    @Override
    public String getDatabasePath() {
        return getBaseDir() + "/valueMemory";
    }

    @Override
    public String getLobCompressionAlgorithm(int type) {
        return "LZF";
    }

    @Override
    public Object getLobSyncObject() {
        return this;
    }

    @Override
    public int getMaxLengthInplaceLob() {
        return 100;
    }

    @Override
    public FileStore openFile(String name, String mode, boolean mustExist) {
        return FileStore.open(this, name, mode);
    }

    @Override
    public SmallLRUCache<String, String[]> getLobFileListCache() {
        return lobFileListCache;
    }

    @Override
    public TempFileDeleter getTempFileDeleter() {
        return TempFileDeleter.getInstance();
    }

    @Override
    public LobStorageFrontend getLobStorage() {
        if (lobStorage == null) {
            lobStorage = new LobStorageFrontend(this);
        }
        return lobStorage;
    }

    @Override
    public int readLob(long lobId, byte[] hmac, long offset, byte[] buff,
            int off, int length) {
        return -1;
    }

    @Override
    public JavaObjectSerializer getJavaObjectSerializer() {
        return null;
    }

    @Override
    public CompareMode getCompareMode() {
        return CompareMode.getInstance(null, 0);
    }
}
