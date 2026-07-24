# RFC-0023: Deterministic Execution Engine

Status: Architecture Specification

Revision: Draft 1

Governing authority: [`CONSTITUTION.md`](../CONSTITUTION.md)

This RFC defines the industry-neutral framework that performs approved,
resource-authorized work.

No implementation is authorized until the complete RFC has been reviewed and
approved.

## Part I: Philosophy, Authority, And First Principles

### 1. Purpose

The Deterministic Execution Engine exists to answer one question:

> Given an executable work definition and complete active resource
> authorization, how does that work progress toward completion?

Execution does not decide whether work should happen.

Execution does not decide whether resources are available.

Execution performs already-approved and already-authorized work.

### 2. Architectural Position

```text
Observation
  -> Planning
      -> Domain-Owned Executable Work Definition
          -> Allocation
              -> Execution
                  -> Transactions
                      -> Authoritative State
```

Planning decides.

Allocation commits scarce execution capacity.

Execution performs work.

Transactions apply authoritative state changes.

Authoritative subsystems own the resulting state.

### 3. Execution Definition

Execution is the deterministic progression of an executable work instance
through time.

Execution begins only after all required authorization conditions are
satisfied.

Execution ends through exactly one terminal outcome:

- `COMPLETED`
- `FAILED`
- `CANCELLED`

Execution never implies successful state mutation unless the required
Transaction has been applied successfully.

### 4. Execution Is Not Planning

Planning answers:

> What should happen?

Execution answers:

> What is currently happening?

Execution shall never:

- create Needs;
- create Opportunities;
- rank Candidate Plans;
- approve Plans;
- change Planning priority;
- modify Planning fairness;
- replan failed work.

Planning remains externally authoritative for decisions.

### 5. Execution Is Not Allocation

Allocation answers:

> Which scarce Capacity is committed?

Execution answers:

> How does authorized work progress?

Execution shall never:

- search for Resources;
- select Resources;
- reserve Capacity;
- create AllocationSets;
- create Commitments;
- replace missing Commitments;
- extend expired Commitments.

Allocation remains externally authoritative for resource authorization.

### 6. Execution Is Not Transactions

Execution may request or construct a proposed state transition through an
approved domain adapter.

Execution shall never directly mutate authoritative state.

Execution shall never directly mutate Inventory quantities.

Execution shall never bypass Transaction validation or execution.

Execution completion and Transaction application remain distinct facts.

### 7. Domain Ownership

Executable work definitions remain owned by their authoritative domain
subsystems.

Examples:

- Production owns `ProductionPlanDefinition`.
- Future Logistics owns `TransportWorkDefinition`.
- Future Maintenance owns `MaintenanceWorkDefinition`.
- Future Utilities owns `UtilityWorkDefinition`.

Execution never becomes authoritative for domain work definitions.

Execution owns only generic execution runtime and execution evidence.

### 8. Execution Ownership

Execution owns:

- Execution instances;
- Execution lifecycle;
- Execution progress;
- Execution attempts;
- Execution failure evidence;
- Execution cancellation evidence;
- Execution completion evidence;
- Execution reports;
- Execution history;
- Execution traces.

Execution does not own:

- Plans;
- Allocation Commitments;
- Resources;
- Capacity;
- Transactions;
- Inventory;
- Goods;
- Businesses;
- Workers;
- Machines;
- Vehicles;
- Utilities.

### 9. Execution Authorization

Execution may begin only when every required authorization condition is
satisfied.

Schema-1 authorization requires at minimum:

- valid executable work reference;
- valid Execution instance;
- complete AllocationSet association;
- required Commitments present;
- required Commitments `ACTIVE`;
- Commitments compatible with executable work;
- no terminal Execution state;
- valid authoritative simulation tick.

Future authorization conditions remain additive.

### 10. Commitment Gate

AllocationCommitments grant permission to begin or continue work.

Execution consumes Commitments as immutable authorization evidence.

Execution does not mutate Commitment definitions.

Execution does not activate Commitments unless a later approved integration
decision explicitly assigns activation authority to Execution.

Execution does not release Commitments directly.

Execution reports lifecycle outcomes to the approved Allocation integration
boundary.

### 11. Execution Instance

An Execution Instance represents one runtime attempt to perform one
domain-owned executable work definition.

Each Execution Instance has stable identity.

The executable work definition remains external.

The Execution Instance references it.

Execution Instances are not reusable after reaching a terminal state.

A retry creates a new Execution attempt or follows the exact retry model defined
later in this RFC.

### 12. Determinism

Given identical:

- executable work definition reference;
- Execution runtime state;
- Allocation authorization;
- simulation tick sequence;
- Scheduler ordering;
- domain execution adapter behavior;
- Transaction results;

Execution shall produce identical:

- lifecycle transitions;
- progress;
- attempts;
- failures;
- completion evidence;
- reports;
- history;
- trace.

No randomness is permitted unless an external domain explicitly supplies a
deterministic random input through an approved contract.

Schema 1 introduces no random execution.

### 13. Time Authority

Execution owns no clock.

Execution reads authoritative simulation time supplied by the Scheduler or
Execution invocation context.

Execution shall never read:

- system time;
- wall-clock time;
- real-time elapsed duration;
- client render time;
- frame time.

Execution progress is measured only through authoritative simulation units.

### 14. Scheduler Authority

Scheduler remains the sole authority for determining when Execution work runs.

Execution does not create its own loop.

Execution does not advance itself.

Execution does not recursively invoke itself.

One Scheduler invocation produces at most one bounded Execution step for one
Execution Instance unless a later accepted batching rule states otherwise.

### 15. Bounded Progress

Execution proceeds through bounded deterministic steps.

A single Execution invocation shall never perform unbounded work.

Every Execution step must have explicit limits.

Execution that requires additional progress returns a nonterminal runtime state
for a future Scheduler invocation.

No hidden loops.

No run-until-complete behavior unless the work is provably bounded within the
approved step budget.

### 16. Progress

Progress represents deterministic advancement toward a domain-defined
completion condition.

Progress may be represented through:

- completed work units;
- remaining work units;
- domain-neutral exact progress quantity;
- discrete execution phase;
- another approved exact representation.

Progress shall never use floating-point arithmetic.

Progress must be monotonic unless an explicitly approved domain model permits a
reversible phase.

Schema 1 assumes monotonic progress.

### 17. Execution Adapter

Execution requires an industry-neutral adapter boundary.

