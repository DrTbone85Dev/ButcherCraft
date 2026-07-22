# ButcherCraft Compatibility Philosophy

Status: long-term integration direction; no compatibility API implementation

## Purpose

Compatible mods should participate in the same regional simulation as ButcherCraft industries. Integration should reuse shared identity, product, capability, order, logistics, utility, and market contracts after those contracts are proven and documented.

Compatibility does not mean importing another mod's internal state, duplicating its content, or granting either client authority over the server simulation.

## General Rules

- Prefer standard Minecraft or NeoForge tags, capabilities, registries, and events where they express the required contract.
- Add ButcherCraft public APIs only when a real integration proves the need and ownership boundary.
- Exchange stable ids, immutable summaries, and explicit requests rather than mutable manager or save objects.
- Preserve source-mod ownership of blocks, entities, inventories, energy, recipes, and lifecycle.
- Validate all consequential changes server-side.
- Make optional integration conditional and safe when a mod is absent.
- Preserve unresolved optional references where practical instead of deleting save data.
- Document load order, failure behavior, persistence, and version compatibility before release.
- Avoid one-off adapters when multiple mods can use a shared economic contract.

## MineColonies

MineColonies colonies may eventually generate population and institutional demand, provide workplaces or labor context, and participate as producers or consumers.

Integration should use bounded colony summaries and explicit orders. ButcherCraft should not control colony citizens or replace MineColonies job AI. MineColonies should not mutate ButcherCraft runtime registries directly.

## Create

Create machines and contraptions may become industrial equipment, material movers, or transportation participants.

Integration should map declared capabilities into ButcherCraft transformations and transactions. Visual motion or item movement alone must not bypass product validation, atomic commits, inventory ownership, or server authority.

## Farmer's Delight

Farmer's Delight ingredients and foods may participate in supply chains, restaurant production, retail demand, and shared product categories.

Integration should prefer existing tags and stable item identities. ButcherCraft should not register duplicate versions of equivalent external products solely to make them economically visible.

## Immersive Engineering

Immersive Engineering may provide industrial equipment, power, infrastructure, and future utility capacity.

Integration should translate available capabilities and service summaries while preserving Immersive Engineering's authority over its machines and energy system. ButcherCraft should consume a validated utility or equipment contract, not reach into internal block entities.

## Future Third-Party Integrations

Future integrations should enter through shared economic contracts such as products, producers, consumers, orders, warehouses, transport services, utilities, and markets. New industry mods should not need a custom private economy adapter when a general contract fits.

Third-party API stability will begin only after at least one real consumer validates the contract. Until then, `docs/API_OVERVIEW.md` is a planning map, not a compatibility guarantee.

## Compatibility Failure Behavior

- Missing optional mods disable their adapters without preventing ButcherCraft Core from loading.
- Unknown optional ids remain unresolved or inactive where practical and are surfaced diagnostically.
- Invalid transactions fail atomically.
- Unsupported API or schema versions fail with structured diagnostics.
- Integration failures must not silently delete products, businesses, orders, shipments, or ownership references.

## Explicit Non-Goals For This Milestone

No compatibility module, API package, product mapping, recipe bridge, machine adapter, colony demand, utility bridge, market participant, networking, or gameplay is implemented here.

