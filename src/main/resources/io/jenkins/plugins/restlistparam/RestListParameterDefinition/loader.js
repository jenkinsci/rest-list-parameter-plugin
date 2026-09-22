/*
 * Loads the entries of the REST List and REST Multi List parameters on the build form after the page has rendered.
 * Each parameter's view renders an empty skeleton (div[data-rlp-loader]) and binds a loader object whose
 * load(forced) method returns the entries, or the error of fetching them, as computed on the server.
 */
jQuery3.noConflict();
(function () {
  // loads in flight on this page; while it is not zero, the form is not submitted
  var inFlight = 0;
  // the Stapler proxy does not call back when a request fails, so a load without response ends after this margin
  var WATCHDOG_MARGIN_MS = 30000;
  // a button without a type attribute submits its form too, as core's Build button does
  var SUBMIT_BUTTONS = "button:not([type]), button[type=submit], input[type=submit]";
  // the Select2 theme of select2-jenkins.css
  var THEME = "jenkins";
  // a strict dropdown with fewer options is short enough to scan without a search field
  var SEARCH_THRESHOLD = 10;

  function byId(id) {
    return document.getElementById(id);
  }

  function parameterForms() {
    var forms = [];
    document.querySelectorAll("[data-rlp-loader]").forEach(function (wrapper) {
      var form = wrapper.closest("form");
      if (form && forms.indexOf(form) < 0) {
        forms.push(form);
      }
    });
    return forms;
  }

  function isSubmitButton(element) {
    return element && element.matches(SUBMIT_BUTTONS);
  }

  // Submit buttons are marked disabled with aria-disabled instead of the disabled attribute: a disabled button
  // receives no click, and an attempted submit must show which parameters are still loading.
  function updateSubmitButtons() {
    parameterForms().forEach(function (form) {
      form.querySelectorAll(SUBMIT_BUTTONS).forEach(function (button) {
        if (inFlight > 0) {
          button.setAttribute("aria-disabled", "true");
          button.classList.add("rlp-submit-blocked");
        }
        else {
          button.removeAttribute("aria-disabled");
          button.classList.remove("rlp-submit-blocked");
        }
      });
    });
  }

  function blockWhileLoading(event) {
    if (inFlight === 0) {
      return;
    }
    var form = event.type === "submit" ? event.target : event.target.closest && event.target.closest("form");
    if (!form || !form.querySelector("[data-rlp-loader]")) {
      return;
    }
    if (event.type === "click" && !isSubmitButton(event.target.closest("button, input"))) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    form.querySelectorAll("[data-rlp-state=loading]").forEach(function (wrapper) {
      byId(wrapper.dataset.rlpId + "-hint").hidden = false;
    });
  }

  // capturing listeners on document run before core's own submit handling of the form
  document.addEventListener("click", blockWhileLoading, true);
  document.addEventListener("submit", blockWhileLoading, true);

  function Parameter(wrapper) {
    this.wrapper = wrapper;
    this.id = wrapper.dataset.rlpId;
    this.mode = wrapper.dataset.rlpMode;
    this.proxy = window[wrapper.dataset.rlpLoader];
    this.timeoutMs = (parseInt(wrapper.dataset.rlpTimeout, 10) || 60) * 1000 + WATCHDOG_MARGIN_MS;
    this.select = byId(this.id + "-select");
    this.loading = byId(this.id + "-loading");
    this.refresh = byId(this.id + "-refresh");
    this.hint = byId(this.id + "-hint");
    this.error = byId(this.id + "-error");
    this.loaded = false;
    this.generation = 0;

    var self = this;
    this.refresh.addEventListener("click", function () {
      self.load(true);
    });
    this.error.querySelector(".rlp-retry").addEventListener("click", function () {
      self.load(true);
    });
  }

  Parameter.prototype.load = function (forced) {
    var self = this;
    var generation = ++this.generation;
    var finished = false;
    this.setLoading(true);
    inFlight++;
    updateSubmitButtons();

    function finish(response) {
      if (finished) {
        return;
      }
      finished = true;
      clearTimeout(watchdog);
      inFlight--;
      try {
        if (generation === self.generation) {
          self.render(response);
        }
      }
      finally {
        if (generation === self.generation) {
          self.setLoading(false);
        }
        updateSubmitButtons();
      }
    }

    var watchdog = setTimeout(function () {
      finish(null);
    }, this.timeoutMs);
    this.proxy.load(forced, function (t) {
      finish(t.responseObject ? t.responseObject() : null);
    });
  };

  Parameter.prototype.setLoading = function (loading) {
    this.wrapper.dataset.rlpState = loading ? "loading" : this.wrapper.dataset.rlpResult || "ready";
    this.loading.hidden = !loading;
    this.refresh.disabled = loading;
    if (loading) {
      this.error.hidden = true;
    }
    else {
      this.hint.hidden = true;
    }
  };

  Parameter.prototype.render = function (response) {
    var before = this.value();
    if (!response || response.status !== "ok") {
      this.wrapper.dataset.rlpResult = "error";
      this.showEntries([], {});
      this.showError(response);
    }
    else {
      this.wrapper.dataset.rlpResult = "ready";
      this.showEntries(response.entries || [], response);
      this.loaded = true;
    }
    // Referencing parameters (e.g. Active Choices) evaluated the value when the form rendered, before the entries
    // arrived; tell them about a changed value the way a user selection would (#204)
    if (this.value() !== before) {
      this.valueElement().dispatchEvent(new Event("change", { bubbles: true }));
    }
  };

  Parameter.prototype.valueElement = function () {
    return this.select;
  };

  /** The value the form would submit, as a string that is equal for equal values. */
  Parameter.prototype.value = function () {
    if (this.mode === "multi") {
      return JSON.stringify(Array.prototype.filter.call(this.select.options, function (element) {
        return element.selected;
      }).map(function (element) {
        return element.value;
      }));
    }
    return this.select.value;
  };

  Parameter.prototype.showError = function (response) {
    var message = response && response.message ? response.message : this.error.dataset.failedMessage;
    this.error.querySelector(".rlp-error-message").textContent = message;
    var detailsElement = this.error.querySelector(".rlp-details");
    var details = response && response.details;
    if (details) {
      var parts = [details.url];
      if (details.page !== undefined) {
        parts.push(this.error.dataset.labelPage + " " + details.page);
      }
      if (details.cause !== undefined) {
        parts.push(details.cause);
      }
      parts.push(details.durationMs + " " + this.error.dataset.labelDuration);
      detailsElement.textContent = parts.join(" · ");
      detailsElement.hidden = false;
    }
    else {
      detailsElement.textContent = "";
      detailsElement.hidden = true;
    }
    this.error.hidden = false;
  };

  Parameter.prototype.showEntries = function (entries, response) {
    if (this.mode === "custom") {
      this.showCustomSelect(entries, response);
    }
    else if (this.mode === "multi") {
      this.showMultiSelect(entries, response);
    }
    else {
      this.showSelect(entries);
    }
  };

  function option(value, text, selected) {
    var element = document.createElement("option");
    element.value = value;
    element.textContent = text;
    element.selected = selected;
    element.defaultSelected = selected;
    return element;
  }

  function selectedOption(select) {
    return select.selectedIndex >= 0 ? select.options[select.selectedIndex] : null;
  }

  /**
   * Offers text that is not shown by any option as a value of its own. Select2 turns what this returns into an
   * option of the dropdown, and into an option of the select when the user picks it.
   */
  function createTag(select) {
    return function (params) {
      var term = (params.term || "").trim();
      if (term === "") {
        return null;
      }
      var listed = Array.prototype.some.call(select.options, function (element) {
        return element.textContent === term;
      });
      return listed ? null : { id: term, text: term, rlpCustom: true };
    };
  }

  /** Labels the typed value in the dropdown as `Use "<text>"`, the entries by their display value. */
  function templateResult(select) {
    var label = select.dataset.customLabel || "";
    return function (data) {
      return data.rlpCustom === true ? label.replace("{0}", data.text) : data.text;
    };
  }

  Parameter.prototype.showCustomSelect = function (entries, response) {
    var select = this.select;
    var empty = select.querySelector("option[data-rlp-empty]");
    // after the first load the value stays, whether it is a listed entry or a value the user typed
    var current = this.loaded ? selectedOption(select) : null;
    var wanted = current ? current.value
                         : (typeof response.freeTextValue === "string" ? response.freeTextValue : "");
    var wantedText = current ? current.textContent : wanted;

    this.destroySelect2();
    select.querySelectorAll("option:not([data-rlp-empty])").forEach(function (element) {
      element.remove();
    });
    var listed = false;
    entries.forEach(function (entry) {
      var selected = !listed && entry.value === wanted;
      listed = listed || selected;
      select.appendChild(option(entry.value, entry.display, selected));
    });
    if (!listed && wanted !== "") {
      var custom = option(wanted, wantedText, true);
      custom.dataset.rlpCustom = "true";
      select.appendChild(custom);
    }
    if (empty) {
      empty.selected = !listed && wanted === "";
    }

    var options = {
      theme: THEME,
      // the search field is how a value that is not listed is typed, so it is always shown
      minimumResultsForSearch: 0,
      tags: true,
      createTag: createTag(select),
      templateResult: templateResult(select)
    };
    if (select.dataset.allowEmptyValue !== "true") {
      options.placeholder = "Select an option";
    }
    jQuery3(select).select2(options);
  };

  Parameter.prototype.showSelect = function (entries) {
    var select = this.select;
    var previous = this.loaded ? select.value : null;
    var kept = previous !== null
      && (previous === "" ? select.querySelector("option[data-rlp-empty]") !== null
                          : entries.some(function (entry) { return entry.value === previous; }));
    var empty = select.querySelector("option[data-rlp-empty]");

    this.destroySelect2();
    select.querySelectorAll("option:not([data-rlp-empty])").forEach(function (element) {
      element.remove();
    });
    var anySelected = false;
    entries.forEach(function (entry) {
      var selected = kept ? entry.value === previous : entry.selected === true;
      anySelected = anySelected || selected;
      select.appendChild(option(entry.value, entry.display, selected));
    });
    if (empty) {
      var emptySelected = kept ? previous === "" : !anySelected && empty.defaultSelected;
      empty.selected = emptySelected;
    }

    var options = { theme: THEME, minimumResultsForSearch: SEARCH_THRESHOLD };
    // When the parameter does not allow an empty value, use a placeholder so the
    // user is prompted to pick a real option. When empty values are allowed, the
    // first <option value=""> rendered by index.jelly must remain a real
    // selectable entry — select2 would otherwise consume it as the placeholder
    // slot and hide it from the dropdown.
    if (select.dataset.allowEmptyValue !== "true") {
      options.placeholder = "Select an option";
    }
    jQuery3(select).select2(options);
  };

  Parameter.prototype.showMultiSelect = function (entries, response) {
    var select = this.select;
    var tags = select.dataset.tags === "true";
    var previous = null;
    var freeForm = [];
    if (this.loaded) {
      previous = Array.prototype.filter.call(select.options, function (element) {
        return element.selected;
      }).map(function (element) {
        return element.value;
      });
      Array.prototype.forEach.call(select.options, function (element) {
        if (element.selected && (element.dataset.rlpFree === "true" || element.dataset.select2Tag === "true")) {
          freeForm.push(element.value);
        }
      });
    }

    this.destroySelect2();
    select.replaceChildren();
    var listed = [];
    entries.forEach(function (entry) {
      var selected = previous !== null ? previous.indexOf(entry.value) >= 0 : entry.selected === true;
      listed.push(entry.value);
      select.appendChild(option(entry.value, entry.display, selected));
    });
    var unmatched = previous !== null ? (tags ? freeForm : []) : (response.unmatchedDefaults || []);
    unmatched.forEach(function (value) {
      if (listed.indexOf(value) < 0) {
        var element = option(value, value, true);
        element.dataset.rlpFree = "true";
        select.appendChild(element);
      }
    });

    // An empty selection is the empty value, so there is no empty option and the placeholder is always shown.
    // With validation disabled, tags mode lets the user type entries that are not in the list.
    jQuery3(select).select2({
      theme: THEME,
      placeholder: "Select one or more options",
      tags: tags,
      createTag: createTag(select),
      templateResult: templateResult(select)
    });
  };

  Parameter.prototype.destroySelect2 = function () {
    var $select = jQuery3(this.select);
    if ($select.hasClass("select2-hidden-accessible")) {
      $select.select2("destroy");
    }
  };

  Behaviour.specify("[data-rlp-loader]", "rest-list-parameter-loader", 0, function (wrapper) {
    if (wrapper.dataset.rlpInitialized === "true") {
      return;
    }
    wrapper.dataset.rlpInitialized = "true";
    new Parameter(wrapper).load(false);
  });
})();
