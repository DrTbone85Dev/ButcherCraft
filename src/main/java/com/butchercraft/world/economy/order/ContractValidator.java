package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodDefinition;

import java.util.Objects;

public final class ContractValidator {
    private final EconomicActorRegistry actorRegistry;

    public ContractValidator(EconomicActorRegistry actorRegistry) {
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
    }

    public ContractOperationResult validate(EconomicContractDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!actorRegistry.contains(definition.principalActorId())) {
            return ContractOperationResult.rejected(ContractFailureCode.UNKNOWN_ACTOR,
                    "Unknown principal actor: " + definition.principalActorId().value());
        }
        if (!actorRegistry.contains(definition.counterpartyActorId())) {
            return ContractOperationResult.rejected(ContractFailureCode.UNKNOWN_ACTOR,
                    "Unknown counterparty actor: " + definition.counterpartyActorId().value());
        }
        if (definition.principalActorId().equals(definition.counterpartyActorId())
                && definition.type() != ContractType.INTERNAL) {
            return ContractOperationResult.rejected(ContractFailureCode.INVALID_PARTIES,
                    "Only internal contracts may use the same party on both sides");
        }
        if (!actorRegistry.knownIndustries().containsAll(definition.supportedIndustries())) {
            return ContractOperationResult.rejected(ContractFailureCode.VALIDATION_FAILED,
                    "Contract references an unknown industry");
        }
        for (ContractLineDefinition line : definition.lines()) {
            GoodDefinition good = actorRegistry.goodRegistry().find(line.goodId()).orElse(null);
            if (good == null) {
                return ContractOperationResult.rejected(ContractFailureCode.UNKNOWN_GOOD,
                        "Unknown Good: " + line.goodId().value());
            }
            if (good.unitOfMeasure() != line.unitOfMeasure()) {
                return ContractOperationResult.rejected(ContractFailureCode.UNIT_MISMATCH,
                        "Contract line unit does not match Good: " + line.id().value());
            }
            if (!definition.supportedIndustries().isEmpty()
                    && !definition.supportedIndustries().contains(good.industryId())) {
                return ContractOperationResult.rejected(ContractFailureCode.VALIDATION_FAILED,
                        "Contract line Good is outside the declared industry scope: " + line.id().value());
            }
        }
        return ContractOperationResult.accepted();
    }
}
