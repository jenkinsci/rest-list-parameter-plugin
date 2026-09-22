// Forwards the parameter's nested settings, including unsaved ones, to Test Configuration: the custom
// header rows and the pagination block. f:validateButton can only send flat fields, so on click
// (capture phase, before core's handler reads the fields) they are serialized into the hidden
// customHeadersJson and paginationJson inputs, which are cleared again right after so nothing extra
// is submitted with the job.
(function () {
  var FIELD = "_.customHeadersJson";
  var PAGINATION_FIELD = "_.paginationJson";
  var PAGINATION_KINDS = {
    LinkHeaderPagination: "linkHeader",
    ContinuationTokenPagination: "continuationToken",
  };

  function isTestButton(button) {
    var withList = button.getAttribute("data-validate-button-with") || "";
    return withList.split(",").indexOf("customHeadersJson") !== -1;
  }

  function fieldValue(row, name) {
    var input = row.querySelector('[name="_.' + name + '"]');
    return input && input.value != null ? input.value : "";
  }

  // the last matching element preceding the button: the one in the same parameter block,
  // matching how validateButton itself finds its fields
  function nearestPreceding(elements, button) {
    var match = null;
    for (var i = 0; i < elements.length; i++) {
      if (elements[i].compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING) {
        match = elements[i];
      }
    }
    return match;
  }

  function serializeHeaders(button) {
    var container = nearestPreceding(document.querySelectorAll(".rlp-custom-headers"), button);
    var headers = [];
    if (container) {
      container.querySelectorAll(".repeated-chunk:not(.to-be-removed)").forEach(function (row) {
        headers.push({
          name: fieldValue(row, "name"),
          value: fieldValue(row, "value"),
          credentialId: fieldValue(row, "credentialId"),
          valuePrefix: fieldValue(row, "valuePrefix"),
        });
      });
    }
    return JSON.stringify(headers);
  }

  // hidden dropdown entries (the strategies not selected) are marked field-disabled by core
  function activeFieldValue(container, name) {
    var inputs = container.querySelectorAll('[name="' + name + '"]');
    for (var i = 0; i < inputs.length; i++) {
      if (!inputs[i].closest("[field-disabled]")) {
        return inputs[i].value != null ? inputs[i].value : "";
      }
    }
    return "";
  }

  // {kind, maxPages, tokenExpression, queryParameter} of the selected strategy, or "" when unchecked
  function serializePagination(button) {
    var enabled = nearestPreceding(document.getElementsByName("paginationEnabled"), button);
    var container = nearestPreceding(document.querySelectorAll(".rlp-pagination"), button);
    if (!enabled || !enabled.checked || !container) {
      return "";
    }
    var clazz = activeFieldValue(container, "$class") || activeFieldValue(container, "stapler-class");
    var kind = PAGINATION_KINDS[clazz.substring(clazz.lastIndexOf(".") + 1)];
    if (!kind) {
      return "";
    }
    return JSON.stringify({
      kind: kind,
      maxPages: activeFieldValue(container, "_.maxPages"),
      tokenExpression: activeFieldValue(container, "_.tokenExpression"),
      queryParameter: activeFieldValue(container, "_.queryParameter"),
    });
  }

  function clearAll() {
    [FIELD, PAGINATION_FIELD].forEach(function (name) {
      var fields = document.getElementsByName(name);
      for (var i = 0; i < fields.length; i++) {
        fields[i].value = "";
      }
    });
  }

  document.addEventListener(
    "click",
    function (event) {
      var button = event.target.closest && event.target.closest("button.validate-button");
      if (!button || !isTestButton(button)) {
        return;
      }
      var field = nearestPreceding(document.getElementsByName(FIELD), button);
      if (!field) {
        return;
      }
      field.value = serializeHeaders(button);
      var paginationField = nearestPreceding(document.getElementsByName(PAGINATION_FIELD), button);
      if (paginationField) {
        paginationField.value = serializePagination(button);
      }
      // validateButton reads its fields synchronously in the click handler
      setTimeout(clearAll, 0);
    },
    true,
  );

  document.addEventListener("submit", clearAll, true);
})();
