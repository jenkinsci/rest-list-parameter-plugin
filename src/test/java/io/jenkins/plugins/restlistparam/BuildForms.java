package io.jenkins.plugins.restlistparam;

import hudson.model.Job;
import org.htmlunit.AjaxController;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opens "Build with Parameters" pages in HtmlUnit and waits until the REST parameters on them have loaded
 * their entries in the background.
 */
public final class BuildForms {
  /** How long {@link #waitUntilLoaded(HtmlPage)} waits by default. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /** Selects a REST parameter whose entries are still loading. */
  public static final String LOADING = "[data-rlp-state=loading]";

  private BuildForms() {
  }

  /**
   * Opens the build form of {@code job} with a new web client and waits until every REST parameter has loaded.
   */
  public static HtmlPage open(final JenkinsRule r, final Job<?, ?> job) throws Exception {
    return waitUntilLoaded(openWithoutWaiting(r.createWebClient(), job));
  }

  /**
   * Opens the build form of {@code job} without waiting for the REST parameters: they load in the background, as in
   * a browser, instead of synchronously while the page loads.
   */
  public static HtmlPage openWithoutWaiting(final JenkinsRule.WebClient wc, final Job<?, ?> job) throws Exception {
    // JenkinsRule's web client resynchronizes requests made while the page loads, and its getPage(String) waits for
    // background JavaScript; keep the requests asynchronous and load the page through HtmlUnit's getPage(URL)
    wc.setAjaxController(new AjaxController());
    // Jenkins serves the parameters form for a GET on build, with status 405
    wc.setThrowExceptionOnFailingStatusCode(false);
    return wc.getPage(new URL(wc.getContextPath() + job.getUrl() + "build?delay=0sec"));
  }

  public static HtmlPage waitUntilLoaded(final HtmlPage page) {
    return waitUntilLoaded(page, DEFAULT_TIMEOUT);
  }

  /** The {@code div[name=parameter]} of the REST parameter {@code name}. */
  public static DomElement parameter(final HtmlPage page, final String name) {
    for (DomNode node : page.querySelectorAll("div[name=parameter]")) {
      DomNode hidden = node.querySelector("input[name=name]");
      if (hidden != null && name.equals(((DomElement) hidden).getAttribute("value"))) {
        return (DomElement) node;
      }
    }
    throw new AssertionError("no parameter " + name);
  }

  /** Opens the Select2 dropdown of the parameter {@code name}; its panel is appended to the page's body. */
  public static void openDropdown(final HtmlPage page, final String name) throws Exception {
    DomElement selection = parameter(page, name).querySelector(".select2-selection");
    assertNotNull(selection, "select2 did not enhance the parameter");
    ((HtmlElement) selection).click();
    waitUntil(page, "the dropdown to open", () -> page.querySelector(".select2-container--open") != null);
  }

  /** Opens the dropdown of the parameter {@code name} and types {@code text} into its search field. */
  public static HtmlInput typeInDropdown(final HtmlPage page, final String name, final String text)
    throws Exception
  {
    openDropdown(page, name);
    HtmlInput search = (HtmlInput) searchField(page);
    search.type(text);
    // select2 filters the options in the field's input handler, so the options match once the text is in
    waitUntil(page, "the search field to hold " + text, () -> text.equals(search.getValue()));
    return search;
  }

  /** Types {@code text} into the dropdown of the parameter {@code name} and picks the offered custom value. */
  public static void pickCustomValue(final HtmlPage page, final String name, final String text) throws Exception {
    typeInDropdown(page, name, text);
    assertNotNull(customOption(page, text), "no custom value offered for " + text);
    pickShownOption(page, customLabel(text));
  }

  /** Opens the dropdown of the parameter {@code name} and picks the option shown as {@code text}. */
  public static void pickOption(final HtmlPage page, final String name, final String text) throws Exception {
    openDropdown(page, name);
    pickShownOption(page, text);
  }

  /** Picks the option shown as {@code text} in the dropdown that is already open. */
  public static void pickShownOption(final HtmlPage page, final String text) throws Exception {
    for (DomNode option : resultOptions(page)) {
      if (text.equals(option.asNormalizedText())) {
        ((HtmlElement) option).click();
        waitUntil(page, "the dropdown to close", () -> page.querySelector(".select2-container--open") == null);
        return;
      }
    }
    throw new AssertionError("no option " + text + " in the open dropdown");
  }

  /**
   * Runs background JavaScript until {@code condition} holds, so that a loaded machine only makes the test slower
   * instead of failing it.
   */
  public static void waitUntil(final HtmlPage page, final String what, final BooleanSupplier condition) {
    long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
    while (!condition.getAsBoolean()) {
      page.getWebClient().waitForBackgroundJavaScript(100);
      if (condition.getAsBoolean()) {
        return;
      }
      if (System.nanoTime() > deadline) {
        fail("waited " + DEFAULT_TIMEOUT.toSeconds() + " s for " + what);
      }
    }
  }

  /** The option offering {@code text} as a custom value, or {@code null} when it is not offered. */
  public static DomNode customOption(final HtmlPage page, final String text) {
    for (DomNode option : resultOptions(page)) {
      if (customLabel(text).equals(option.asNormalizedText())) {
        return option;
      }
    }
    return null;
  }

  /** The label of the option that offers {@code text} as a custom value. */
  public static String customLabel(final String text) {
    return Messages.RLP_BuildForm_CustomValue(text);
  }

  /** The options of the open dropdown. */
  public static List<DomNode> resultOptions(final HtmlPage page) {
    return List.copyOf(page.querySelectorAll(".select2-container--open .select2-results__option"));
  }

  /** The search field container of the open dropdown; it carries {@code select2-search--hide} when hidden. */
  public static DomElement searchContainer(final HtmlPage page) {
    DomElement search = page.querySelector(".select2-container--open .select2-search--dropdown");
    assertNotNull(search, "dropdown is not open");
    return search;
  }

  /** The search field of the open dropdown, of the panel or, for a multi-select, of the control itself. */
  public static DomElement searchField(final HtmlPage page) {
    DomElement search = page.querySelector(".select2-container--open .select2-search__field");
    assertNotNull(search, "no search field");
    return search;
  }

  /**
   * Runs background JavaScript until no REST parameter on {@code page} shows its loading indicator.
   *
   * @return {@code page}, for chaining
   */
  public static HtmlPage waitUntilLoaded(final HtmlPage page, final Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      page.getWebClient().waitForBackgroundJavaScript(100);
      if (page.querySelectorAll(LOADING).isEmpty()) {
        return page;
      }
      if (System.nanoTime() > deadline) {
        fail("REST parameters still loading after " + timeout.toSeconds() + " s");
      }
    }
  }
}
