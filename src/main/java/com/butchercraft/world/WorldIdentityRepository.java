package com.butchercraft.world;

import com.butchercraft.world.identity.WorldIdentity;

import java.util.Optional;

interface WorldIdentityRepository {
    Optional<WorldIdentity> load();

    void save(WorldIdentity identity);
}
