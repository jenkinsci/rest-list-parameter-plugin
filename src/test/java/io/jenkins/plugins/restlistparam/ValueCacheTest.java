package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.ValueItem;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ValueCacheTest {

  private static final List<ValueItem> ENTRIES = List.of(new ValueItem("a", "A"), new ValueItem("b", "B"));

  private final MutableClock clock = new MutableClock();

  @Test
  void entriesAreFreshForTheCacheTimeAfterTheirFetch() {
    ValueCache cache = new ValueCache(10, clock);
    ValueCache.Key key = key("p");
    cache.put(key, ENTRIES);

    clock.advance(Duration.ofMinutes(9).plusSeconds(59));
    assertEquals(ENTRIES, cache.getFresh(key, 10));

    clock.advance(Duration.ofSeconds(1));
    assertNull(cache.getFresh(key, 10), "fetched exactly 10 minutes ago");
  }

  @Test
  void cacheTimeIsAppliedOnRead() {
    ValueCache cache = new ValueCache(10, clock);
    ValueCache.Key key = key("p");
    cache.put(key, ENTRIES);
    clock.advance(Duration.ofMinutes(3));

    assertNull(cache.getFresh(key, 2));
    assertEquals(ENTRIES, cache.getFresh(key, 5));
    assertNull(cache.getFresh(key, 0), "cache time 0 never reads from the cache");
  }

  @Test
  void putReplacesEntriesAndRestartsTheirCacheTime() {
    ValueCache cache = new ValueCache(10, clock);
    ValueCache.Key key = key("p");
    cache.put(key, ENTRIES);
    clock.advance(Duration.ofMinutes(8));
    List<ValueItem> newer = List.of(new ValueItem("c", "C"));
    cache.put(key, newer);
    clock.advance(Duration.ofMinutes(8));

    assertEquals(newer, cache.getFresh(key, 10));
  }

  @Test
  void keysDifferingInAnyPartAreSeparate() {
    ValueCache cache = new ValueCache(10, clock);
    cache.put(new ValueCache.Key("job", "p", "f1"), ENTRIES);

    assertNotNull(cache.getFresh(new ValueCache.Key("job", "p", "f1"), 10));
    assertNull(cache.getFresh(new ValueCache.Key("other", "p", "f1"), 10));
    assertNull(cache.getFresh(new ValueCache.Key("", "p", "f1"), 10));
    assertNull(cache.getFresh(new ValueCache.Key("job", "q", "f1"), 10));
    assertNull(cache.getFresh(new ValueCache.Key("job", "p", "f2"), 10));
  }

  @Test
  void leastRecentlyUsedEntryIsEvictedFirst() {
    ValueCache cache = new ValueCache(2, clock);
    cache.put(key("a"), ENTRIES);
    cache.put(key("b"), ENTRIES);
    cache.getFresh(key("a"), 10);

    cache.put(key("c"), ENTRIES);

    assertEquals(2, cache.size());
    assertNotNull(cache.getFresh(key("a"), 10));
    assertNull(cache.getFresh(key("b"), 10), "b was used least recently");
    assertNotNull(cache.getFresh(key("c"), 10));
  }

  @Test
  void clearRemovesAllEntries() {
    ValueCache cache = new ValueCache(10, clock);
    cache.put(key("a"), ENTRIES);
    cache.put(key("b"), ENTRIES);

    cache.clear();

    assertEquals(0, cache.size());
    assertNull(cache.getFresh(key("a"), 10));
  }

  @Test
  void defaultBoundIsOneThousand() {
    ValueCache cache = new ValueCache(0, clock);
    for (int i = 0; i < 1001; i++) {
      cache.put(key("p" + i), ENTRIES);
    }

    assertEquals(1000, cache.size());
    assertNull(cache.getFresh(key("p0"), 10));
    assertEquals("io.jenkins.plugins.restlistparam.ValueCache.maxEntries", ValueCache.MAX_ENTRIES_PROPERTY);
  }

  private static ValueCache.Key key(final String name) {
    return new ValueCache.Key("job", name, "fingerprint");
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(final Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