The generic Execution Engine does not understand:

- Grinding;
- Transporting;
- Inspecting;
- Repairing;
- Cooking;
- Packaging;
- Generating power.

Each domain supplies an Execution Adapter that translates its executable work
definition into deterministic Execution behavior.

Adapters remain owned by the domain or an integration package.

Core Execution shall not depend directly on concrete Production, Logistics,
Utilities, Maintenance, or gameplay classes.

### 18. Adapter Responsibilities

A domain Execution Adapter may:

- validate domain work eligibility;
- describe required deterministic progress;
- evaluate one bounded Execution step;
- produce immutable proposed domain outcome evidence;
- produce an approved Transaction proposal when completion requires state
  mutation;
- produce typed domain failure evidence.

A domain Execution Adapter shall not:

- mutate authoritative state directly;
- allocate Resources;
- schedule itself;
- read wall-clock time;
- use randomness;
- bypass Transactions;
- modify Execution runtime directly.

### 19. Execution Lifecycle

Schema-1 lifecycle shall include the following conceptual states:

- `CREATED`
- `READY`
- `RUNNING`
- `WAITING`
- `COMPLETING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

Exact state names and transitions are defined in later Parts.

Terminal states:

- `COMPLETED`
- `FAILED`
- `CANCELLED`

Terminal states never transition.

### 20. Waiting

`WAITING` represents valid work that cannot advance during the current
Execution step.

Possible causes include:

- authorization temporarily unavailable;
- required domain condition absent;
- Transaction result pending;
- Scheduler dependency incomplete;
- another explicitly modeled deterministic condition.

`WAITING` is not failure.

Execution never busy-waits.

Execution never retries internally.

A later Scheduler invocation may reevaluate waiting work.

### 21. Failure

Failure is explicit and typed.

Expected failures shall not escape as generic exceptions.

Failure evidence must distinguish:

- invalid Execution state;
- invalid authorization;
- domain execution failure;
- Transaction rejection;
- Transaction execution failure;
- unsupported work definition;
- budget exhaustion;
- reference failure;
- adapter failure;
- internal invariant failure.

Failure must remain deterministic and replayable.

### 22. Cancellation

Cancellation is an explicit externally requested lifecycle event.

Execution does not invent cancellation.

Cancellation authority belongs to an approved caller, such as:

- Planning integration;
- domain owner;
- Business Runtime;
- administrator command in a future gameplay layer.

Cancellation does not directly release Resources or reverse Transactions.

Those effects occur through their authoritative subsystems.

### 23. Completion

Execution completion requires all approved completion conditions.

For work that changes authoritative state, Schema 1 requires:

- domain progress complete;
- proposed Transaction valid;
- Transaction applied successfully;
- completion evidence published.

Only then may Execution become `COMPLETED`.

Progress completion without successful Transaction application is not final
Execution completion.

### 24. Transaction Boundary

Execution may produce a proposed Transaction through a domain adapter.

The Transaction framework remains responsible for:

- validation;
- atomic execution;
- mutation;
- history;
- replay.

Execution observes the resulting Transaction status.

Execution shall not duplicate Transaction logic.

### 25. Atomic Completion

Execution publication shall be atomic.

A terminal completion result shall not become visible unless:

- Execution runtime transition;
- completion evidence;
- Transaction reference;
- report;
- history;
- trace;

are mutually consistent.

Partial completion publication is prohibited.

### 26. Attempts

Execution attempts must be explicit.

A failed attempt shall never be silently rewritten as though it did not occur.

Retries shall preserve prior attempt evidence.

Schema-1 retry policy is defined later in this RFC.

Execution does not automatically retry unless an accepted policy explicitly
authorizes it.

### 27. Replay

Execution shall support deterministic replay.

Replay requires explicit input state and authoritative external results.

Execution replay shall not query live provider state.

Execution replay shall not resubmit Transactions unless the replay mode
explicitly uses an isolated deterministic Transaction environment.

Reports and traces shall allow comparison through canonical digests.

### 28. Living World Principle

Execution must remain fully functional without players.

Factories continue working.

Transport continues progressing.

Maintenance continues.

Utilities continue.

Transactions continue.

The absence of a connected player shall have no effect on Execution behavior.

### 29. Industry Neutrality

Core Execution shall not contain:

- meat-processing logic;
- Minecraft blocks;
- Minecraft entities;
- villager logic;
- recipes;
- Production-specific progress rules;
- Transport-specific routing;
- Utility-specific dispatch;
- Maintenance-specific repair logic.

All domain semantics enter through explicit adapters and immutable contracts.

### 30. Purity

The deterministic Execution decision step should remain pure wherever
practical.

Conceptually:

```text
Execution Input
  -> Execution Evaluation
      -> Proposed Execution Result
          -> Validation
              -> Atomic Publication
```

No authoritative runtime mutation occurs during evaluation.

No authoritative state mutation occurs outside Transactions.

### 31. Execution Pipeline

The canonical conceptual pipeline is:

```text
Capture Input
  -> Validate Execution Envelope
      -> Validate Authorization
          -> Resolve Domain Adapter
              -> Evaluate One Bounded Step
                  -> Validate Proposed Result
                      -> Submit or Observe Transaction
                          -> Construct Runtime Transition
                              -> Atomic Publication
                                  -> Report and Trace
