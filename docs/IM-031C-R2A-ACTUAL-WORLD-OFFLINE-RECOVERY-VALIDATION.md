# IM-031C-R2A Actual-World Offline Recovery Validation

Status: COMPLETE - OFFLINE RECOVERY GENERATION VALIDATED

Validation source: a byte-identical disposable copy of the preserved failed-
world evidence. Minecraft startup, native owner restoration, and startup
generation selection were not attempted and remain IM-031C-R4 work.

## Protected Evidence

- Non-lock files: `61`
- Bytes: `12,915,790`
- Canonical manifest: `998b0395fa98e3621eb95bea5080f5592895bc21d4c35e2fff4db7cc20484ceb`
- Original-to-evidence differences: `0`
- Evidence-to-disposable-native differences after publication: `0`
- Additive checkpoint artifacts in the disposable copy: `38`

## Actual Analysis

- World Identity: `butchercraft:world_identity/world_1k3jka2n744s9`
- World Identity digest: `sha256:6eddce6c3e44e6f4de105c65be99cfb9ecd15311cecaf1069b96752b64f2dd81`
- Recovery Identity: `butchercraft:legacy_split_recovery/v1/d2b39d6b69ea9eabcb5b8e05cf26e8ca34d6afcb7646f062fe6a9251ac73c5f5`
- Analysis digest: `sha256:9854af750c72e74607f9b3975def0491ecaaba06438c5881ec41a714c0cd0aaa`
- Eligibility: `RECOVERABLE_WITH_AUTHORITY_BLOCKS`
- Clock: `39872`
- Scheduler: `39084`
- Gap: `788`
- Discontinuity: `39085-39872`
- Next normal admission: `39873`
- Historical acknowledgements: `9`
- Ordinary Work reconstructed: `0`
- Synthetic ticks: `0`
- Legacy Clock temp: `IDENTICAL_STALE_DEBRIS`

## Source Snapshots

Each source identity has the form shown and its suffix equals its exact content
digest.

| Owner | Source snapshot identity |
| --- | --- |
| Business Runtime | `butchercraft:legacy_source_snapshot/v1/business_runtime/d1062648a7b8ecec8a53ba2e7e6061060210b7c4c016af9f1abf6b00678bff59` |
| Contracts | `butchercraft:legacy_source_snapshot/v1/contracts/763aa6d6e9b067a209b5f162485a5d35503694a9986b7e3c92adbb79edc49d8c` |
| Economic Actors | `butchercraft:legacy_source_snapshot/v1/economic_actors/56de9894dc16cf1641b82ac98b08b91b2b206e63b2f924e5a6a9e63656812a6d` |
| Execution | `butchercraft:legacy_source_snapshot/v1/execution/cfb34eb60511d7971deb2f02f51bd84e6536674842584a47f229b96e13d37d28` |
| Goods | `butchercraft:legacy_source_snapshot/v1/goods/8fe2b36e249e67d13b8a0946b22372c7aa077b202722b2f78be396345ed56736` |
| Inventory | `butchercraft:legacy_source_snapshot/v1/inventory/15be99362d5758d3f3d88ad0f3cb7e0ec513691087ae894cbef82499a2dd5cba` |
| Material Handling | `butchercraft:legacy_source_snapshot/v1/material_handling/9bff2603c7da1534cbe2c8e692bdee7bfc003e9ee14c56c50d160684b7615038` |
| Orders | `butchercraft:legacy_source_snapshot/v1/orders/80b785750dd9f75b4abcb32a2c92411c0e3f141fac45d2aaef274ab25d3f811e` |
| Planning | `butchercraft:legacy_source_snapshot/v1/planning/cc8a8c3f4acd4b1d1bd8ac6a0331522de312ca197fa65da6c809a63e5d4aacf8` |
| Player Identity | `butchercraft:legacy_source_snapshot/v1/player_identity/42f114c8c9a3db8b27eab88c83faeb77c0b55a28d913a70b096e338a18325286` |
| Production | `butchercraft:legacy_source_snapshot/v1/production/9c0488680c0ac8d01f3af745b10c73ed2b142f9b4af863eef2cfe966d1b5cf41` |
| Simulation Clock | `butchercraft:legacy_source_snapshot/v1/simulation_clock/0d2b73cd7050b55b57e646565b5a05aefede9b3728ce3b3e324dd2acf523ee0b` |
| Simulation Scheduler | `butchercraft:legacy_source_snapshot/v1/simulation_scheduler/31c49fc1c4a05ef1839b23e595e62a8ad79f119ca6eb7cc167efdb27bcf23feb` |
| Transactions | `butchercraft:legacy_source_snapshot/v1/transactions/f32b5772ac0373090ebf986ac680ea66b809498ac74853736c2444e4be342e2b` |
| Workforce | `butchercraft:legacy_source_snapshot/v1/workforce/38a0d6966054f4d8b0387e1071df6fa9f4f2da5eac842f4efa94cc03702d58ea` |
| Workstation | `butchercraft:legacy_source_snapshot/v1/workstation/f77ef4028af543ef6c47a51c6c0ef5d3505bc122be4a17de20e214b57505a572` |

