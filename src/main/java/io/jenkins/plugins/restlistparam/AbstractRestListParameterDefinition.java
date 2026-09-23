package io.jenkins.plugins.restlistparam;

import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.SimpleParameterDefinition;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The value source (endpoint, credentials, expressions, filter, order, cache, custom headers, pagination) and the
 * mode flags shared by the REST List and REST Multi List parameters.
 * <p>
 * The fields keep the names and declaration order they had in {@link RestListParameterDefinition},
 * because XStream writes inherited fields as flat, unqualified elements: this keeps the stored job
 * configuration of existing REST List Parameters unchanged.
 */
public abstract class AbstractRestListParameterDefinition extends SimpleParameterDefinition {
  private static final long serialVersionUID = 6204519541427309281L;
  protected static final RestListParameterGlobalConfig config = RestListParameterGlobalConfig.get();

  private final String restEndpoint;
  private final String credentialId;
  private final MimeType mimeType;
  private final String valueExpression;
  private String displayExpression;
  private ValueOrder valueOrder;
  private String defaultValue;
  private String filter;
  private Integer cacheTime;
  private boolean allowEmptyValue;
  private boolean enableValidation = true;
  // errorMsg and values are no longer written at runtime; they are kept because stored job configurations contain them
  private String errorMsg;
  private List<ValueItem> values;
  private List<CustomHeader> customHeaders;
  // null means one request per fetch, as before pagination existed
  private Pagination pagination;
  // The job found to hold this instance; transient because the definition is stored in config.xml and build.xml
  private transient volatile WeakReference<Job<?, ?>> owner;

  protected AbstractRestListParameterDefinition(final String name,
                                                final String description,
                                                final String restEndpoint,
                                                final String credentialId,
                                                final MimeType mimeType,
                                                final String valueExpression,
                                                final String displayExpression,
                                                final ValueOrder valueOrder,
                                                final String filter,
                                                final Integer cacheTime,
                                                final String defaultValue,
                                                final boolean allowEmptyValue,
                                                final boolean enableValidation,
                                                final List<ValueItem> values,
                                                final List<CustomHeader> customHeaders)
  {
    super(name);
    setDescription(description);
    this.restEndpoint = restEndpoint;
    this.mimeType = mimeType;
    this.valueExpression = valueExpression;
    this.credentialId = credentialId != null && !credentialId.trim().isEmpty() ? credentialId : "";
    if (mimeType == MimeType.APPLICATION_JSON) {
      this.displayExpression = displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$";
    }
    this.defaultValue = defaultValue != null && !defaultValue.trim().isEmpty() ? defaultValue : "";
    this.valueOrder = valueOrder != null ? valueOrder : ValueOrder.NONE;
    this.filter = !filter.isBlank() ? filter : ".*";
    this.cacheTime = cacheTime != null ? cacheTime : config.getCacheTime();
    this.allowEmptyValue = allowEmptyValue;
    this.enableValidation = enableValidation;
    this.errorMsg = "";
    this.values = values;
    this.customHeaders = customHeaders != null ? customHeaders : Collections.emptyList();
  }