```

Detailed stage semantics are defined in later Parts.

### 32. No Hidden Work

Execution shall never perform undeclared secondary work.

One Execution Instance advances only its referenced executable work.

Any new dependent work must be represented explicitly through:

- Planning;
- domain work creation;
- Scheduler Work;
- another approved subsystem.

Execution does not silently spawn work.

### 33. No Resource Ownership Transfer

Execution using a Resource does not become its owner.

Execution references Allocation authorization.

Resource ownership remains with the provider subsystem.

Execution termination does not destroy the Resource.

Execution completion does not automatically release Capacity without the
approved Allocation lifecycle handoff.

### 34. Observability

Every Execution step shall produce sufficient immutable evidence to explain:

- what was evaluated;
- why it advanced;
- why it waited;
- why it failed;
- what Transaction was proposed;
- what Transaction result was observed;
- what runtime transition occurred.

No opaque success or failure.

### 35. Public Stability

Execution contracts shall be designed for future domains without requiring Core
changes.

Future domains shall integrate through:

- stable identifiers;
- generic executable-work references;
- Execution adapters;
- exact progress contracts;
- typed outcomes;
- Transactions.

Future domain support must remain additive.

### 36. First Principles

Planning decides.

Allocation authorizes scarce Capacity.

Scheduler determines execution order.

Execution performs bounded work.

Domain adapters define work semantics.

Transactions apply authoritative mutations.

Authoritative subsystems own resulting state.

No layer assumes another layer's authority.

### 37. Execution Invariants

`EX-0001`

Execution never plans.

`EX-0002`

Execution never allocates.

`EX-0003`

Execution never directly mutates authoritative state.

`EX-0004`

Execution requires complete approved authorization.

`EX-0005`

Execution owns runtime only.

`EX-0006`

Domain subsystems retain executable-work ownership.

`EX-0007`

Scheduler remains execution-order authority.

`EX-0008`

Transactions remain mutation authority.

`EX-0009`

Execution steps remain bounded.

`EX-0010`

Execution remains deterministic.

`EX-0011`

Terminal states never transition.

`EX-0012`

Expected failures remain typed.

`EX-0013`

Completion publication remains atomic.

`EX-0014`

Execution reads no wall clock.

`EX-0015`

Execution contains no industry-specific semantics.

`EX-0016`

Evaluation does not mutate authoritative runtime.

`EX-0017`

One Execution Instance represents one explicit work attempt.

`EX-0018`

Retries preserve attempt history.

`EX-0019`

Progress without successful required mutation is not completion.

`EX-0020`

Execution remains autonomous without players.

### 38. End Part I

Part II defines:

- Execution Domain Model;
- Execution identities;
- executable work references;
- Execution definitions;
- Execution attempts;
- progress models;
- authorization evidence;
- runtime and result contracts.

## Part II: Execution Domain Model

This Part defines the immutable vocabulary used by the Execution Engine.

Execution runtime is defined later.

No implementation is authorized.

### 39. Domain Philosophy

Execution models work.

Execution does not model planning.

Execution does not model allocation.

Execution does not model authoritative state mutation.

Execution models the deterministic progression of one authorized work
instance.

### 40. Primary Domain Objects

Execution consists of the following first-class concepts:

- `ExecutionInstance`;
- `ExecutionAttempt`;
- `ExecutionProgress`;
- `ExecutionAuthorization`;
- `ExecutionContext`;
- `ExecutionStep`;
- `ExecutionStepResult`;
- `ExecutionCompletion`;
- `ExecutionFailure`;
- `ExecutionCancellation`;
- `ExecutionTrace`;
- `ExecutionSummary`;
- `ExecutionReport`;
- `ExecutionOutcome`.

Schema-1 introduces no additional execution concepts.

### 41. ExecutionInstance

`ExecutionInstance` represents one runtime instance of one executable work
definition.

`ExecutionInstance` owns runtime only.

Executable work remains externally authoritative.

### 42. ExecutionInstance Identity

Implement `ExecutionInstanceId`.

Requirements:

- immutable;
- canonical;
- replay stable;
- persistable;
- value equality;
- namespace validated;
- no random UUID;
- no clock-derived identity.

### 43. Executable Work Reference

Execution references executable work.

Execution never owns executable work.

Examples:

- `ProductionPlanDefinition`;
- future `TransportWorkDefinition`;
- future `MaintenanceWorkDefinition`;
- future `UtilityWorkDefinition`.

The reference shall remain industry neutral.

### 44. ExecutionAuthorization

`ExecutionAuthorization` represents immutable authorization evidence.

Authorization is produced externally.

Execution consumes authorization.

Execution never creates authorization.

Execution never repairs authorization.

`ExecutionAuthorization` references:

- AllocationSet;
- AllocationCommitments;
- Simulation Tick;
- required evidence;
- schema version.

### 45. ExecutionContext

`ExecutionContext` contains every deterministic fact required for one Execution
step.

`ExecutionContext` is immutable.

`ExecutionContext` contains:

- Simulation Tick;
- ExecutionInstance reference;
- authorization;
- domain adapter reference;
- runtime snapshot;
- configuration;
- schema version.

`ExecutionContext` shall never query live state.

### 46. ExecutionAttempt

`ExecutionAttempt` represents one bounded attempt to advance one
`ExecutionInstance`.

An immutable `ExecutionAttempt` record is created only when Execution actually
begins evaluating a bounded work step.

Scheduler invocations that terminate before an Execution attempt begins do not
create an `ExecutionAttempt`.

`ExecutionAttempt` records remain immutable historical records and are never
rewritten.

### 47. ExecutionAttempt Identity

Implement `ExecutionAttemptId`.

Requirements:

- deterministic;
- immutable;
- replay stable;
- persistable;
- canonical.

### 48. ExecutionProgress

`ExecutionProgress` represents deterministic advancement.

Progress remains domain neutral.

Progress does not imply completion.

Progress may be represented through:

- completed work;
- remaining work;
- exact progress quantity;
- discrete phase;
- another approved exact model.

Progress remains immutable.

### 49. ExecutionStep

Execution proceeds through bounded Steps.

One Scheduler invocation executes at most one `ExecutionStep`.

`ExecutionStep` never loops internally.

### 50. ExecutionStepResult

`ExecutionStepResult` represents the proposed outcome of one bounded Execution
step.

Possible contents include:

- progress;
- runtime transition;
- domain evidence;
- proposed Transaction;
- warnings;
- typed failure;
- completion evidence.

`ExecutionStepResult` remains immutable.

### 51. ExecutionCompletion

`ExecutionCompletion` represents successful completion evidence.

Completion references:

- ExecutionInstance;
- Completion Tick;
- Completion Attempt;
- Transaction reference;
- Completion evidence;
- schema version.

Completion remains immutable.

### 52. ExecutionFailure

`ExecutionFailure` represents deterministic failure evidence.

`ExecutionFailure` contains:

- failure code;
- failure scope;
- ExecutionAttempt;
- Simulation Tick;
- evidence;
- schema version.

`ExecutionFailure` remains immutable.

### 53. ExecutionCancellation

`ExecutionCancellation` represents explicit cancellation evidence.

Cancellation references:

- ExecutionInstance;
- cancellation authority;
- Simulation Tick;
- reason;
- schema version.

Execution never invents cancellation.

### 54. ExecutionOutcome

`ExecutionOutcome` classifies one `ExecutionStepResult`.

Examples:

- `ADVANCED`;
- `WAITING`;
- `FAILED`;
- `COMPLETED`;
- `CANCELLED`.

`ExecutionOutcome` is distinct from lifecycle state.

### 55. ExecutionTrace

`ExecutionTrace` records deterministic engineering evidence.

`ExecutionTrace` may include:

- ExecutionAttempt;
- evaluation phases;
- adapter evidence;
- progress;
- proposed Transaction;
- publication evidence;
- deterministic digests.

`ExecutionTrace` remains immutable.

### 56. ExecutionSummary

`ExecutionSummary` contains concise deterministic information.

Examples:

- Execution status;
- progress;
- attempts;
- completion state;
- failure count;
- cancellation state.

`ExecutionSummary` remains immutable.

### 57. ExecutionReport

`ExecutionReport` represents detailed execution evidence.

Reports remain immutable.

Reports never own runtime.

### 58. Runtime Separation

Definitions remain immutable.

Runtime remains mutable.

Execution runtime is defined in later Parts.

Definitions never mutate runtime.

Runtime never mutates definitions.

### 59. Domain Adapter

Execution requires an industry-neutral adapter.

Implement conceptually:

- `ExecutionAdapter`.

Responsibilities:

- validate work;
- evaluate one Step;
- produce StepResult;
- produce proposed Transaction;
- produce deterministic evidence.

Execution adapters remain external.

### 60. Domain Adapter Identity

Implement `ExecutionAdapterId`.

Requirements:

- immutable;
- replay stable;
- namespace validated;
- canonical;
- persistable.

### 61. Adapter Result

Execution adapters produce `ExecutionStepResult`.

Execution adapters never mutate runtime.

Execution adapters never mutate Inventory.

Execution adapters never execute Transactions.

### 62. Execution References

Execution owns:

- Execution Reports;
- Execution History;
- Execution Trace.

Execution references:

- executable work definitions;
- Allocation authorization;
- Allocation Commitments;
- Transaction references;
- authoritative domain state;
- external Resources.

Execution never owns:

- executable work definitions;
- Allocation Commitments;
- Transactions;
- authoritative domain state;
- Resources;
- Capacity;
- Inventory;
- Goods;
- Businesses;
- Workers;
- Machines.

### 63. Progress Exactness

Progress shall use exact arithmetic.

Floating point is prohibited.

Progress quantities remain replay stable.

### 64. Domain Neutrality

Execution definitions shall contain no:

- Production logic;
- Logistics logic;
- Maintenance logic;
- Utility logic;
- gameplay logic;
- Minecraft classes;
- NeoForge classes.

Industry semantics belong to adapters.

### 65. Schema Version

Execution definitions remain schema versioned.

Definitions retain compatibility.

Runtime compatibility is defined later.

### 66. Public Stability

Execution public contracts shall remain stable.

Future domains integrate through adapters.

Core Execution should not require modification to support future industries.

### 67. Execution Invariants

`ED-0001`

ExecutionInstance owns runtime only.

`ED-0002`

Executable work remains external.

`ED-0003`

ExecutionAuthorization remains immutable.

`ED-0004`

ExecutionContext is explicit.

`ED-0005`

ExecutionStep is bounded.

`ED-0006`

ExecutionAttempt is immutable.

`ED-0007`

ExecutionProgress uses exact arithmetic.

`ED-0008`

ExecutionStepResult is immutable.

`ED-0009`

ExecutionCompletion is immutable.

`ED-0010`

ExecutionFailure is immutable.

`ED-0011`

ExecutionCancellation is immutable.

`ED-0012`

ExecutionTrace is immutable.

`ED-0013`

ExecutionReport is immutable.

`ED-0014`

Definitions remain separate from runtime.

`ED-0015`

Execution adapters remain external.

`ED-0016`

Execution owns no industry logic.

### 68. End Part II

Part III defines:

- Execution Runtime;
- lifecycle;
- legal state transitions;
- attempts;
- retry model;
- waiting;
- completion;
- cancellation;
- failure propagation.

## Part III: Execution Runtime And Lifecycle

This Part defines the mutable runtime owned by the Execution Engine.

Definitions remain immutable.

Runtime remains mutable.

### 69. Runtime Philosophy

Execution Definitions describe work.

Execution Runtime describes the current state of one `ExecutionInstance`.

Runtime never mutates Definitions.

Definitions never mutate Runtime.

### 70. Runtime Ownership

Execution Runtime owns only mutable execution state.

Execution Runtime never owns:

- executable work definitions;
- Allocation Commitments;
- Transactions;
- Resources;
- Capacity;
- Inventory;
- Goods;
- domain runtime.

### 71. Runtime Objects

Execution Runtime introduces:

- `ExecutionRuntime`;
- `ExecutionRuntimeView`;
- `ExecutionRuntimeRegistry`;
- `ExecutionRuntimeService`;
- `ExecutionHistory`;
- `ExecutionReportRegistry`;
- `ExecutionTraceRegistry`;
- `ExecutionAttemptRuntime`.

Schema 1 introduces no additional runtime concepts.

### 72. Runtime Identity

`ExecutionRuntime` is keyed by `ExecutionInstanceId`.

Exactly one mutable Runtime exists for one active `ExecutionInstance`.

Terminal Runtime remains addressable for history and replay.

### 73. Runtime State

Runtime stores only mutable execution facts.

Examples:

- current lifecycle state;
- current progress;
- current attempt;
- current waiting reason;
- completion reference;
- failure reference;
- cancellation reference;
- last simulation tick;
- schema version.

### 74. Lifecycle States

Schema 1 defines:

- `CREATED`
- `READY`
- `RUNNING`
- `WAITING`
- `COMPLETING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