## Historical Acknowledgements

All nine are `SUCCEEDED` evidence only and have no execution capability.

| Acknowledgement identity | Execution operation identity |
| --- | --- |
| `butchercraft:historical_coordination_acknowledgement/v1/07759f8e1854ea6f2275ede4068e3e2259c2fa65c83ae7e88e23c83e9a4949d9` | `butchercraft:execution_operation/v1/3fc5e8a35de93aff72ebf93c92b8d78edf6f1d875fadf2832cdb80d194d6a751` |
| `butchercraft:historical_coordination_acknowledgement/v1/2257cd8d67cfbcd40aae875865715c3b735e5b2df44db36b9ce27d9a9530ff3c` | `butchercraft:execution_operation/v1/870ae262b5a64a5bdb88218b7105838dd628f162f5e667cfa980bc96c9750152` |
| `butchercraft:historical_coordination_acknowledgement/v1/22a27522acb7e3be613aa2eccf65b623409d11f6b90b1fbd61b6bf86669a9ee6` | `butchercraft:execution_operation/v1/e621777372d63bc67d129eee93c0dc372c5566e724426f0e9e9b9f384b831141` |
| `butchercraft:historical_coordination_acknowledgement/v1/24439c5834ce5b85005599119d37393690c9317e75e2efeba8f4c28e711ab2d2` | `butchercraft:execution_operation/v1/e90bbfa1a0905fe16fa57af7fe8d4d8a23b70bfc6a0ba8b5ea3ec028e59140e5` |
| `butchercraft:historical_coordination_acknowledgement/v1/87c16b47c167653da9df6467661c87af4376d79f6ff2f332763290e14459f27f` | `butchercraft:execution_operation/v1/5f8813cd46ffc9ed428e2ba2b39bcd17de5a50e12197981605a04d40b09a2245` |
| `butchercraft:historical_coordination_acknowledgement/v1/c7f1e3a334ac6e96a646385fd61fb0b23e9838e18b3451ef3f1b54fb64171390` | `butchercraft:execution_operation/v1/b5572e18a1e4f0946f496a6d5a9f1a44f97483e0a78de1f0962cd71e44e7e065` |
| `butchercraft:historical_coordination_acknowledgement/v1/ef5a9f05b6e48808d033c3282f5e3b49146b88f5bf4790b6e2f7f71861ab362c` | `butchercraft:execution_operation/v1/16cdd1a19a03c16aff75d3e923765eaefdf8541823865eb00479b168fcb08c46` |
| `butchercraft:historical_coordination_acknowledgement/v1/efd29c5edcce506880f3977419cb05210993d9910994479972caf2291a112aec` | `butchercraft:execution_operation/v1/1660771a72fe3b42c869d145828ef538f43bfd0ba5d9b7215491469655e404fe` |
| `butchercraft:historical_coordination_acknowledgement/v1/f0eb42be1c80ae31b39afa6ea99722c07637ef5187ac7f7f12d7d17d5a843480` | `butchercraft:execution_operation/v1/b4c3b169c9c3abcda281d682fa9118234b509394cc1f4308e91655ea95e1c4fa` |

