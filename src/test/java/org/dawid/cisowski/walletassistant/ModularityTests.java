package org.dawid.cisowski.walletassistant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(WalletAssistantApplication.class);

    @Test
    void verifyModularStructure() {
        modules.verify();
    }

    @Test
    void shouldDetectAllExpectedModules() {
        var expectedModules = Set.of(
                "walletevents", "expenses", "accounts", "assets",
                "mcp", "config", "security"
        );

        var actualModules = modules.stream()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(actualModules).containsAll(expectedModules);
    }

    @Test
    void shouldHaveNoViolations() {
        var violations = modules.detectViolations();
        violations.throwIfPresent();
        assertThat(true).isTrue();
    }

    @Test
    void walletEventsShouldNotDependOnProjectionModules() {
        var projectionModules = Set.of("expenses", "accounts", "assets");

        var module = modules.getModuleByName("walletevents").orElseThrow();
        var dependencies = module.getBootstrapDependencies(modules)
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(dependencies)
                .as("walletevents should not depend on projection modules")
                .doesNotContainAnyElementsOf(projectionModules);
    }

    @Test
    void modulesShouldHaveProperlyNamedFacades() {
        var modulesWithFacades = Map.of(
                "walletevents", "WalletEventsFacade",
                "expenses", "ExpensesFacade",
                "accounts", "AccountsFacade",
                "assets", "AssetsFacade"
        );

        modulesWithFacades.forEach((moduleName, facadeName) -> {
            String fullClassName = "org.dawid.cisowski.walletassistant.%s.api.%s"
                    .formatted(moduleName, facadeName);
            assertThatCode(() -> Class.forName(fullClassName))
                    .as("Module %s should have facade %s", moduleName, facadeName)
                    .doesNotThrowAnyException();
        });
    }
}
