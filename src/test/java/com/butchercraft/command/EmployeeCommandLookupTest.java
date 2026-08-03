package com.butchercraft.command;

import com.butchercraft.integration.employee.EmployeeWorkstationOperationService;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeCommandLookupTest {
    @Test
    void resolvesEmployeeByFriendlyNumber() {
        EmployeeRecord first = record(0L, "Ada Cutter");
        EmployeeRecord second = record(1L, "Ben Butcher");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference("#2", List.of(first, second));

        assertTrue(result.resolved());
        assertEquals(second.employeeId(), result.record().orElseThrow().employeeId());
    }

    @Test
    void resolvesEmployeeByPlainNumber() {
        EmployeeRecord record = record(0L, "Ada Cutter");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference("1", List.of(record));

        assertTrue(result.resolved());
        assertEquals(record.employeeId(), result.record().orElseThrow().employeeId());
    }

    @Test
    void resolvesEmployeeByUniqueDisplayName() {
        EmployeeRecord record = record(0L, "Ada Cutter");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference("ada cutter", List.of(record));

        assertTrue(result.resolved());
        assertEquals(record.employeeId(), result.record().orElseThrow().employeeId());
    }

    @Test
    void resolvesEmployeeByQuotedDisplayName() {
        EmployeeRecord record = record(0L, "Ada Cutter");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference("\"Ada Cutter\"", List.of(record));

        assertTrue(result.resolved());
        assertEquals(record.employeeId(), result.record().orElseThrow().employeeId());
    }

    @Test
    void resolvesEmployeeByCanonicalId() {
        EmployeeRecord record = record(0L, "Ada Cutter");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference(record.employeeId().value(), List.of(record));

        assertTrue(result.resolved());
        assertEquals(record.employeeId(), result.record().orElseThrow().employeeId());
    }

    @Test
    void duplicateDisplayNamesRequireNumberDisambiguation() {
        EmployeeRecord first = record(0L, "Ada Cutter");
        EmployeeRecord second = record(1L, "Ada Cutter");

        ButcherCraftDiagnostics.EmployeeLookupResult result =
                ButcherCraftDiagnostics.resolveEmployeeReference("Ada Cutter", List.of(first, second));

        assertFalse(result.resolved());
        assertEquals(Optional.of(ButcherCraftDiagnostics.EmployeeLookupFailure.AMBIGUOUS), result.failure());
        assertEquals(List.of(first, second), result.matches());
    }

    @Test
    void suggestionsExposeExecutableEmployeeReferences() {
        EmployeeRecord first = record(0L, "Ada Cutter");
        EmployeeRecord second = record(1L, "Ben");

        List<String> suggestions = ButcherCraftDiagnostics.employeeLookupSuggestions(List.of(first, second));

        assertEquals(List.of(
                "#1",
                "\"Ada Cutter\"",
                first.employeeId().value(),
                "#2",
                "Ben",
                second.employeeId().value()
        ), suggestions);
        for (String suggestion : suggestions) {
            assertTrue(ButcherCraftDiagnostics.resolveEmployeeReference(suggestion, List.of(first, second))
                    .resolved(), "Suggestion is executable: " + suggestion);
        }
    }

    @Test
    void duplicateDisplayNamesAreNotSuggestedAsExecutableNames() {
        EmployeeRecord first = record(0L, "Ada Cutter");
        EmployeeRecord second = record(1L, "Ada Cutter");

        List<String> suggestions = ButcherCraftDiagnostics.employeeLookupSuggestions(List.of(first, second));

        assertEquals(List.of("#1", first.employeeId().value(), "#2", second.employeeId().value()), suggestions);
    }

    @Test
    void parsesAbsoluteWorkstationPositionForGreedyEmployeeTailCommands() {
        assertEquals(Optional.of(new BlockPos(12, 64, -3)),
                ButcherCraftDiagnostics.parseWorkstationPosition("12 64 -3"));
    }

    @Test
    void rejectsWorkstationPositionWithTrailingData() {
        assertEquals(Optional.empty(),
                ButcherCraftDiagnostics.parseWorkstationPosition("12 64 -3 extra"));
    }

    @Test
    void operationFeedbackNamesEveryCommandOutcome() {
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.ACCEPTED,
                "Employee operation accepted");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.EMPLOYEE_NOT_PRESENT,
                "Employee not present");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.EMPLOYEE_NOT_AT_WORKSTATION,
                "Employee not at workstation");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.RESERVATION_MISSING_OR_INVALID,
                "Reservation missing or invalid");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.UNSUPPORTED_WORKSTATION,
                "Unsupported workstation");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.MISSING_INPUT,
                "Missing input");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.INVALID_RECIPE,
                "Invalid recipe");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.BLOCKED_OUTPUT,
                "Blocked output");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.ALREADY_REQUESTED,
                "Operation already requested");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.EXECUTION_REJECTED,
                "Execution rejected");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.UNKNOWN_OUTCOME,
                "Unknown Outcome; recovery required");
        assertOperationFeedback(EmployeeWorkstationOperationService.RequestStatus.RECOVERY_REQUIRED,
                "Recovery required");
    }

    private static void assertOperationFeedback(
            EmployeeWorkstationOperationService.RequestStatus status,
            String expectedPrefix
    ) {
        String feedback = ButcherCraftDiagnostics.employeeOperationFeedback(
                new EmployeeWorkstationOperationService.RequestResult(status, "test detail")
        );
        assertTrue(feedback.startsWith(expectedPrefix + ":"), feedback);
    }

    private static EmployeeRecord record(long sequence, String displayName) {
        return new EmployeeRecord(
                EmployeeSchema.CURRENT_VERSION,
                new EmployeeId("butchercraft:employee/test/" + sequence),
                new BusinessId("test_business"),
                sequence,
                "butchercraft:world_identity_root/test",
                "sha256:" + "a".repeat(64),
                displayName,
                Optional.empty(),
                EmployeeStatus.ACTIVE,
                EmployeePresenceState.OFF_SHIFT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                new BusinessTimeOfDay(7, 0),
                "butchercraft:world_day/test",
                Optional.empty(),
                Optional.empty(),
                1L,
                "butchercraft:employee_creation/test",
                "butchercraft:business_runtime/test"
        );
    }
}