Terminal states:

- `COMPLETED`
- `FAILED`
- `CANCELLED`

### 75. Lifecycle Purpose

`CREATED`

`ExecutionInstance` exists. Authorization is not yet confirmed.

`READY`

Execution is authorized and eligible for Scheduler execution.

`RUNNING`

Execution is currently advancing through one bounded step.

`WAITING`

Execution is valid but unable to advance during this Scheduler invocation.

`COMPLETING`

Execution has finished domain work and is awaiting successful completion
publication.

`COMPLETED`

Execution successfully completed.

`FAILED`

Execution permanently failed.

`CANCELLED`

Execution was explicitly cancelled.

### 76. Legal Transitions

Schema 1 permits:

```text
CREATED -> READY
READY -> RUNNING
RUNNING -> WAITING
RUNNING -> COMPLETING
WAITING -> RUNNING
WAITING -> FAILED
COMPLETING -> COMPLETED
COMPLETING -> FAILED
READY -> CANCELLED
WAITING -> CANCELLED
RUNNING -> FAILED
```

Illegal transitions are rejected.

### 77. Illegal Transitions

Examples:

```text
COMPLETED -> RUNNING
FAILED -> READY
CANCELLED -> RUNNING
RUNNING -> CREATED
WAITING -> CREATED
```