  public String getRestEndpoint() {
    return restEndpoint;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public MimeType getMimeType() {
    return mimeType;
  }

  public String getValueExpression() {
    return valueExpression;
  }

  public String getFilter() {
    return filter;
  }

  public String getDisplayExpression() {
    if (mimeType == MimeType.APPLICATION_JSON) {
      return displayExpression != null && !displayExpression.isBlank() ? displayExpression : "$";
    }
    return "";
  }

  @DataBoundSetter
  public void setDisplayExpression(final String displayExpression) {
    this.displayExpression = displayExpression;
  }

  @DataBoundSetter
  public void setValueOrder(final ValueOrder valueOrder) {
    this.valueOrder = valueOrder;
  }

  public ValueOrder getValueOrder() {
    return valueOrder != null ? valueOrder : ValueOrder.NONE;
  }

  @DataBoundSetter
  public void setFilter(final String filter) {
    this.filter = filter;
  }

  public Integer getCacheTime() {
    return cacheTime != null ? cacheTime : config.getCacheTime();
  }

  @DataBoundSetter
  public void setCacheTime(final Integer cacheTime) {
    this.cacheTime = cacheTime;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  @DataBoundSetter
  public void setDefaultValue(final String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public boolean isAllowEmptyValue() {
    return allowEmptyValue;
  }

  @DataBoundSetter
  public void setAllowEmptyValue(final boolean allowEmptyValue) {
    this.allowEmptyValue = allowEmptyValue;
  }

  public boolean isEnableValidation() {
    return enableValidation;
  }

  @DataBoundSetter
  public void setEnableValidation(final boolean enableValidation) {
    this.enableValidation = enableValidation;
  }

  public List<CustomHeader> getCustomHeaders() {
    return customHeaders != null ? customHeaders : Collections.emptyList();
  }

  @DataBoundSetter
  public void setCustomHeaders(final List<CustomHeader> customHeaders) {
    this.customHeaders = customHeaders != null ? customHeaders : Collections.emptyList();
  }

  /**
   * @return How to follow paginated responses, or {@code null} to send a single request
   */
  public Pagination getPagination() {
    return pagination;
  }

  @DataBoundSetter
  public void setPagination(final Pagination pagination) {
    this.pagination = pagination;
  }

  /**
   * @return The error message stored in the job configuration by earlier versions; fetch errors are no longer
   * stored on the definition
   */
  public String getErrorMsg() {
    return errorMsg;
  }

  /**
   * @return The label of the option the build form offers for a typed value, with {@code {0}} where the typed text
   * goes; loader.js fills it in
   */
  public String getCustomValueLabel() {
    return Messages.RLP_BuildForm_CustomValue("{0}");
  }

  /**
   * Returns this parameter's entries, from the value cache while they are fresh, fetching them otherwise.
   * Credentials are resolved against the job of the current Stapler request, if any.
   *
   * @return The entries, or an empty list when fetching failed
   * @deprecated Use {@link ValueService#entries(AbstractRestListParameterDefinition, Item, boolean)}, which also
   * returns the error
   */
  @Deprecated
  public List<ValueItem> getValues() {
    return ValueService.entries(this, currentContext(), false).getValue();
  }

  /**
   * @param item A fetched entry
   * @return Whether the build form preselects the entry because of the default value
   */
  public abstract boolean isDefaultSelected(ValueItem item);

  /**
   * Creates the object the build form loads this parameter's entries through, for the job of the current request.
   */
  public ValueLoader createLoader() {
    return createLoader(currentContext());
  }

  /**
   * @param item The job the build form belongs to, or {@code null} outside any job
   * @return The object the build form loads this parameter's entries through
   */
  public ValueLoader createLoader(final Item item) {
    return new ValueLoader(this, item);
  }

  /**
   * The entry values that submitted elements are checked against when validation is enabled. The cached entries
   * are used when they are fresh and contain every element; otherwise the entries are fetched once, bypassing both
   * caches. Stale cached entries are never used.
   * <p>
   * Credentials, custom header credentials and the cache key are resolved against the job of the current web
   * request or, without one (Pipeline {@code build} step, CLI), against the job holding this definition.
   *
   * @param elements The non-empty elements of the submitted value
   * @return The entry values to check against; empty when the fetch failed
   */
  protected Set<String> entryValuesFor(final Collection<String> elements) {
    Item context = resolveContext();
    int cacheTime = getCacheTime() != null ? getCacheTime() : 0;
    List<ValueItem> fresh = ValueCache.get().getFresh(ValueCache.keyFor(this, context), cacheTime);
    if (fresh != null) {
      Set<String> cached = valuesOf(fresh);
      if (cached.containsAll(elements)) {
        return cached;
      }
    }

    ResultContainer<List<ValueItem>> fetched = ValueService.entries(this, context, true);
    if (fetched.getErrorMsg().isPresent()) {
      return Collections.emptySet();
    }
    return valuesOf(fetched.getValue());
  }

  private static Set<String> valuesOf(final List<ValueItem> entries) {
    Set<String> values = new HashSet<>();
    for (ValueItem item : entries) {
      if (item != null && item.getValue() != null) {
        values.add(item.getValue());
      }
    }
    return values;
  }

  /**
   * @return The job credentials are resolved against: the job of the current web request, otherwise the job holding
   * this definition, or {@code null} when there is neither
   */
  private Item resolveContext() {
    Item context = currentContext();
    return context != null ? context : findOwner();
  }

  /**
   * Finds the job whose parameter definitions contain this instance. The job found is remembered and reused while
   * it still holds this instance. Instances are compared by identity: another job may hold an equal definition with
   * different folder credentials. If several jobs hold this instance, which neither the UI nor XStream produce, the
   * first one found wins.
   *
   * @return The job holding this definition, or {@code null} when no job holds it
   */
  Job<?, ?> findOwner() {
    WeakReference<Job<?, ?>> cached = owner;
    Job<?, ?> job = cached != null ? cached.get() : null;
    if (job != null && holdsThis(job)) {
      return job;
    }
    job = null;
    // the caller may be anonymous (the Pipeline thread); the scan only locates the job, the credential lookup
    // uses the job's own authentication
    try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
      for (Job<?, ?> candidate : Jenkins.get().allItems(Job.class)) {
        if (holdsThis(candidate)) {
          job = candidate;
          break;
        }
      }
    }
    owner = job != null ? new WeakReference<>(job) : null;
    return job;
  }

  private boolean holdsThis(final Job<?, ?> job) {
    ParametersDefinitionProperty property = job.getProperty(ParametersDefinitionProperty.class);
    if (property == null) {
      return false;
    }
    for (ParameterDefinition definition : property.getParameterDefinitions()) {
      if (definition == this) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return The job of the current Stapler request, or {@code null} outside a request or any job
   */
  private static Item currentContext() {
    return Stapler.getCurrentRequest2() != null
      ? Stapler.getCurrentRequest2().findAncestorObject(Item.class)
      : null;
  }
}
