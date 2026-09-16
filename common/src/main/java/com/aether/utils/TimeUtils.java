package com.aether.utils;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间入参的解析与格式化。
 *
 * <p>这里只做纯函数，不决定「解析失败该怎么办」——是报错还是当作没传，属于调用方的策略，
 * 工具层与控制器对坏入参的处置并不一样。
 *
 * <p>用 {@code java.time} 的不可变类型：{@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} 与
 * {@link Instant} 都线程安全，不像 {@code SimpleDateFormat} 需要额外同步。
 */
public final class TimeUtils {
    /**
     * 纯数字毫秒串的最短位数。12 位对应 2001-09-09，更短的数字更可能是「2026」这类误输入 ——
     * 直接当毫秒会静默变成 1970-01-01T00:00:02Z，调用方拿到空结果却看不到任何错误。
     */
    private static final int MIN_MILLIS_DIGITS = 12;

    private TimeUtils() {
    }

    /**
     * 解析时间入参：先按 ISO-8601（必须带 {@code Z} 或偏移量），再按纯数字毫秒串。
     *
     * <p><b>裸本地时间（{@code 2026-09-15T10:00:00}）与裸日期（{@code 2026-09-15}）一律拒绝。</b>
     * 按服务器时区去猜，会让同一个请求的结果取决于它落到哪台机器上，而调用方无从得知。
     * 因为本项目对外输出的时间一律带 {@code Z}，往返总能带上偏移量，所以严格是安全的。
     *
     * @param value ISO-8601 时间串或毫秒数字串
     * @return 毫秒时间戳；无法解析时返回 {@code null}
     */
    public static Long parseEpochMillis(String value) {
        if (StringUtils.isBlank(value)) return null;
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // 不是 ISO，落到下面的数字分支。
        }
        if (!text.matches("\\d{" + MIN_MILLIS_DIGITS + ",}")) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 毫秒时间戳 → ISO-8601 UTC，如 {@code 2023-11-15T00:28:20Z}。
     *
     * @param epochMillis 毫秒时间戳，可为 {@code null}
     * @return ISO-8601 字符串；入参为 {@code null} 时返回 {@code null}
     */
    public static String formatIsoMillis(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis).toString();
    }
}
