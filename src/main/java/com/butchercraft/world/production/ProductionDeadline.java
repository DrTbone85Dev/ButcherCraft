package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessOperatingSchedule;
import com.butchercraft.world.business.runtime.BusinessRuntimeConfigurationIdentity;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.Objects;
import java.util.Optional;

public record ProductionDeadline(
        int schemaVersion,
        ProductionDeadlineIdentity identity,
        ProductionRunId runId,
        ProductionDeadlineType type,
        long businessDayIndex,
        BusinessTimeOfDay businessTime,
        BusinessRuntimeConfigurationIdentity businessRuntimeConfigurationIdentity,
        String sourceWorldDayIdentity,
        String sourceDimensionIdentity,
        String sourceIdentity,
        ProductionDeadlineStatus status,
        boolean locked,
        Optional<ProductionDeadlineCompletionTiming> completionTiming,
        Optional<String> evaluatedWorldDayIdentity
) {
    public ProductionDeadline {
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production deadline");
        identity = Objects.requireNonNull(identity, "identity");
        runId = Objects.requireNonNull(runId, "runId");
        type = Objects.requireNonNull(type, "type");
        businessTime = Objects.requireNonNull(businessTime, "businessTime");
        businessRuntimeConfigurationIdentity = Objects.requireNonNull(
                businessRuntimeConfigurationIdentity,
                "businessRuntimeConfigurationIdentity"
        );
        sourceWorldDayIdentity = ProductionValidation.requireText(
                sourceWorldDayIdentity,
                "Production deadline source world-day identity",
                256
        );
        sourceDimensionIdentity = ProductionValidation.requireExternalIdentity(
                sourceDimensionIdentity,
                "Production deadline source dimension identity"
        );
        sourceIdentity = ProductionValidation.requireExternalIdentity(sourceIdentity, "Production deadline source");
        status = Objects.requireNonNull(status, "status");
        completionTiming = Objects.requireNonNull(completionTiming, "completionTiming");
        evaluatedWorldDayIdentity = Objects.requireNonNull(evaluatedWorldDayIdentity, "evaluatedWorldDayIdentity")
                .map(value -> ProductionValidation.requireText(value,
                        "Production deadline evaluated world-day identity",
                        256));
        if (status == ProductionDeadlineStatus.NO_DEADLINE) {
            throw new IllegalArgumentException("NO_DEADLINE is represented by an absent Production deadline");
        }
        if (completionTiming.isPresent() != status.terminalCompletion()) {
            throw new IllegalArgumentException("Production deadline completion timing must match terminal status");
        }
        ProductionDeadlineIdentity expected = ProductionDeadlineIdentity.from(
                runId,
                type,
                businessDayIndex,
                businessTime,
                businessRuntimeConfigurationIdentity,
                sourceWorldDayIdentity,
                sourceDimensionIdentity,
                sourceIdentity
        );
        if (!identity.equals(expected)) {
            throw new IllegalArgumentException("Production deadline identity does not match canonical content");
        }
    }

    public static ProductionDeadline target(
            ProductionRunId runId,
            BusinessCalendarSnapshot creationCalendar,
            BusinessRuntimeConfigurationIdentity configurationIdentity,
            int offsetBusinessMinutes,
            String sourceIdentity
    ) {
        if (offsetBusinessMinutes < 0) {
            throw new IllegalArgumentException("Production deadline offset must not be negative");
        }
        long dueAbsoluteMinute = Math.addExact(
                BusinessOperatingSchedule.absoluteMinute(creationCalendar),
                offsetBusinessMinutes
        );
        long day = Math.floorDiv(dueAbsoluteMinute, BusinessOperatingSchedule.MINUTES_PER_DAY);
        int minute = (int) Math.floorMod(dueAbsoluteMinute, BusinessOperatingSchedule.MINUTES_PER_DAY);
        BusinessTimeOfDay time = new BusinessTimeOfDay(minute / 60, minute % 60);
        ProductionDeadlineIdentity identity = ProductionDeadlineIdentity.from(
                runId,
                ProductionDeadlineType.TARGET,
                day,
                time,
                configurationIdentity,
                creationCalendar.worldDayIdentity(),
                creationCalendar.sourceDimensionIdentity(),
                sourceIdentity
        );
        return new ProductionDeadline(
                ProductionSchema.CURRENT_VERSION,
                identity,
                runId,
                ProductionDeadlineType.TARGET,
                day,
                time,
                configurationIdentity,
                creationCalendar.worldDayIdentity(),
                creationCalendar.sourceDimensionIdentity(),
                sourceIdentity,
                ProductionDeadlineStatus.UPCOMING,
                false,
                Optional.empty(),
                Optional.of(creationCalendar.worldDayIdentity())
        ).evaluate(ProductionRunStatus.PLANNED, creationCalendar);
    }

    public ProductionDeadline withLocked() {
        if (locked) {
            return this;
        }
        return copy(status, true, completionTiming, evaluatedWorldDayIdentity);
    }

    public ProductionDeadline evaluate(ProductionRunStatus runStatus, BusinessCalendarSnapshot calendar) {
        Objects.requireNonNull(runStatus, "runStatus");
        Objects.requireNonNull(calendar, "calendar");
        if (completionTiming.isPresent()) {
            return this;
        }
        if (runStatus == ProductionRunStatus.CANCELLED) {
            return copy(ProductionDeadlineStatus.CANCELLED, true, Optional.empty(),
                    Optional.of(calendar.worldDayIdentity()));
        }
        if (runStatus == ProductionRunStatus.COMPLETED) {
            long current = BusinessOperatingSchedule.absoluteMinute(calendar);
            long due = dueAbsoluteMinute();
            ProductionDeadlineCompletionTiming timing;
            ProductionDeadlineStatus nextStatus;
            if (current < due) {
                timing = ProductionDeadlineCompletionTiming.EARLY;
                nextStatus = ProductionDeadlineStatus.COMPLETED_EARLY;
            } else if (current == due) {
                timing = ProductionDeadlineCompletionTiming.ON_TIME;
                nextStatus = ProductionDeadlineStatus.COMPLETED_ON_TIME;
            } else {
                timing = ProductionDeadlineCompletionTiming.LATE;
                nextStatus = ProductionDeadlineStatus.COMPLETED_LATE;
            }
            return copy(nextStatus, true, Optional.of(timing), Optional.of(calendar.worldDayIdentity()));
        }
        if (runStatus.isTerminal()) {
            return this;
        }
        long current = BusinessOperatingSchedule.absoluteMinute(calendar);
        long due = dueAbsoluteMinute();
        ProductionDeadlineStatus nextStatus = current < due
                ? ProductionDeadlineStatus.UPCOMING
                : current == due ? ProductionDeadlineStatus.DUE_NOW : ProductionDeadlineStatus.OVERDUE;
        return copy(nextStatus, locked, Optional.empty(), Optional.of(calendar.worldDayIdentity()));
    }

    public long dueAbsoluteMinute() {
        return Math.addExact(
                Math.multiplyExact(businessDayIndex, BusinessOperatingSchedule.MINUTES_PER_DAY),
                businessTime.hour() * 60L + businessTime.minute()
        );
    }

    public BusinessDayOfWeek dayOfWeek() {
        return BusinessDayOfWeek.fromDayIndex(businessDayIndex);
    }

    public String displayTime() {
        return dayOfWeek().displayName() + " " + businessTime.displayText();
    }

    public boolean sameAssignment(ProductionDeadline other) {
        return other != null
                && identity.equals(other.identity)
                && runId.equals(other.runId)
                && type == other.type
                && businessDayIndex == other.businessDayIndex
                && businessTime.equals(other.businessTime)
                && businessRuntimeConfigurationIdentity.equals(other.businessRuntimeConfigurationIdentity)
                && sourceIdentity.equals(other.sourceIdentity);
    }

    private ProductionDeadline copy(
            ProductionDeadlineStatus nextStatus,
            boolean nextLocked,
            Optional<ProductionDeadlineCompletionTiming> nextTiming,
            Optional<String> nextEvaluatedWorldDayIdentity
    ) {
        return new ProductionDeadline(
                schemaVersion,
                identity,
                runId,
                type,
                businessDayIndex,
                businessTime,
                businessRuntimeConfigurationIdentity,
                sourceWorldDayIdentity,
                sourceDimensionIdentity,
                sourceIdentity,
                nextStatus,
                nextLocked,
                nextTiming,
                nextEvaluatedWorldDayIdentity
        );
    }
}
