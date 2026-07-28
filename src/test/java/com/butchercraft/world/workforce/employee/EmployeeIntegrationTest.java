package com.butchercraft.world.workforce.employee;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeIntegrationTest {
    @Test
    void modRegistersEmployeeLifecycleAndEntityType() throws IOException {
        String butchercraft = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        String entityTypes = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/registration/ModEntityTypes.java"
        ));

        assertTrue(butchercraft.contains("EmployeeService.INSTANCE::initialize"));
        assertTrue(butchercraft.contains("EmployeeService.INSTANCE::save"));
        assertTrue(butchercraft.contains("ModEntityTypes.register(modEventBus)"));
        assertTrue(entityTypes.contains("EMPLOYEE"));
        assertTrue(entityTypes.contains("butchercraft"));
    }

    @Test
    void employeeServiceUsesDedicatedPersistenceFileAndBusinessRuntimeCalendar() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/EmployeeService.java"
        ));

        assertTrue(source.contains("EmployeeSchema.FILE_NAME"));
        assertTrue(source.contains("BusinessRuntimeCalendarService"));
        assertTrue(source.contains("WorkforceService"));
        assertTrue(source.contains("LevelResource.ROOT"));
    }

    @Test
    void diagnosticsExposeEmployeeCommandsWithoutProductionAutomation() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/command/ButcherCraftDiagnostics.java"
        ));

        assertTrue(source.contains("Commands.literal(\"employee\")"));
        assertTrue(source.contains("Commands.literal(\"create\")"));
        assertTrue(source.contains("Commands.literal(\"list\")"));
        assertTrue(source.contains("Commands.literal(\"status\")"));
        assertTrue(source.contains("Commands.literal(\"set-shift\")"));
        assertTrue(source.contains("Commands.literal(\"set-presence\")"));
        assertTrue(source.contains("Commands.argument(EMPLOYEE_ARGUMENT, StringArgumentType.string())"));
        assertTrue(source.contains("EMPLOYEE_LOOKUP_SUGGESTIONS"));
        assertTrue(source.contains("resolveEmployeeReference"));
    }

    @Test
    void employeeInspectionTranslationShipsInGeneratedAndRuntimeLanguageResources() throws IOException {
        String provider = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/data/ButcherCraftLanguageProvider.java"
        ));
        String generatedLanguage = Files.readString(TestProjectPaths.projectPath(
                "src/generated/resources/assets/butchercraft/lang/en_us.json"
        ));
        String runtimeLanguage = Files.readString(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/lang/en_us.json"
        ));

        String key = "entity.butchercraft.employee.inspect";
        String translated = "Employee: %s | Status: %s | Presence: %s | Shift: %s";
        assertTrue(provider.contains(key));
        assertTrue(provider.contains(translated));
        assertTrue(generatedLanguage.contains("\"" + key + "\": \"" + translated + "\""));
        assertTrue(runtimeLanguage.contains("\"" + key + "\": \"" + translated + "\""));
    }
}
