package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BusinessShiftSet {
    private static final int WEEK_DAYS = BusinessDayOfWeek.values().length;

    private final List<BusinessShiftDefinition> shifts;
    private final BusinessShiftSetIdentity identity;
    private final String canonical;

    private BusinessShiftSet(List<BusinessShiftDefinition> shifts, BusinessOperatingSchedule schedule) {
        this.shifts = shifts.stream().sorted().toList();
        rejectDuplicateIds(this.shifts);
        rejectOverlaps(this.shifts);
        validateInsideOperatingHours(this.shifts, Objects.requireNonNull(schedule, "schedule"));
        this.canonical = canonical(this.shifts);
        this.identity = BusinessShiftSetIdentity.fromCanonical(canonical);
    }

    public static BusinessShiftSet of(List<BusinessShiftDefinition> shifts, BusinessOperatingSchedule schedule) {
        return new BusinessShiftSet(List.copyOf(Objects.requireNonNull(shifts, "shifts")), schedule);
    }

    public static BusinessShiftSet defaultShifts(BusinessOperatingSchedule schedule) {
        return of(List.of(
                BusinessShiftDefinition.of("day_shift", "Day Shift", "06:00", "14:30",
                        Set.of(BusinessDayOfWeek.MONDAY, BusinessDayOfWeek.TUESDAY,
                                BusinessDayOfWeek.WEDNESDAY, BusinessDayOfWeek.THURSDAY,
                                BusinessDayOfWeek.FRIDAY)),
                BusinessShiftDefinition.of("evening_shift", "Evening Shift", "14:30", "18:00",
                        Set.of(BusinessDayOfWeek.MONDAY, BusinessDayOfWeek.TUESDAY,
                                BusinessDayOfWeek.WEDNESDAY, BusinessDayOfWeek.THURSDAY,
                                BusinessDayOfWeek.FRIDAY))
        ), schedule);
    }

    public List<BusinessShiftDefinition> shifts() {
        return shifts;
    }

    public BusinessShiftSetIdentity identity() {
        return identity;
    }

    public String canonical() {
        return canonical;
    }

    public Optional<BusinessScheduleBoundary> activeShift(BusinessCalendarSnapshot calendar) {
        long absoluteMinute = BusinessOperatingSchedule.absoluteMinute(calendar);
        return activeShiftAt(absoluteMinute);
    }

    public Optional<BusinessScheduleBoundary> nextShift(BusinessCalendarSnapshot calendar) {
        long current = BusinessOperatingSchedule.absoluteMinute(calendar);
        for (BusinessShiftInterval interval : intervalsAround(calendar.businessDayIndex(), 0, WEEK_DAYS + 1)) {
            if (interval.startAbsoluteMinute() > current) {
                return Optional.of(boundaryFor(interval));
            }
        }
        return Optional.empty();
    }

    private Optional<BusinessScheduleBoundary> activeShiftAt(long absoluteMinute) {
        long dayIndex = Math.floorDiv(absoluteMinute, BusinessOperatingSchedule.MINUTES_PER_DAY);
        return intervalsAround(dayIndex, -1, 0).stream()
                .filter(interval -> absoluteMinute >= interval.startAbsoluteMinute()
                        && absoluteMinute < interval.endExclusiveAbsoluteMinute())
                .findFirst()
                .map(BusinessShiftSet::boundaryFor);
    }

    private List<BusinessShiftInterval> intervalsAround(long baseDay, int firstOffset, int lastOffset) {
        List<BusinessShiftInterval> intervals = new ArrayList<>();
        for (int offset = firstOffset; offset <= lastOffset; offset++) {
            long dayIndex = Math.addExact(baseDay, offset);
            BusinessDayOfWeek day = BusinessDayOfWeek.fromDayIndex(dayIndex);
            for (BusinessShiftDefinition shift : shifts) {
                if (!shift.days().contains(day)) continue;
                long start = dayIndex * BusinessOperatingSchedule.MINUTES_PER_DAY + shift.startMinuteOfDay();
                long end = start + shift.durationMinutes();
                intervals.add(new BusinessShiftInterval(shift, start, end));
            }
        }
        intervals.sort(Comparator
                .comparingLong(BusinessShiftInterval::startAbsoluteMinute)
                .thenComparing(interval -> interval.shift().id()));
        return intervals;
    }

    private static BusinessScheduleBoundary boundaryFor(BusinessShiftInterval interval) {
        return new BusinessScheduleBoundary(
                Math.floorDiv(interval.startAbsoluteMinute(), BusinessOperatingSchedule.MINUTES_PER_DAY),
                BusinessDayOfWeek.fromDayIndex(Math.floorDiv(interval.startAbsoluteMinute(),
                        BusinessOperatingSchedule.MINUTES_PER_DAY)),
                interval.shift().start(),
                Optional.of(interval.shift().identity().value()),
                interval.shift().displayName()
        );
    }

    private static void rejectDuplicateIds(List<BusinessShiftDefinition> shifts) {
        Set<String> seen = new HashSet<>();
        for (BusinessShiftDefinition shift : shifts) {
            if (!seen.add(shift.id())) {
                throw new IllegalArgumentException("Duplicate business shift id: " + shift.id());
            }
        }
    }

    private static void rejectOverlaps(List<BusinessShiftDefinition> shifts) {
        List<BusinessShiftInterval> intervals = sampleWeekIntervals(shifts);
        for (int index = 1; index < intervals.size(); index++) {
            BusinessShiftInterval previous = intervals.get(index - 1);
            BusinessShiftInterval current = intervals.get(index);
            if (current.startAbsoluteMinute() < previous.endExclusiveAbsoluteMinute()) {
                throw new IllegalArgumentException("Business shifts overlap: "
                        + previous.shift().id() + "/" + current.shift().id());
            }
        }
    }

    private static void validateInsideOperatingHours(
            List<BusinessShiftDefinition> shifts,
            BusinessOperatingSchedule schedule
    ) {
        for (BusinessShiftInterval interval : sampleWeekIntervals(shifts)) {
            if (!schedule.containsInterval(interval.startAbsoluteMinute(), interval.endExclusiveAbsoluteMinute())) {
                throw new IllegalArgumentException("Business shift falls outside operating hours: "
                        + interval.shift().id());
            }
        }
    }

    private static List<BusinessShiftInterval> sampleWeekIntervals(List<BusinessShiftDefinition> shifts) {
        List<BusinessShiftInterval> intervals = new ArrayList<>();
        for (int week = 0; week < 2; week++) {
            for (BusinessDayOfWeek day : BusinessDayOfWeek.values()) {
                long dayIndex = (long) week * WEEK_DAYS + day.ordinal();
                for (BusinessShiftDefinition shift : shifts) {
                    if (!shift.days().contains(day)) continue;
                    long start = dayIndex * BusinessOperatingSchedule.MINUTES_PER_DAY + shift.startMinuteOfDay();
                    intervals.add(new BusinessShiftInterval(shift, start, start + shift.durationMinutes()));
                }
            }
        }
        intervals.sort(Comparator
                .comparingLong(BusinessShiftInterval::startAbsoluteMinute)
                .thenComparing(interval -> interval.shift().id()));
        return intervals;
    }

    private static String canonical(List<BusinessShiftDefinition> shifts) {
        StringBuilder builder = new StringBuilder("schema_version=")
                .append(BusinessRuntimeCalendarSchema.CURRENT_VERSION)
                .append('\n')
                .append("ordering=canonical_shift_id\n");
        for (BusinessShiftDefinition shift : shifts) {
            builder.append(shift.canonicalLine());
        }
        return builder.toString();
    }

    private record BusinessShiftInterval(
            BusinessShiftDefinition shift,
            long startAbsoluteMinute,
            long endExclusiveAbsoluteMinute
    ) {
    }
}