Terminal states never transition.

### 78. ExecutionAttempt Runtime

`ExecutionAttemptRuntime` tracks mutable information for the current attempt.

Immutable `ExecutionAttempt` records remain separate.

Runtime may contain:

- attempt number;
- current progress;
- attempt status;
- attempt start tick;
- last update tick.

### 79. Attempt Creation

A new `ExecutionAttempt` record is created only when a bounded execution attempt
actually begins.

Scheduler invocations that terminate before an attempt begins create no new
`ExecutionAttempt`.

Attempts remain historically significant.

### 80. Waiting

`WAITING` indicates:

- Execution remains valid;
- Execution has not failed;
- Execution has not completed;
- Execution cannot currently advance.

Waiting shall include explicit typed reason evidence.

### 81. Completion

Execution enters `COMPLETING` after domain work has successfully reached its
completion condition.

Execution enters `COMPLETED` only after:

- completion validation;
- required Transaction success;
- Runtime publication;
- Report publication;
- History publication;
- Trace publication.

### 82. Failure

`FAILED` is terminal.

Failure evidence remains immutable.

Runtime references Failure.

Failure shall never be silently replaced.

### 83. Cancellation

Cancellation is externally requested.

Execution Runtime records:

- cancellation authority;
- cancellation tick;
- cancellation reason.

Execution does not invent cancellation.

### 84. Runtime Service

`ExecutionRuntimeService` is the only authority permitted to mutate Runtime.

Public mutation APIs are prohibited.

### 85. Runtime Registry

`ExecutionRuntimeRegistry` stores mutable Runtime.

Public views remain immutable.

Canonical ordering is required.

Duplicate `ExecutionInstanceId` is prohibited.

### 86. Runtime Queries

Queries shall include:

- find by ExecutionInstance;
- find by lifecycle;
- find by executable work;
- find active;
- find waiting;
- find completed;
- find failed;
- find cancelled.

Queries never mutate Runtime.

### 87. Runtime Validation

Validation shall detect:

- illegal transition;
- duplicate Runtime;
- unknown ExecutionInstance;
- unknown Attempt;
- malformed progress;
- invalid completion;
- invalid cancellation;
- unknown references;
- terminal transition violation.

### 88. Runtime Publication

Runtime publication remains atomic.

Partial Runtime publication is prohibited.

Publication shall synchronize:

- Runtime;
- History;
- Reports;
- Trace;
- completion references.

### 89. Runtime History

History records every Runtime transition.

History remains immutable.

History is append-only.

### 90. Runtime Reports

Reports summarize Runtime.

Reports never mutate Runtime.

### 91. Runtime Trace

Trace captures deterministic engineering evidence.

Trace remains immutable.

Trace supports replay.

### 92. Runtime Replay

Replay reconstructs Runtime using:

- Definitions;
- Runtime History;
- Execution Attempts;
- Transaction results;
- Simulation ticks.

Replay never queries live provider state.

### 93. Runtime Invariants

`ER-0001`

Runtime owns mutable state only.

`ER-0002`

Definitions remain immutable.

`ER-0003`

Exactly one Runtime exists per active ExecutionInstance.

`ER-0004`

ExecutionAttempt records remain immutable.

`ER-0005`

ExecutionAttemptRuntime remains mutable.

`ER-0006`

Lifecycle transitions remain deterministic.

`ER-0007`

Illegal transitions are rejected.

`ER-0008`

Terminal states never transition.

`ER-0009`

Runtime publication remains atomic.

`ER-0010`

History remains append-only.

`ER-0011`

Reports remain immutable.

`ER-0012`

Trace remains immutable.

`ER-0013`

Replay remains deterministic.

`ER-0014`

Runtime Service owns mutation.

`ER-0015`

Runtime Registry exposes immutable views.

`ER-0016`

Queries never mutate Runtime.

### 94. End Part III

Part IV defines:

- Execution Pipeline;
- bounded execution steps;
- Execution adapters;
- Transaction proposal;
- publication;
- engineering trace.

## Part IV: Execution Pipeline

### 95. Purpose

Part IV defines the deterministic Execution pipeline.

The pipeline explains how one bounded Execution step progresses from immutable
input to immutable proposed result while preserving every architectural
ownership boundary.

This Part does not redefine ownership already established by Parts I through
III.

### 96. Pipeline Philosophy

Execution is a deterministic pipeline.

Execution never mutates authoritative state during evaluation.

Execution evaluates.

Execution proposes.

Execution validates.

Execution publishes atomically.

### 97. Canonical Pipeline

The canonical Execution pipeline is:

```text
Capture Input
  -> Validate Runtime
      -> Validate Authorization
          -> Resolve Execution Adapter
              -> Evaluate One Bounded Step
                  -> Validate Proposed Result
                      -> Construct Transaction Proposal
                          -> Validate Publication
                              -> Atomic Publication
                                  -> Reports
                                      -> History
                                          -> Trace
```

Every phase executes exactly once.

No phase loops.

No phase recursively invokes Execution.

### 98. Capture Input

`ExecutionInput` is immutable.

`ExecutionInput` shall contain every fact required for one bounded Execution
step.

`ExecutionInput` shall include only immutable references and immutable Runtime
views.

`ExecutionInput` shall never query live external systems.

### 99. Runtime Validation

Execution validates:

- ExecutionInstance;
- ExecutionRuntime;
- ExecutionAttemptRuntime;
- ExecutionAuthorization;
- lifecycle;
- schema;
- Runtime consistency.

Validation failures remain typed.

### 100. Authorization Validation

Execution verifies:

- AllocationSet;
- required Commitments;
- Commitment lifecycle;
- Simulation Tick;
- required Execution conditions.

Execution never repairs authorization.

Execution never allocates.

### 101. Execution Adapter

Execution resolves exactly one `ExecutionAdapter`.

`ExecutionAdapter` performs one bounded deterministic step.

`ExecutionAdapter` returns an immutable `ExecutionStepResult`.

Execution never interprets industry-specific work.

### 102. Execution Step

