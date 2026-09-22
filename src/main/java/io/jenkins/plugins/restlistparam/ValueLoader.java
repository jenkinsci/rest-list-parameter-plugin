package io.jenkins.plugins.restlistparam;

import hudson.model.Item;
import io.jenkins.plugins.restlistparam.logic.ValueService;
import io.jenkins.plugins.restlistparam.model.FetchErrorDetails;
import io.jenkins.plugins.restlistparam.model.ResultContainer;
import io.jenkins.plugins.restlistparam.model.ValueItem;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.util.List;

/**
 * Loads the entries of one rendered parameter for the build form. The view binds it to the page with
 * {@code <st:bind>}, so it holds the exact definition instance that was rendered (for example the Rebuild copy with
 * the previous value as default) and the job the form belongs to.
 */
public final class ValueLoader {
  private final AbstractRestListParameterDefinition definition;
  private final Item item;

  /**
   * @param definition The rendered parameter
   * @param item       The job the form belongs to, or {@code null} outside any job
   */
  ValueLoader(final AbstractRestListParameterDefinition definition, final Item item) {
    this.definition = definition;
    this.item = item;
  }

  /**
   * Returns the parameter's entries, or the error of fetching them. Requires Build on the job, or Administer when
   * there is no job. Request details of a failed fetch are only included for callers who may configure the job.
   *
   * <pre>
   * { "status": "ok", "entries": [ { "value", "display", "selected" } ],
   *   "freeTextValue": "...",           // REST List Parameter with validation disabled
   *   "unmatchedDefaults": [ "..." ] }  // REST Multi List Parameter with validation disabled
   * { "status": "error", "message": "...",
   *   "details": { "url", "page", "cause", "durationMs" } }  // Configure only
   * </pre>
   *
   * @param forced Whether to bypass the value cache and the HTTP response cache
   */
  @JavaScriptMethod
  public JSONObject load(final boolean forced) {
    if (item != null) {
      item.checkPermission(Item.BUILD);
    }
    else {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }

    ResultContainer<List<ValueItem>> result = ValueService.entries(definition, item, forced);
    JSONObject json = new JSONObject();
    if (result.getErrorMsg().isPresent()) {
      json.put("status", "error");
      json.put("message", result.getErrorMsg().get());
      if (canConfigure() && result.getErrorDetails().isPresent()) {
        json.put("details", toJson(result.getErrorDetails().get()));
      }
      return json;
    }

    List<ValueItem> entries = result.getValue();
    JSONArray array = new JSONArray();
    for (ValueItem entry : entries) {
      JSONObject option = new JSONObject();
      option.put("value", entry.getValue());
      option.put("display", entry.getDisplayValue());
      option.put("selected", definition.isDefaultSelected(entry));
      array.add(option);
    }
    json.put("status", "ok");
    json.put("entries", array);
    if (definition instanceof RestListParameterDefinition && !definition.isEnableValidation()) {
      json.put("freeTextValue", ((RestListParameterDefinition) definition).resolveFreeTextDefault(entries));
    }
    if (definition instanceof RestMultiListParameterDefinition) {
      json.put("unmatchedDefaults",
        JSONArray.fromObject(((RestMultiListParameterDefinition) definition).getUnmatchedDefaults(entries)));
    }
    return json;
  }

  private boolean canConfigure() {
    return item != null ? item.hasPermission(Item.CONFIGURE) : Jenkins.get().hasPermission(Jenkins.ADMINISTER);
  }

  private static JSONObject toJson(final FetchErrorDetails details) {
    JSONObject json = new JSONObject();
    json.put("url", details.getUrl());
    if (details.getPage() != null) {
      json.put("page", details.getPage());
    }
    if (details.getCause() != null) {
      json.put("cause", details.getCause());
    }
    json.put("durationMs", details.getDurationMs());
    return json;
  }
}
