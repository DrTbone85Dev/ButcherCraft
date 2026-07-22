package com.butchercraft.world;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityServiceTest {
    @Test
    void serviceCreatesAndSavesIdentityWhenRepositoryIsEmpty() {
        WorldIdentityService service = new WorldIdentityService(new WorldIdentityGenerator());
        InMemoryRepository repository = new InMemoryRepository(null);

        WorldIdentity identity = service.getOrCreate(444L, repository);

        assertEquals(identity, repository.identity.get());
        assertEquals(1, repository.saveCount.get());
        assertEquals(Optional.of(identity), service.currentIdentity());
    }

    @Test
    void serviceLoadsExistingIdentityWithoutRegeneratingOrResaving() {
        WorldIdentity existing = new WorldIdentityGenerator().generate(555L);
        WorldIdentityService service = new WorldIdentityService(new WorldIdentityGenerator());
        InMemoryRepository repository = new InMemoryRepository(existing);

        WorldIdentity identity = service.getOrCreate(999L, repository);

        assertEquals(existing, identity);
        assertEquals(0, repository.saveCount.get());
        assertEquals(Optional.of(existing), service.currentIdentity());
    }

    @Test
    void repeatedAccessReturnsPersistedIdentityEvenWhenSeedChanges() {
        WorldIdentityService service = new WorldIdentityService(new WorldIdentityGenerator());
        InMemoryRepository repository = new InMemoryRepository(null);

        WorldIdentity created = service.getOrCreate(101L, repository);
        WorldIdentity loaded = service.getOrCreate(202L, repository);

        assertEquals(created, loaded);
        assertEquals(1, repository.saveCount.get());
        assertTrue(repository.loadCount.get() >= 2);
    }

    private static final class InMemoryRepository implements WorldIdentityRepository {
        private final AtomicReference<WorldIdentity> identity;
        private final AtomicInteger loadCount = new AtomicInteger();
        private final AtomicInteger saveCount = new AtomicInteger();

        private InMemoryRepository(WorldIdentity identity) {
            this.identity = new AtomicReference<>(identity);
        }

        @Override
        public Optional<WorldIdentity> load() {
            loadCount.incrementAndGet();
            return Optional.ofNullable(identity.get());
        }

        @Override
        public void save(WorldIdentity identity) {
            saveCount.incrementAndGet();
            this.identity.set(identity);
        }
    }
}