`ExecutionAdapter` evaluates exactly one bounded step.

The adapter may:

- advance progress;
- remain waiting;
- fail;
- complete domain work;
- produce deterministic evidence;
- produce a proposed Transaction.

The adapter shall never:

- mutate authoritative state;
- mutate Inventory;
- allocate Capacity;
- execute Transactions.

### 103. Transaction Proposal

Execution may construct one immutable proposed Transaction.

Execution never executes Transactions.

Execution submits proposals through the approved Transaction boundary.

Execution completion depends upon Transaction success where required.

### 104. Publication

Publication remains atomic.

The following shall publish together:

- Execution Runtime;
- Execution Reports;
- Execution History;
- Execution Trace;
- completion evidence;
- failure evidence;
- cancellation evidence.

Partial publication is prohibited.

### 105. Engineering Trace

`ExecutionTrace` shall include:

- pipeline phases;
- ExecutionAttempt;
- progress;
- adapter evidence;
- Transaction proposal;
- publication evidence;
- deterministic digests.

Trace remains immutable.

### 106. Failure Isolation

Failure during one `ExecutionInstance` shall never corrupt unrelated
ExecutionInstances.

Adapter failures remain local.

Publication failures remain local.

Runtime corruption is prohibited.

### 107. Replay

Replay shall reproduce:

- progress;
- attempts;
- lifecycle;
- Reports;
- History;
- Trace;
- Transaction proposal;
- deterministic digests.

Execution replay never queries live external systems.

### 108. Bounded Execution

One Scheduler invocation performs at most one bounded `ExecutionStep`.

Execution never performs unbounded work.

Execution never recursively schedules itself.

Execution never busy-waits.

### 109. Execution Pipeline Invariants

`EP-0001`

The pipeline executes once.

`EP-0002`

Evaluation remains pure.

`EP-0003`

Execution never mutates authoritative state.

`EP-0004`

Publication remains atomic.

`EP-0005`

ExecutionAdapter owns domain logic.

`EP-0006`

Execution owns orchestration.

`EP-0007`

Replay remains deterministic.

`EP-0008`

Execution remains bounded.

`EP-0009`

ExecutionInput remains immutable and explicit.

`EP-0010`

Validation failures remain typed.

`EP-0011`

Execution never repairs or creates Allocation authorization.

`EP-0012`

Exactly one ExecutionAdapter is resolved for one bounded step.

`EP-0013`

ExecutionAdapter never executes Transactions.

`EP-0014`

Execution constructs at most one proposed Transaction per bounded step.

`EP-0015`

ExecutionInstance failures remain isolated.

`EP-0016`

ExecutionTrace remains immutable and digest-backed.

### 110. End Part IV

Part V defines:

- Execution Adapter Framework;
- adapter registration;
- adapter identity;
- adapter resolution;
- domain work translation;
- adapter validation;
- adapter failure isolation.

## Part V: Execution Adapter Framework

### 111. Purpose

Part V defines the industry-neutral Execution Adapter Framework.

Execution owns orchestration.

Domain adapters own work semantics.

Execution itself never understands domain-specific behavior.

### 112. Architectural Position

```text
Execution Engine
  -> Execution Adapter
      -> Domain Evaluation
          -> ExecutionStepResult
              -> Transaction Proposal
                  -> Transaction Framework
                      -> Observed Transaction Result
                          -> Execution Runtime
```

Execution never bypasses Transactions.

The Transaction Framework and Observed Transaction Result stages are mandatory
whenever a proposed Transaction is required. They refine the abbreviated
pipeline in Part IV without changing its ownership boundaries.

### 113. Execution Adapter

The generic `ExecutionAdapter` contract shall:

- validate executable work;
- evaluate exactly one bounded step;
- produce an immutable `ExecutionStepResult`;
- produce deterministic domain evidence;
- produce one immutable proposed Transaction when required;
- produce typed domain failures.

`ExecutionAdapter` shall never:

- mutate authoritative state;
- execute Transactions;
- allocate Resources;
- schedule work;
- read wall-clock time;
- use randomness.

### 114. Execution Adapter Identity

`ExecutionAdapterId` is:

- immutable;
- replay stable;
- canonical;
- persistable;
- namespace validated.

### 115. Execution Context

`ExecutionContext` contains every deterministic fact required for one Execution
step.

`ExecutionContext` remains immutable.

`ExecutionContext` shall never query live external systems.

### 116. Execution Step Evaluation

`ExecutionAdapter` evaluates exactly one bounded step.

Possible outcomes include:

- advance;
- wait;
- fail;
- complete domain work;
- construct a Transaction proposal.

`ExecutionAdapter` never completes Execution directly.

### 117. Transaction Boundary

`ExecutionAdapter` may construct one immutable proposed Transaction.

Execution submits the proposal.

The Transaction Framework validates and executes the proposal.

Execution observes the resulting Transaction outcome.

Execution completion depends upon the observed Transaction result.

Execution never executes Transactions.

### 118. Transaction Observation

Execution explicitly observes Transaction results as:

- `ACCEPTED`;
- `REJECTED`;
- `EXECUTED`;
- `FAILED`.

These are Execution observation classifications, not replacements for the
authoritative Transaction Framework's `TransactionStatus` values.

`ACCEPTED` records accepted validation represented by authoritative
`VALIDATED` Transaction evidence. It does not permit Execution completion.

`REJECTED` records authoritative rejection.

`EXECUTED` requires authoritative Transaction evidence with final `APPLIED`
status. Only this classification can satisfy a required successful Transaction
completion condition.

`FAILED` records a typed Transaction submission, validation, execution, or
observation failure without inferring successful mutation.

Execution Runtime transitions according to the observed Transaction outcome.

Execution never infers Transaction success.

### 119. Execution Publication

Execution Runtime transitions only after observing required Transaction
results.

Publication remains atomic.

The following publish together:

- Execution Runtime;
- Reports;
- History;
- Trace;
- completion evidence;
- failure evidence;
- cancellation evidence.

### 120. Adapter Purity

Given identical:

- ExecutionContext;
- Simulation Tick;
- Runtime snapshot;
- Allocation authorization;

an `ExecutionAdapter` produces identical:

- ExecutionStepResult;
- Transaction proposal;
- domain evidence.

No hidden state.

No randomness.

### 121. Domain Neutrality

Execution Core contains no knowledge of:

- Production;
- Logistics;
- Maintenance;
- Utilities;
- gameplay;
- Minecraft;
- NeoForge.

