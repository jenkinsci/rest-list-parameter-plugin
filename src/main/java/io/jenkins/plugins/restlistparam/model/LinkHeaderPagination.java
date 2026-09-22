package io.jenkins.plugins.restlistparam.model;

import hudson.Extension;
import io.jenkins.plugins.restlistparam.Messages;
import okhttp3.HttpUrl;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Follows the {@code Link} response header entry whose {@code rel} includes {@code next} (RFC 8288),
 * as sent by GitHub, GitLab, Gitea or Harbor.
 */
public class LinkHeaderPagination extends Pagination {
  private static final long serialVersionUID = 1L;

  // entries are separated by commas that precede the next "<uri>", so commas inside URLs are safe
  private static final Pattern ENTRY_SEPARATOR = Pattern.compile(",(?=\\s*<)");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @DataBoundConstructor
  public LinkHeaderPagination() {
    // maxPages is the only setting
  }

  @Override
  public Optional<NextPage> next(final Page page) {
    return findNextLink(page.getHeaders().values("Link"), page.getRequestUrl())
      .map(url -> new NextPage(url, null));
  }

  /**
   * Finds the first {@code rel="next"} link among {@code Link} header values.
   *
   * @param linkHeaders All {@code Link} header values of a response
   * @param base        The URL relative links are resolved against
   * @return The resolved next link, or empty when there is none; unparsable entries are ignored
   */
  static Optional<HttpUrl> findNextLink(final Iterable<String> linkHeaders, final HttpUrl base) {
    for (String header : linkHeaders) {
      for (String entry : ENTRY_SEPARATOR.split(header)) {
        Optional<HttpUrl> next = parseNextEntry(entry.trim(), base);
        if (next.isPresent()) {
          return next;
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<HttpUrl> parseNextEntry(final String entry, final HttpUrl base) {
    int end = entry.indexOf('>');
    if (!entry.startsWith("<") || end < 0) {
      return Optional.empty();
    }
    String target = entry.substring(1, end).trim();
    for (String param : entry.substring(end + 1).split(";")) {
      int eq = param.indexOf('=');
      if (eq < 0 || !param.substring(0, eq).trim().equalsIgnoreCase("rel")) {
        continue;
      }
      String rel = unquote(param.substring(eq + 1).trim());
      for (String relation : WHITESPACE.split(rel)) {
        if (relation.equalsIgnoreCase("next")) {
          return Optional.ofNullable(base.resolve(target));
        }
      }
    }
    return Optional.empty();
  }

  private static String unquote(final String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  @Extension
  @Symbol("linkHeader")
  public static class DescriptorImpl extends PaginationDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return Messages.RLP_LinkHeaderPagination_DisplayName();
    }
  }
}
