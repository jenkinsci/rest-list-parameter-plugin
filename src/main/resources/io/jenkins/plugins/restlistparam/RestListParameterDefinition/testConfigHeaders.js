// Forwards the parameter's custom header rows, including unsaved ones, to Test Configuration.
// f:validateButton can only send flat fields, so on click (capture phase, before core's handler
// reads the fields) the rows are serialized into the hidden customHeadersJson input, which is
// cleared again right after so nothing extra is submitted with the job.
(function () {
  var FIELD = "_.customHeadersJson";

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

  function clearAll() {
    var fields = document.getElementsByName(FIELD);
    for (var i = 0; i < fields.length; i++) {
      fields[i].value = "";
    }
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
      // validateButton reads its fields synchronously in the click handler
      setTimeout(clearAll, 0);
    },
    true,
  );

  document.addEventListener("submit", clearAll, true);
})();