All semantics belong to adapters.

### 122. Execution Adapter Invariants

`EA-0001`

Adapter owns domain semantics.

`EA-0002`

Execution owns orchestration.

`EA-0003`

Transactions own mutation.

`EA-0004`

Adapters remain deterministic.

`EA-0005`

Adapters remain bounded.

`EA-0006`

Adapters produce immutable results.

`EA-0007`

Execution observes Transaction outcomes.

`EA-0008`

Execution never bypasses Transactions.

`EA-0009`

ExecutionContext remains immutable and explicit.

`EA-0010`

One adapter evaluation performs exactly one bounded step.

`EA-0011`

Adapter domain failures remain typed.

`EA-0012`

Adapters never allocate Resources or schedule work.

`EA-0013`

Adapters read no wall clock and use no randomness.

`EA-0014`

Adapters never complete Execution directly.

`EA-0015`

Required Runtime transitions follow observed Transaction evidence.

`EA-0016`

Execution publication of adapter results remains atomic with Execution
evidence.

### 123. End Part V

Part VI defines:

- Execution Runtime;
- Reporting;
- History;
- Replay;
- Verification.

## Part VI: Execution Runtime, Evidence, And Replay

### 124. Purpose

Part VI defines the mutable Runtime and immutable evidence owned by the
Execution subsystem.

Execution Runtime represents current state.

Execution evidence records historical facts.

Replay reconstructs Runtime solely from deterministic evidence.

### 125. Runtime Philosophy

Execution Definitions remain immutable.

Execution Runtime remains mutable.

Execution evidence remains immutable.

Execution Runtime never mutates Definitions.

Execution evidence never mutates Runtime.

### 126. Execution Runtime

Execution Runtime owns:

- current lifecycle;
- current progress;
- current ExecutionAttemptRuntime;
- current waiting state;
- current completion reference;
- current failure reference;
- current cancellation reference;
- last Simulation Tick;
- schema version.

Runtime owns no domain state.

### 127. Execution Runtime Identity

Runtime is keyed by `ExecutionInstanceId`.

Exactly one mutable Runtime exists for one active `ExecutionInstance`.

Terminal Runtime remains replayable.

### 128. Execution Registry

Execution defines:

- `ExecutionRuntimeRegistry`;
- `ExecutionReportRegistry`;
- `ExecutionTraceRegistry`;
- `ExecutionHistoryRegistry`.

Registries expose immutable public views.

Mutation occurs only through `ExecutionRuntimeService`.

Canonical ordering is required.

### 129. Execution History

`ExecutionHistory` records every Runtime transition.

History is append-only.

History remains immutable.

History includes:

- transition;
- Simulation Tick;
- Attempt;
- reason;
- evidence;
- schema version.

History is never rewritten.

### 130. Execution Reports

`ExecutionReport` summarizes Runtime.

Reports remain immutable.

Reports never own Runtime.

Reports include:

- current state;
- progress;
- Attempts;
- waiting;
- completion;
- failure;
- cancellation;
- Transaction observations.

### 131. Execution Trace

`ExecutionTrace` records deterministic engineering evidence.

Trace includes:

- pipeline phases;
- ExecutionAttempt;
- ExecutionStepResult;
- adapter evidence;
- Transaction proposal;
- observed Transaction result;
- publication evidence;
- canonical digests.

Trace remains immutable.

### 132. Execution Replay

Replay reconstructs Runtime using only:

- Definitions;
- ExecutionHistory;
- ExecutionAttempt records;
- observed Transaction results;
- Simulation Ticks.

Replay shall never query:

- Planning;
- Allocation providers;
- live Inventory;
- live Resources;
- live Capacity;
- Minecraft Runtime.

Replay remains deterministic.

### 133. Execution Digests

Execution defines canonical SHA-256 digests:

- Execution Input Digest;
- Execution Runtime Digest;
- Execution History Digest;
- Execution Report Digest;
- Execution Trace Digest;
- Execution Result Digest.

Equivalent replay produces identical digests.

### 134. Runtime Publication

Publication remains atomic.

The following shall publish together:

- Execution Runtime;
- Execution History;
- Execution Reports;
- Execution Trace;
- completion evidence;
- failure evidence;
- cancellation evidence.

Partial publication is prohibited.

### 135. Runtime Queries

Execution defines an immutable query model.

Queries include:

- by ExecutionInstance;
- by lifecycle;
- by executable work;
- by Attempt;
- by waiting reason;
- by completion;
- by failure;
- by cancellation.

Queries never mutate Runtime.

### 136. Replay Validation

Replay validates:

- Execution Definitions;
- Runtime;
- History;
- Attempts;
- Transaction observations;
- lifecycle;
- Reports;
- Trace;
- digests.

Replay shall fail explicitly on inconsistency.

### 137. Engineering Evidence

Execution evidence shall explain:

- why work advanced;
- why work waited;
- why work failed;
- why work completed;
- which Transaction was proposed;
- which Transaction result was observed;
- why publication succeeded.

No opaque outcomes.

### 138. Execution Runtime Invariants

`EV-0001`

Runtime owns mutable state.

`EV-0002`

Evidence remains immutable.

`EV-0003`

History remains append-only.

`EV-0004`

Reports remain immutable.

`EV-0005`

Trace remains immutable.

`EV-0006`

Publication remains atomic.

`EV-0007`

Replay remains deterministic.

`EV-0008`

Runtime Registry exposes immutable views.

`EV-0009`

Runtime mutation occurs only through ExecutionRuntimeService.

`EV-0010`

Queries never mutate Runtime.

`EV-0011`

Digests remain canonical.

`EV-0012`

Execution History is never rewritten.

`EV-0013`

Replay never queries live systems.

`EV-0014`

Evidence explains every lifecycle transition.

`EV-0015`

Definitions remain separate from Runtime.

`EV-0016`

Definitions remain separate from evidence.

### 139. End Part VI

Part VII defines:

- Verification;
- Architecture Compliance;
- Acceptance Criteria;
- Completion.

## Part VII: Verification, Acceptance Criteria, And Architectural Compliance

This Part defines the verification requirements for the Deterministic
Execution Engine.

No implementation shall be considered complete unless every acceptance
criterion is satisfied.

### 140. Verification Philosophy

Verification demonstrates architectural correctness.

Passing tests alone do not imply architectural compliance.

Execution must satisfy:

