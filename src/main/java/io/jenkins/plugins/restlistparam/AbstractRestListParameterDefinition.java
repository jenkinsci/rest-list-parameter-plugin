package io.jenkins.plugins.restlistparam;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import hudson.model.SimpleParameterDefinition;
import io.jenkins.plugins.restlistparam.logic.RestValueService;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.MimeType;
import io.jenkins.plugins.restlistparam.model.Pagination;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.model.ValueOrder;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
  private String errorMsg;
  private List<ValueItem> values;
  private List<CustomHeader> customHeaders;
  // null means one request per fetch, as before pagination existed
  private Pagination pagination;

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

  void setErrorMsg(final String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public List<ValueItem> getValues() {
    Item context = null;

    if (Stapler.getCurrentRequest2() != null) {
      context = Stapler.getCurrentRequest2().findAncestorObject(Item.class);
    }

    Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, credentialId);

    ResultContainer<List<ValueItem>> container = RestValueService.get(
      getRestEndpoint(),
      credentials.orElse(null),
      getMimeType(),
      getCacheTime(),
      getValueExpression(),
      getDisplayExpression(),
      getFilter(),
      getValueOrder(),
      CustomHeader.resolveAll(getCustomHeaders(), context),
      getPagination());

    // An empty list is a valid response when an empty value may be submitted (#209)
    boolean expectedEmpty = allowEmptyValue && container.isNoValues();
    setErrorMsg(expectedEmpty ? "" : container.getErrorMsg().orElse(""));
    values = container.getValue();
    return values;
  }
}
