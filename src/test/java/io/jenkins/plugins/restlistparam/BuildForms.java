package io.jenkins.plugins.restlistparam;

import hudson.model.Job;
import org.htmlunit.AjaxController;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.time.Duration;

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
