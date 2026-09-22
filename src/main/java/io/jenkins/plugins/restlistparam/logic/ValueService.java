package io.jenkins.plugins.restlistparam.logic;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import io.jenkins.plugins.restlistparam.AbstractRestListParameterDefinition;
import io.jenkins.plugins.restlistparam.ValueCache;
import io.jenkins.plugins.restlistparam.model.CustomHeader;
import io.jenkins.plugins.restlistparam.model.FetchOptions;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import io.jenkins.plugins.restlistparam.util.CredentialsUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The one way to get a parameter's entries, used by the build form, submission-time validation and the deprecated
 * {@link AbstractRestListParameterDefinition#getValues()}. It never writes to the definition.
 */
public final class ValueService {
  private ValueService() {
    throw new IllegalStateException("Static Logic class");
  }

  /**
   * Returns the parameter's entries from the value cache while they are fresh, and fetches them otherwise.
   * Credentials and custom headers are resolved in {@code context}. Only successful fetches are cached, and only
   * when the parameter's cache time is greater than 0. An empty result is a success when the parameter allows an
   * empty value.
   *
   * @param definition The parameter
   * @param context    The job the entries are for, or {@code null} outside any job
   * @param forced     Whether to skip the value cache and the HTTP response cache and contact the endpoint
   * @return The entries, or the error message and, for a failed fetch, its request metadata
   */
  public static ResultContainer<List<ValueItem>> entries(final AbstractRestListParameterDefinition definition,
                                                         final Item context,
                                                         final boolean forced)
  {
    ValueCache cache = ValueCache.get();
    ValueCache.Key key = ValueCache.keyFor(definition, context);
    int cacheTime = definition.getCacheTime() != null ? definition.getCacheTime() : 0;

    if (!forced) {
      List<ValueItem> fresh = cache.getFresh(key, cacheTime);
      if (fresh != null) {
        return new ResultContainer<>(fresh);
      }
    }

    Optional<StandardCredentials> credentials = CredentialsUtils.findCredentials(context, definition.getCredentialId());
    ResultContainer<List<ValueItem>> result = RestValueService.get(
      definition.getRestEndpoint(),
      credentials.orElse(null),
      definition.getMimeType(),
      cacheTime,
      definition.getValueExpression(),
      definition.getDisplayExpression(),
      definition.getFilter(),
      definition.getValueOrder(),
      CustomHeader.resolveAll(definition.getCustomHeaders(), context),
      definition.getPagination(),
      FetchOptions.withGlobalTimeout(forced));

    // An empty list is a valid response when an empty value may be submitted (#209)
    if (definition.isAllowEmptyValue() && result.isNoValues()) {
      result = new ResultContainer<>(Collections.emptyList());
    }

    if (!result.getErrorMsg().isPresent() && cacheTime > 0) {
      cache.put(key, result.getValue());
    }
    return result;
  }
}
