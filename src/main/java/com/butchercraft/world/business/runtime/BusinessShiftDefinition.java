package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BusinessShiftDefinition(
        String id,
        String displayName,
        BusinessTimeOfDay start,
        BusinessTimeOfDay end,
        Set<BusinessDayOfWeek> days,
        BusinessShiftIdentity identity
) implements Comparable<BusinessShiftDefinition> {
    public BusinessShiftDefinition(
            String id,
            String displayName,
            BusinessTimeOfDay start,
            BusinessTimeOfDay end,
            Set<BusinessDayOfWeek> days
    ) {
        this(
                BusinessRuntimeValidation.requireShiftId(id),
                BusinessRuntimeValidation.requireText(displayName, "Business shift display name"),
                Objects.requireNonNull(start, "start"),
                Objects.requireNonNull(end, "end"),
                normalizedDays(days),
                BusinessShiftIdentity.fromCanonical(canonical(
                        BusinessRuntimeValidation.requireShiftId(id),
                        BusinessRuntimeValidation.requireText(displayName, "Business shift display name"),
                        start,
                        end,
                        normalizedDays(days)
                ))
        );
    }

    public BusinessShiftDefinition {
        id = BusinessRuntimeValidation.requireShiftId(id);
        displayName = BusinessRuntimeValidation.requireText(displayName, "Business shift display name");
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        days = normalizedDays(days);
        identity = Objects.requireNonNull(identity, "identity");
    }

    public static BusinessShiftDefinition of(
            String id,
            String displayName,
            String start,
            String end,
            Set<BusinessDayOfWeek> days
    ) {
        return new BusinessShiftDefinition(
                id,
                displayName,
                BusinessOperatingWindow.parseTime(start),
                BusinessOperatingWindow.parseTime(end),
                days
        );
    }

    public boolean overnight() {
        return endMinuteOfDay() <= startMinuteOfDay();
    }

    public int startMinuteOfDay() {
        return start.hour() * 60 + start.minute();
    }

    public int endMinuteOfDay() {
        return end.hour() * 60 + end.minute();
    }

    long durationMinutes() {
        int startMinute = startMinuteOfDay();
        int endMinute = endMinuteOfDay();
        return endMinute > startMinute
                ? endMinute - startMinute
                : BusinessOperatingSchedule.MINUTES_PER_DAY - startMinute + endMinute;
    }

    String canonicalLine() {
        return canonical(id, displayName, start, end, days);
    }

    @Override
    public int compareTo(BusinessShiftDefinition other) {
        return id.compareTo(Objects.requireNonNull(other, "other").id);
    }

    private static Set<BusinessDayOfWeek> normalizedDays(Set<BusinessDayOfWeek> source) {
        Objects.requireNonNull(source, "days");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Business shift must apply to at least one day");
        }
        EnumSet<BusinessDayOfWeek> normalized = EnumSet.noneOf(BusinessDayOfWeek.class);
        for (BusinessDayOfWeek day : source) {
            normalized.add(Objects.requireNonNull(day, "day"));
        }
        return Set.copyOf(normalized);
    }

    private static String canonical(
            String id,
            String displayName,
            BusinessTimeOfDay start,
            BusinessTimeOfDay end,
            Set<BusinessDayOfWeek> days
    ) {
        StringBuilder builder = new StringBuilder("schema_version=")
                .append(BusinessRuntimeCalendarSchema.CURRENT_VERSION)
                .append('\n')
                .append("id=")
                .append(id)
                .append('\n')
                .append("display_name=")
                .append(displayName)
                .append('\n')
                .append("start=")
                .append(Objects.requireNonNull(start, "start").displayText())
                .append('\n')
                .append("end=")
                .append(Objects.requireNonNull(end, "end").displayText())
                .append('\n');
        List<BusinessDayOfWeek> orderedDays = days.stream().sorted().toList();
        builder.append("days=");
        for (int index = 0; index < orderedDays.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(orderedDays.get(index).serializedName());
        }
        builder.append('\n');
        return builder.toString();
    }
}