- ownership;
- determinism;
- replay;
- atomic publication;
- bounded execution;
- Runtime correctness;
- evidence correctness;
- architecture compliance.

### 141. Verification Categories

Execution implementations shall be verified through:

- Identity Verification;
- Ownership Verification;
- Runtime Verification;
- Lifecycle Verification;
- Execution Pipeline Verification;
- Adapter Verification;
- Authorization Verification;
- Transaction Boundary Verification;
- Publication Verification;
- Replay Verification;
- Evidence Verification;
- Performance Verification;
- Living World Verification;
- Architecture Compliance Review.

### 142. Identity Verification

Verify:

- ExecutionInstanceId;
- ExecutionAttemptId;
- ExecutionAdapterId;
- Execution Runtime identity;
- Execution Definitions;
- Execution Reports;
- Execution Traces;
- Execution History.

Identities remain:

- immutable;
- canonical;
- replay stable;
- persistable;
- namespace validated.

### 143. Ownership Verification

Verify:

- Planning owns decisions;
- Allocation owns authorization;
- Execution owns Runtime;
- Execution owns Reports;
- Execution owns History;
- Execution owns Traces;
- Transactions own mutation;
- Inventory owns quantities.

Execution never violates another subsystem's authority.

### 144. Runtime Verification

Verify:

- exactly one Runtime exists for one active ExecutionInstance;
- Definitions remain immutable;
- Runtime remains mutable;
- evidence remains immutable;
- Runtime mutation occurs only through ExecutionRuntimeService.

### 145. Lifecycle Verification

Verify:

- every legal transition;
- every illegal transition;
- terminal-state protection;
- Attempt creation;
- waiting;
- completion;
- failure;
- cancellation.

Illegal transitions fail explicitly.

### 146. Execution Pipeline Verification

Verify:

```text
Capture Input
  -> Validate Runtime
      -> Validate Authorization
          -> Resolve Adapter
              -> Evaluate Step
                  -> Validate Result
                      -> Construct Transaction Proposal
                          -> Submit Proposal
                              -> Observe Transaction Result
                                  -> Construct Runtime Transition
                                      -> Atomic Publication
                                          -> Reports
                                              -> History
                                                  -> Trace
```

Each phase executes exactly once.

### 147. Adapter Verification

Verify:

- ExecutionAdapter receives immutable context;
- ExecutionAdapter evaluates exactly one bounded step;
- ExecutionAdapter produces immutable StepResult.

ExecutionAdapter never:

- mutates state;
- allocates Capacity;
- executes Transactions;
- uses randomness;
- reads wall-clock time.

### 148. Authorization Verification

Verify:

- Execution never advances without required Allocation authorization;
- Execution never repairs authorization;
- Execution never allocates missing Capacity;
- Execution observes authorization only.

### 149. Transaction Boundary Verification

Verify:

- Execution constructs Transaction proposals;
- Transaction Framework validates and executes proposals;
- Execution observes Transaction results;
- Execution never executes Transactions directly;
- Execution completion depends upon required observed Transaction success.

### 150. Publication Verification

Verify atomic publication.

The following publish together:

- Runtime;
- History;
- Reports;
- Trace;
- completion evidence;
- failure evidence;
- cancellation evidence.

Partial publication is prohibited.

### 151. Replay Verification

Given identical:

- Definitions;
- Runtime;
- authorization;
- Simulation Ticks;
- Execution adapters;
- observed Transaction results;

Execution shall produce identical:

- Runtime;
- History;
- Reports;
- Trace;
- digests.

Replay remains deterministic.

### 152. Evidence Verification

Verify every lifecycle transition is explained through immutable evidence.

Execution shall explain:

- progress;
- waiting;
- failure;
- completion;
- cancellation;
- Transaction observation;
- publication.

No opaque transitions.

### 153. Performance Verification

Verify deterministic Execution under representative large workloads.

Representative scenarios include:

- millions of ExecutionInstances;
- millions of Attempts;
- large Runtime registries;
- large History registries;
- large Report registries;
- large Trace registries;
- high replay volume.

Results remain deterministic.

### 154. Living World Verification

Verify autonomous operation.

Without players:

- Execution continues;
- domain adapters continue;
- Transactions continue;
- replay remains correct;
- simulation remains operational.

### 155. Failure Isolation

Failure of one ExecutionInstance shall not corrupt unrelated
ExecutionInstances.

Adapter failure remains local.

Publication failure remains local.

Runtime corruption is prohibited.

### 156. Save Compatibility

Schema-version compatibility shall be verified.

The following shall either load successfully or fail explicitly:

- Execution Definitions;
- Runtime;
- History;
- Reports;
- Trace.

Silent repair is prohibited.

### 157. Repository Requirements

Execution implementation shall include:

- architecture documentation;
- replay tests;
- lifecycle tests;
- pipeline tests;
- adapter tests;
- publication tests;
- determinism tests;
- stress tests;
- architecture validation.

Documentation forms part of the implementation contract.

### 158. Architecture Gates

Execution implementation shall not proceed to production until:

- ownership is verified;
- Runtime is verified;
- pipeline is verified;
- replay is verified;
- publication is verified;
- Architecture Validation passes;
- acceptance criteria are satisfied.

### 159. Acceptance Criteria

An RFC-0023 implementation shall be considered complete only when:

- Execution remains deterministic;
- Execution remains bounded;
- Execution Runtime is correct;
- Execution Reports remain immutable;
- Execution History remains append-only;
- Execution Trace remains immutable;
- Execution adapters remain pure;
- Transaction boundary remains intact;
- publication remains atomic;
- replay succeeds;
- Living World simulation remains autonomous.

### 160. Completion Report

Implementation shall produce a completion report summarizing:

- architecture implemented;
- public APIs;
- Runtime;
- pipeline;
- Reports;
- History;
- Trace;
- replay;
- testing totals;
- stress testing;
- known limitations;
- future extension points.

### 161. Future Expansion

Future schemas may extend Execution through:

- parallel deterministic execution;
- distributed execution;
- Execution budgets;
- cooperative execution;
- long-running work;
- Execution preemption;
- Execution migration;
- Execution batching.

Schema 1 implements none of these.

### 162. Final Principle

Execution performs deterministic bounded work.

Planning decides.

Allocation authorizes.

Execution performs.

Transactions mutate.

Inventory owns.

These responsibilities remain permanently independent.

### 163. End RFC-0023

RFC-0023 is complete.

Implementation may begin only after architectural review and owner approval.