## Preserved Authorized Work

- Execution operation: `butchercraft:execution_operation/v1/dc08ca2fc1b1e083f8fe0f21a5842c8a092e389a699cec6618cd4e635721c3f4`
- Machine Run: `butchercraft:machine_run/v1/92d1b5a4f85f9e46907c4ba80bc4becfe0d5cd76600d4056156063d08bb32b3c`
- Machine Run generation: `1`
- Child: `butchercraft:machine_run_child/v1/e5e6e6d41fd78bed2642c7325276940d3ba231008fc58ad55e8b66c2b408ce73`
- Child sequence: `10`
- Workstation instance: `butchercraft:workstation_instance/v1/243a13bf683621dc6d48a20ed39fdc81f96bc9fd776ba316feb236bf7b771996`
- Workstation generation: `6`
- Authorization digest: `sha256:156d5c3435f115e8cd6a64b9c3b87ba2d69794caa972d8802b9c02f7bba793dd`
- Source state: `AUTHORIZED`, unscheduled, uninvoked, nonterminal
- Recovery projection: `SUSPENDED_RESTART_REQUIRED` / `RESTART_REQUIRED`

## Authority Block

- Recovery block: `butchercraft:recovery_authority_block/v1/98a64ecc5b7fb1a1d3cd70173aff733b52375692c945c594cd06be9a3638ba2d`
- Planning block: `butchercraft:planning_recovery_authority_block/v1/0e1fea625db3f8a54f426dc810c5af945dfacf3281a3db350184dfe5aad371f5`
- Work: `butchercraft:economic_planning_cycle/continuation`
- Eligibility tick: `39601`
- Scope: `WHOLE_WORLD_MUTATION`
- Dependency closure proven: `false`
- Planning replay count: `0`

## Publication

- Operator authorization: `butchercraft:legacy_split_recovery_authorization/v2/d77a1689916f5a160b8126efe80196900d3c87bcae7db1a769e91a99099969a9`
- Authorization digest: `sha256:d77a1689916f5a160b8126efe80196900d3c87bcae7db1a769e91a99099969a9`
- Generation: `butchercraft:checkpoint/00000000000000000001/39872`
- Predecessor: none
- Participant count: `17`
- Generation manifest digest: `sha256:63a75e239e830e91cc1b60a4c5f8ec0560dfdec411d339f80f321afb73a116b8`
- Recovery Result: `butchercraft:legacy_split_recovery_result/v1/4dc2f6c19b3e6f66fbf258a63b91ba59b15f72edce646bea3d6a3ccba32d401e`
- Head sequence: `1`
- Fresh-service reload: same committed generation and Recovery Result
- Exact re-publication: `EXISTING_RESULT_OBSERVED`; no new artifacts

Required participants were Business Runtime, Checkpoint Recovery, Contracts,
Economic Actors, Execution, Goods, Inventory, Material Handling, Orders,
Planning, Player Identity, Production, Simulation Clock, Simulation Scheduler,
Transactions, Workforce, and Workstation.

## Invariance And Validation

- Dry-run native changes: `0`
- Publication native changes: `0`
- Historical consequences replayed: `0`
- New Execution operations: `0`
- New Scheduler invocations: `0`
- New Machine Run children: `0`
- New Workstation inventory effects: `0`
- Planning replays: `0`
- All eight R2 publication fault boundaries converged to one generation on
  separate actual-evidence-derived disposable replicas.
- Focused sanitized and actual-world adapter tests passed.
- Full test suite: `1,658` tests, `0` failures, `0` errors; two environment-
  gated actual-world tests were separately run and passed with explicit paths.
- Build passed.
- `git diff --check` passed with Windows line-ending warnings only.

## Remaining Boundary

The recovery generation is validated offline. Coordinated live owner checkpoint
publication remains IM-031C-R3. Native owner restoration, startup generation
selection, Minecraft boot validation, and hard-crash startup recovery remain
IM-031C-R4.
