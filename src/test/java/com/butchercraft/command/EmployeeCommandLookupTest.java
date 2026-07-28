package com.butchercraft.command;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
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
    void suggestionsExposeFriendlyReferencesOnly() {
        EmployeeRecord first = record(0L, "Ada Cutter");
        EmployeeRecord second = record(1L, "Ben");

        List<String> suggestions = ButcherCraftDiagnostics.employeeLookupSuggestions(List.of(first, second));

        assertEquals(List.of("#1", "\"Ada Cutter\"", "#2", "Ben"), suggestions);
        assertFalse(suggestions.contains(first.employeeId().value()));
        assertFalse(suggestions.contains(second.employeeId().value()));
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
