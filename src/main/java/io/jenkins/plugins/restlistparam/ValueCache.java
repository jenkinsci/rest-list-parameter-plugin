package io.jenkins.plugins.restlistparam;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Item;
import hudson.util.Secret;
import io.jenkins.plugins.restlistparam.model.ContinuationTokenPagination;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import jenkins.util.SystemProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The processed entries of successful fetches (all pages, after extraction, filter and order), kept in memory for
 * each parameter's cache time, whatever caching headers the endpoint sent.
 * <p>
 * Entries are kept per job, parameter name and value-source configuration, so jobs with different credentials never
 * share entries and a changed configuration makes earlier entries unreachable. The number of cached parameters is
 * bounded; the least recently used one is evicted first. Nothing is written to disk.
 */
public final class ValueCache {
  /** System property overriding {@link #DEFAULT_MAX_ENTRIES}. */
  public static final String MAX_ENTRIES_PROPERTY = ValueCache.class.getName() + ".maxEntries";
  public static final int DEFAULT_MAX_ENTRIES = 1000;

  private static final ValueCache INSTANCE =
    new ValueCache(SystemProperties.getInteger(MAX_ENTRIES_PROPERTY, DEFAULT_MAX_ENTRIES), Clock.systemUTC());

  private final Object lock = new Object();
  private Clock clock;
  private final Map<Key, Cached> entries;

  /**
   * @param maxEntries How many parameters' entries are kept at most; non-positive means the default
   * @param clock      The clock fetch times are taken from
   */
  ValueCache(final int maxEntries, final Clock clock) {
    int bound = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
    this.clock = clock;
    this.entries = new LruMap(bound);
  }

  public static ValueCache get() {
    return INSTANCE;
  }

  /**
   * Starts every Jenkins instance with an empty cache, also when one JVM runs several instances one after another.
   */
  @Initializer(after = InitMilestone.PLUGINS_STARTED)
  public static void clearOnStartup() {
    INSTANCE.clear();
  }

  /**
   * Replaces the clock fetch times and freshness are taken from. For tests.
   */
  void setClock(final Clock clock) {
    synchronized (lock) {
      this.clock = clock;
    }
  }

  /**
   * @param key              The parameter's key
   * @param cacheTimeMinutes The parameter's cache time; 0 or less means nothing is fresh
   * @return The cached entries if they were fetched less than {@code cacheTimeMinutes} ago, otherwise {@code null}
   */
  public List<ValueItem> getFresh(final Key key, final int cacheTimeMinutes) {
    if (cacheTimeMinutes <= 0) {
      return null;
    }
    synchronized (lock) {
      Cached cached = entries.get(key);
      if (cached == null) {
        return null;
      }
      Duration age = Duration.between(cached.fetchedAt, clock.instant());
      return age.compareTo(Duration.ofMinutes(cacheTimeMinutes)) < 0 ? cached.entries : null;
    }
  }

  /**
   * Stores the entries of a successful fetch, fetched now, replacing earlier entries of the same key.
   */
  public void put(final Key key, final List<ValueItem> values) {
    List<ValueItem> copy = Collections.unmodifiableList(new ArrayList<>(values));
    synchronized (lock) {
      entries.put(key, new Cached(copy, clock.instant()));
    }
  }

  /**
   * Removes the entries of all parameters.
   */
  public void clear() {
    synchronized (lock) {
      entries.clear();
    }
  }

  /**
   * @return How many parameters have entries cached, fresh or not
   */
  public int size() {
    synchronized (lock) {
      return entries.size();
    }
  }

  /**
   * @param definition The parameter
   * @param context    The job the entries are fetched for, or {@code null} outside any job
   * @return The key of {@code definition}'s entries in {@code context}
   */
  public static Key keyFor(final AbstractRestListParameterDefinition definition, final Item context) {
    return new Key(context != null ? context.getFullName() : "", definition.getName(), fingerprint(definition));
  }

  /**
   * A SHA-256 hash of the value-source configuration: endpoint, credential ID, MIME type, value and display
   * expressions, filter, order, custom headers (with the static value hashed) and pagination.
   */
  static String fingerprint(final AbstractRestListParameterDefinition definition) {
    Fingerprint fingerprint = new Fingerprint()
      .add(definition.getRestEndpoint())
      .add(definition.getCredentialId())
      .add(String.valueOf(definition.getMimeType()))
      .add(definition.getValueExpression())
      .add(definition.getDisplayExpression())
      .add(definition.getFilter())
      .add(String.valueOf(definition.getValueOrder()));
    List<CustomHeader> headers = definition.getCustomHeaders();
    fingerprint.add(String.valueOf(headers.size()));
    for (CustomHeader header : headers) {
      if (header == null) {
        fingerprint.add("null");
        continue;
      }
      fingerprint.add(header.getName())
                 .add(header.getValuePrefix())
                 .add(header.getCredentialId())
                 .add(sha256(Objects.toString(Secret.toString(header.getValue()), "")));
    }
    Pagination pagination = definition.getPagination();
    if (pagination == null) {
      fingerprint.add("none");
    }
    else {
      fingerprint.add(pagination.getClass().getName()).add(String.valueOf(pagination.getEffectiveMaxPages()));
      if (pagination instanceof ContinuationTokenPagination) {
        ContinuationTokenPagination token = (ContinuationTokenPagination) pagination;
        fingerprint.add(token.getTokenExpression()).add(token.getQueryParameter());
      }
    }
    return fingerprint.digest();
  }

  private static String sha256(final String value) {
    return HexFormat.of().formatHex(newDigest().digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    }
    catch (NoSuchAlgorithmException ex) {
      // every Java platform provides SHA-256
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Hashes a sequence of strings; each one is length-prefixed, so different sequences never hash alike by
   * concatenation.
   */
  private static final class Fingerprint {
    private final MessageDigest digest = newDigest();

    Fingerprint add(final String value) {
      if (value == null) {
        digest.update((byte) 0);
        return this;
      }
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      digest.update((byte) 1);
      digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
      digest.update((byte) ':');
      digest.update(bytes);
      return this;
    }

    String digest() {
      return HexFormat.of().formatHex(digest.digest());
    }
  }

  /**
   * An access-ordered map that drops its least recently used entry beyond {@code bound} entries.
   */
  private static final class LruMap extends LinkedHashMap<Key, Cached> {
    private static final long serialVersionUID = 1L;
    private final int bound;

    private LruMap(final int bound) {
      super(16, 0.75f, true);
      this.bound = bound;
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<Key, Cached> eldest) {
      return size() > bound;
    }
  }

  private static final class Cached {
    private final List<ValueItem> entries;
    private final Instant fetchedAt;

    private Cached(final List<ValueItem> entries, final Instant fetchedAt) {
      this.entries = entries;
      this.fetchedAt = fetchedAt;
    }
  }

  /**
   * Identifies one parameter's entries: the job's full name ({@code ""} outside any job), the parameter name and the
   * fingerprint of its value-source configuration.
   */
  public static final class Key {
    private final String context;
    private final String name;
    private final String fingerprint;

    public Key(final String context, final String name, final String fingerprint) {
      this.context = context;
      this.name = name;
      this.fingerprint = fingerprint;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Key key = (Key) o;
      return Objects.equals(context, key.context)
        && Objects.equals(name, key.name)
        && Objects.equals(fingerprint, key.fingerprint);
    }

    @Override
    public int hashCode() {
      return Objects.hash(context, name, fingerprint);
    }

    @Override
    public String toString() {
      return context + "/" + name + "@" + fingerprint;
    }
  }
}
