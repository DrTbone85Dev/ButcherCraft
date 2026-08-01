package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BusinessOperatingSchedule {
    public static final long MINUTES_PER_DAY = BusinessCalendarSnapshot.BUSINESS_MINUTES_PER_DAY;
    private static final int WEEK_DAYS = BusinessDayOfWeek.values().length;

    private final Map<BusinessDayOfWeek, BusinessOperatingWindow> windows;
    private final BusinessOperatingScheduleIdentity identity;
    private final String canonical;

    private BusinessOperatingSchedule(Map<BusinessDayOfWeek, BusinessOperatingWindow> windows) {
        EnumMap<BusinessDayOfWeek, BusinessOperatingWindow> ordered = new EnumMap<>(BusinessDayOfWeek.class);
        for (BusinessDayOfWeek day : BusinessDayOfWeek.values()) {
            BusinessOperatingWindow window = windows.get(day);
            if (window != null) {
                if (window.dayOfWeek() != day) {
                    throw new IllegalArgumentException("Operating window day mismatch: " + day);
                }
                ordered.put(day, window);
            }
        }
        this.windows = Map.copyOf(ordered);
        rejectOverlaps(this.windows);
        this.canonical = canonical(this.windows);
        this.identity = BusinessOperatingScheduleIdentity.fromCanonical(canonical);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BusinessOperatingSchedule defaultSchedule() {
        Builder builder = builder();
        for (BusinessDayOfWeek day : List.of(
                BusinessDayOfWeek.MONDAY,
                BusinessDayOfWeek.TUESDAY,
                BusinessDayOfWeek.WEDNESDAY,
                BusinessDayOfWeek.THURSDAY,
                BusinessDayOfWeek.FRIDAY
        )) {
            builder.open(day, "06:00-18:00");
        }
        builder.closed(BusinessDayOfWeek.SATURDAY);
        builder.closed(BusinessDayOfWeek.SUNDAY);
        return builder.build();
    }

    public BusinessOperatingScheduleIdentity identity() {
        return identity;
    }

    public String canonical() {
        return canonical;
    }

    public List<BusinessOperatingWindow> openWindows() {
        return BusinessDayOfWeek.values().length == 0 ? List.of()
                : BusinessDayOfWeekStream.windowsInOrder(windows);
    }

    public Optional<BusinessOperatingWindow> windowFor(BusinessDayOfWeek day) {
        return Optional.ofNullable(windows.get(Objects.requireNonNull(day, "day")));
    }

    public Optional<BusinessScheduleBoundary> currentWindow(BusinessCalendarSnapshot calendar) {
        return windowAt(absoluteMinute(calendar));
    }

    public boolean isOpen(BusinessCalendarSnapshot calendar) {
        return currentWindow(calendar).isPresent();
    }

    public Optional<BusinessScheduleBoundary> nextOpening(BusinessCalendarSnapshot calendar) {
        long current = absoluteMinute(calendar);
        for (int offset = 0; offset <= WEEK_DAYS; offset++) {
            long dayIndex = Math.addExact(calendar.businessDayIndex(), offset);
            BusinessDayOfWeek day = BusinessDayOfWeek.fromDayIndex(dayIndex);
            BusinessOperatingWindow window = windows.get(day);
            if (window == null) continue;
            long start = Math.addExact(dayIndex * MINUTES_PER_DAY, window.startMinuteOfDay());
            if (start > current) {
                return Optional.of(boundary(dayIndex, window.start(), Optional.of(identity.value()), "Opening"));
            }
        }
        return Optional.empty();
    }

    public Optional<BusinessScheduleBoundary> nextClosing(BusinessCalendarSnapshot calendar) {
        long current = absoluteMinute(calendar);
        for (int offset = -1; offset <= WEEK_DAYS; offset++) {
            long dayIndex = Math.addExact(calendar.businessDayIndex(), offset);
            BusinessDayOfWeek day = BusinessDayOfWeek.fromDayIndex(dayIndex);
            BusinessOperatingWindow window = windows.get(day);
            if (window == null) continue;
            long start = Math.addExact(dayIndex * MINUTES_PER_DAY, window.startMinuteOfDay());
            long end = Math.addExact(start, window.durationMinutes());
            if (current >= start && current < end || end > current) {
                if (end > current) {
                    return Optional.of(boundaryForAbsolute(end, Optional.of(identity.value()), "Closing"));
                }
            }
        }
        return Optional.empty();
    }

    boolean containsInterval(long startAbsoluteMinute, long endExclusiveAbsoluteMinute) {
        if (endExclusiveAbsoluteMinute <= startAbsoluteMinute) {
            throw new IllegalArgumentException("Interval end must be after start");
        }
        Optional<BusinessScheduleBoundary> active = windowAt(startAbsoluteMinute);
        if (active.isEmpty()) {
            return false;
        }
        Optional<BusinessScheduleBoundary> closing = nextClosingAt(startAbsoluteMinute);
        return closing.isPresent() && endExclusiveAbsoluteMinute <= closing.orElseThrow().absoluteMinute();
    }

    public static long absoluteMinute(BusinessCalendarSnapshot calendar) {
        Objects.requireNonNull(calendar, "calendar");
        return Math.addExact(Math.multiplyExact(calendar.businessDayIndex(), MINUTES_PER_DAY),
                calendar.timeOfDay().hour() * 60L + calendar.timeOfDay().minute());
    }

    static BusinessScheduleBoundary boundaryForAbsolute(
            long absoluteMinute,
            Optional<String> identity,
            String displayName
    ) {
        long day = Math.floorDiv(absoluteMinute, MINUTES_PER_DAY);
        int minute = (int) Math.floorMod(absoluteMinute, MINUTES_PER_DAY);
        return boundary(day, new BusinessTimeOfDay(minute / 60, minute % 60), identity, displayName);
    }

    private Optional<BusinessScheduleBoundary> windowAt(long absoluteMinute) {
        for (int offset = -1; offset <= 0; offset++) {
            long dayIndex = Math.addExact(Math.floorDiv(absoluteMinute, MINUTES_PER_DAY), offset);
            BusinessOperatingWindow window = windows.get(BusinessDayOfWeek.fromDayIndex(dayIndex));
            if (window == null) continue;
            long start = Math.addExact(dayIndex * MINUTES_PER_DAY, window.startMinuteOfDay());
            long end = Math.addExact(start, window.durationMinutes());
            if (absoluteMinute >= start && absoluteMinute < end) {
                return Optional.of(boundary(dayIndex, window.start(), Optional.of(identity.value()), "Open"));
            }
        }
        return Optional.empty();
    }

    private Optional<BusinessScheduleBoundary> nextClosingAt(long absoluteMinute) {
        BusinessCalendarSnapshot synthetic = BusinessCalendarFactory.synthetic(absoluteMinute);
        return nextClosing(synthetic);
    }

    private static BusinessScheduleBoundary boundary(
            long dayIndex,
            BusinessTimeOfDay time,
            Optional<String> identity,
            String displayName
    ) {
        return new BusinessScheduleBoundary(dayIndex, BusinessDayOfWeek.fromDayIndex(dayIndex), time, identity,
                displayName);
    }

    private static String canonical(Map<BusinessDayOfWeek, BusinessOperatingWindow> windows) {
        StringBuilder builder = new StringBuilder("schema_version=")
                .append(BusinessRuntimeCalendarSchema.CURRENT_VERSION)
                .append('\n')
                .append("weekday_mapping=monday_day_0\n")
                .append("timezone_mode=")
                .append(BusinessRuntimeCalendarSchema.TIMEZONE_MODE_BUSINESS_CALENDAR)
                .append('\n');
        for (BusinessDayOfWeek day : BusinessDayOfWeek.values()) {
            BusinessOperatingWindow window = windows.get(day);
            builder.append(window == null ? day.serializedName() + "=closed" : window.canonicalLine()).append('\n');
        }
        return builder.toString();
    }

    private static void rejectOverlaps(Map<BusinessDayOfWeek, BusinessOperatingWindow> windows) {
        List<Interval> intervals = new ArrayList<>();
        for (int week = 0; week < 2; week++) {
            for (BusinessDayOfWeek day : BusinessDayOfWeek.values()) {
                BusinessOperatingWindow window = windows.get(day);
                if (window == null) continue;
                long dayBase = (long) week * WEEK_DAYS * MINUTES_PER_DAY + day.ordinal() * MINUTES_PER_DAY;
                long start = dayBase + window.startMinuteOfDay();
                long end = start + window.durationMinutes();
                intervals.add(new Interval(start, end, day));
            }
        }
        intervals.sort(java.util.Comparator.comparingLong(Interval::start));
        for (int index = 1; index < intervals.size(); index++) {
            Interval previous = intervals.get(index - 1);
            Interval current = intervals.get(index);
            if (current.start < previous.end) {
                throw new IllegalArgumentException("Operating windows overlap near " + current.day);
            }
        }
    }

    public static final class Builder {
        private final EnumMap<BusinessDayOfWeek, BusinessOperatingWindow> windows =
                new EnumMap<>(BusinessDayOfWeek.class);

        public Builder open(BusinessDayOfWeek day, String range) {
            BusinessDayOfWeek normalizedDay = Objects.requireNonNull(day, "day");
            windows.put(normalizedDay, BusinessOperatingWindow.of(normalizedDay, range));
            return this;
        }

        public Builder open(BusinessDayOfWeek day, BusinessTimeOfDay start, BusinessTimeOfDay end) {
            BusinessDayOfWeek normalizedDay = Objects.requireNonNull(day, "day");
            windows.put(normalizedDay, new BusinessOperatingWindow(normalizedDay, start, end));
            return this;
        }

        public Builder closed(BusinessDayOfWeek day) {
            windows.remove(Objects.requireNonNull(day, "day"));
            return this;
        }

        public BusinessOperatingSchedule build() {
            return new BusinessOperatingSchedule(windows);
        }
    }

    private record Interval(long start, long end, BusinessDayOfWeek day) {
    }

    private static final class BusinessDayOfWeekStream {
        private static List<BusinessOperatingWindow> windowsInOrder(Map<BusinessDayOfWeek, BusinessOperatingWindow> map) {
            List<BusinessOperatingWindow> values = new ArrayList<>();
            for (BusinessDayOfWeek day : BusinessDayOfWeek.values()) {
                BusinessOperatingWindow window = map.get(day);
                if (window != null) values.add(window);
            }
            return List.copyOf(values);
        }
    }
}
