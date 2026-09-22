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
    this.input = byId(this.id + "-input");
    this.datalist = byId(this.id + "-datalist");
    this.loading = byId(this.id + "-loading");
    this.refresh = byId(this.id + "-refresh");
    this.hint = byId(this.id + "-hint");
    this.error = byId(this.id + "-error");
    this.loaded = false;
    this.edited = false;
    this.generation = 0;

    var self = this;
    if (this.input) {
      this.input.addEventListener("input", function () {
        self.edited = true;
      });
    }
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
    return this.mode === "freetext" ? this.input : this.select;
  };

  /** The value the form would submit, as a string that is equal for equal values. */
  Parameter.prototype.value = function () {
    if (this.mode === "freetext") {
      return this.input.value;
    }
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
    if (this.mode === "freetext") {
      this.showSuggestions(entries, response);
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

  Parameter.prototype.showSuggestions = function (entries, response) {
    this.datalist.replaceChildren();
    var self = this;
    entries.forEach(function (entry) {
      self.datalist.appendChild(option(entry.value, entry.display, false));
    });
    // the default is shown verbatim until the entries arrive; text the user typed is kept
    if (!this.loaded && !this.edited && typeof response.freeTextValue === "string") {
      this.input.value = response.freeTextValue;
    }
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

    var options = { theme: "bootstrap4" };
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
    jQuery3(select).select2({ theme: "bootstrap4", placeholder: "Select one or more options", tags: tags });
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
