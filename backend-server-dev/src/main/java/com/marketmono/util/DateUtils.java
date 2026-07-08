package com.marketmono.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility class for date operations using Eastern Standard Time (EST/EDT)
 * All financial APIs should use EST for date parameters to ensure consistency
 */
public final class DateUtils {

    /**
     * Eastern Time Zone ID (America/New_York)
     * Covers both EST (Eastern Standard Time) and EDT (Eastern Daylight Time)
     */
    public static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");

    private DateUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Get the current date in Eastern Time
     * This should be used for all financial API date parameters
     *
     * @return LocalDate representing today in EST
     */
    public static LocalDate getCurrentDateInEasternTime() {
        return ZonedDateTime.now(EASTERN_TIME).toLocalDate();
    }

    /**
     * Get the start of the current Eastern Time calendar day as an instant.
     * This is the default lower bound for same-day market data queries.
     *
     * @return Instant representing today's midnight in Eastern Time
     */
    public static Instant startOfCurrentEasternDay() {
        return getCurrentDateInEasternTime()
            .atStartOfDay(EASTERN_TIME)
            .toInstant();
    }

    public static LocalDate toEasternDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(EASTERN_TIME).toLocalDate();
    }

    public static Instant startOfEasternDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(EASTERN_TIME).toInstant();
    }

    public static Instant startOfNextEasternDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return startOfEasternDay(date.plusDays(1));
    }

    public static Instant endOfEasternDay(LocalDate date) {
        Instant nextDayStart = startOfNextEasternDay(date);
        return nextDayStart != null ? nextDayStart.minusNanos(1) : null;
    }

    /**
     * Convert a LocalDate to Eastern Time and format as YYYY-MM-DD string
     * Useful for API calls that require date strings
     *
     * @param date The date to convert (assumed to be in system time zone)
     * @return String in YYYY-MM-DD format representing the date in EST
     */
    public static String formatDateForEasternTime(LocalDate date) {
        if (date == null) {
            return null;
        }
        // Convert the LocalDate to Eastern Time at start of day
        ZonedDateTime easternDateTime = date.atStartOfDay(EASTERN_TIME);
        return easternDateTime.toLocalDate().toString();
    }

    /**
     * Check if a given date is in the future relative to Eastern Time
     * Useful for validating dates before sending to APIs that reject future dates
     *
     * @param date The date to check
     * @return true if the date is in the future in EST
     */
    public static boolean isFutureDateInEasternTime(LocalDate date) {
        if (date == null) {
            return false;
        }
        LocalDate todayEastern = getCurrentDateInEasternTime();
        return date.isAfter(todayEastern);
    }
}
