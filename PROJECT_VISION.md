# ButcherCraft Project Vision

Status: proposed planning document  
Target: Minecraft 1.21.1, NeoForge, Java 21  
Scope: ButcherCraft Core with a limited number of substantial optional expansion mods

## Core Player Experience

ButcherCraft is a Minecraft-style meat-processing and business-management simulation. The player starts as a hands-on owner-operator who personally performs every essential task: preparing inputs, processing one product at a time, packaging goods, keeping the workspace clean, storing products cold, and fulfilling simple customer orders.

As the business grows, the player shifts from doing all labor directly to designing a facility, assigning employee villagers, managing equipment capacity, maintaining refrigeration, responding to MCDA inspections, and deciding how much realism or forgiveness they want through configuration.

The tone should stay practical and Minecraft-like. Processing is represented through workstations, items, progress states, particles, sounds, and abstract product visuals. Graphic or unnecessarily disturbing animal-processing imagery is intentionally out of bounds.

MCDA means Minecraft Department of Agriculture. It is fictional and must not use USDA branding, marks, uniforms, or real regulatory claims.

## Progression

### Early Game

The player operates alone or nearly alone.

- Manually processes a basic meat input into one basic product.
- Uses one manual processing station, one grinder, and one packaging station.
- Learns that product quality is affected by product condition, cleanliness, storage, and process success.
- Uses simple refrigerated storage to slow freshness loss.
- Accepts one customer order type and receives simple payment or reputation feedback.
- Encounters basic MCDA inspection feedback such as a write-up for poor cleanliness or unsafe storage.

Early game must be fully playable without employees, multiblocks, large capital investment, or complex supply chains.

### Mid Game

The player begins building a facility rather than a single work corner.

- Adds more workstations and basic machines.
- Hires employee villagers for defined jobs.
- Assigns work orders and begins managing throughput.
- Expands refrigerated storage and starts considering room layout.
- Balances cleanliness labor against production speed.
- Handles larger customer demand and early wholesale-style requests.
- Experiences escalating MCDA consequences if issues are ignored.

Mid game should feel like a growing operation with visible tradeoffs: faster production can create more cleaning load, poor storage can reduce quality, and untrained employees may create waste or errors.

### Late Game

The player manages a staffed, specialized facility.

- Builds scalable walk-in coolers and freezers.
- Manages compressors, condensers, evaporators, equipment wear, and overload risk.
- Runs multiple production lines and order types.
- Develops skilled employees and specialized teams.
- Tracks business finances, reputation, inspections, shutdown risk, and reinspection requirements.
- Chooses between safer, slower, higher-quality production or aggressive high-volume output.

Late game should be complex because the facility has grown, not because the user interface is obscure.

## Target Audience

The mod is for players who enjoy:

- Automation-adjacent Minecraft progression without losing manual interaction.
- Food, farming, logistics, and light industrial systems.
- Business simulation with consequences.
- Building functional facilities where layout matters.
- Configurable realism, from forgiving play to stricter management.

The initial release should favor stable, readable gameplay over maximum realism. A player should understand why a product improved, spoiled, failed inspection, or earned less money.

## Distinctive Qualities

- Manual work remains valid. Every essential early job must be performable by the player.
- Employees are a progression feature rather than a replacement for core gameplay.
- Cleanliness is continuous and affects quality, inspection risk, and business outcomes.
- Refrigeration is spatial and scalable, with room size and target temperature affecting capacity.
- MCDA inspections escalate from write-ups to fines, shutdowns, and reinspections.
- Product quality is not only a final random roll; it is shaped by handling, employee skill, cleanliness, storage, freshness, and process choices.
- The mod combines facility design, production, cold-chain management, and commerce in one coherent loop.

## Initial Non-Goals

These features are intentionally outside the initial scope unless later approved:

- Graphic slaughter, dismemberment, or realistic gore visuals.
- Use of USDA branding, marks, names, or real-world regulatory claims.
- A separate downloadable mod for every workstation or machine.
- Fully realistic food-safety law simulation.
- Complex animal genetics, veterinary systems, or ranch-management depth.
- Highly detailed thermodynamics before the core loop is fun and stable.
- Full ERP-style accounting, taxes, loans, payroll, or contracts.
- Multiplayer economy balancing beyond basic server correctness.
- Cross-mod automation compatibility beyond documented, stable capabilities needed by the vertical slice.
- Final art for every block and item before placeholder assets prove the gameplay.

## Product Pillars

- Hands-on first, management later.
- Abstract presentation, meaningful consequences.
- Configurable realism.
- Stable persistence and clear data ownership.
- Expansion-friendly public APIs with documented boundaries.
- Small, testable milestones before broad content volume.
