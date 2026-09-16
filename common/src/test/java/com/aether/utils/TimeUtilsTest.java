package com.aether.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证时间入参解析与格式化的行为。
 */
class TimeUtilsTest {

    /** 2025-09-16T04:00:00Z，同时也是 2025-09-16T12:00:00+08:00。 */
    private static final long SEPT_16_2025_04_UTC = 1757995200000L;

    @Test
    void parsesIsoWithZuluAndWithOffsetToTheSameInstant() {
        // 同一个时刻的两种写法必须落到同一个毫秒值，否则模型的表达方式会改变查询结果。
        assertEquals(Long.valueOf(SEPT_16_2025_04_UTC), TimeUtils.parseEpochMillis("2025-09-16T04:00:00Z"));
        assertEquals(Long.valueOf(SEPT_16_2025_04_UTC), TimeUtils.parseEpochMillis("2025-09-16T12:00:00+08:00"));
    }

    @Test
    void parsesPlainEpochMillisString() {
        assertEquals(Long.valueOf(SEPT_16_2025_04_UTC), TimeUtils.parseEpochMillis("1757995200000"));
    }

    @Test
    void rejectsBareLocalDateTimeAndBareDate() {
        // 这两种都没带偏移量。按服务器时区猜会让结果取决于请求落到哪台机器上，只能拒绝。
        assertNull(TimeUtils.parseEpochMillis("2026-09-15T10:00:00"));
        assertNull(TimeUtils.parseEpochMillis("2026-09-15"));
    }

    @Test
    void rejectsAShortNumberInsteadOfReadingItAsMillis() {
        // 「2026」当毫秒会变成 1970-01-01T00:00:02Z，调用方拿到空结果却看不到任何错误。
        assertNull(TimeUtils.parseEpochMillis("2026"));
        // 11 位，差一位到门槛。
        assertNull(TimeUtils.parseEpochMillis("17579952000"));
    }

    @Test
    void acceptsTheShortestMillisStringThatIsStillMilliseconds() {
        // 12 位是门槛本身，必须放行 —— 门槛是「至少 12 位」而不是「多于 12 位」。
        assertEquals(Long.valueOf(175799520000L), TimeUtils.parseEpochMillis("175799520000"));
    }

    @Test
    void rejectsBlankAndGarbage() {
        assertNull(TimeUtils.parseEpochMillis(null));
        assertNull(TimeUtils.parseEpochMillis(""));
        assertNull(TimeUtils.parseEpochMillis("   "));
        assertNull(TimeUtils.parseEpochMillis("abc"));
        assertNull(TimeUtils.parseEpochMillis("-1758000000000"));
    }

    @Test
    void formatsMillisAsIsoAndPassesNullThrough() {
        assertEquals("2025-09-16T04:00:00Z", TimeUtils.formatIsoMillis(SEPT_16_2025_04_UTC));
        assertNull(TimeUtils.formatIsoMillis(null));
    }

    @Test
    void formattedValueParsesBackToTheSameInstant() {
        // 输出必须能被自己解析回去：模型会把清单里看到的时间直接拿来当查询入参。
        Long original = 1789539323482L;
        assertEquals(original, TimeUtils.parseEpochMillis(TimeUtils.formatIsoMillis(original)));
    }
}
