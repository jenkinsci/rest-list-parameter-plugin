package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.Pagination;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkHeaderPaginationTest {

  private static final String GITHUB_LINK =
    "<https://api.github.com/repositories/1300192/issues?page=2>; rel=\"prev\", "
      + "<https://api.github.com/repositories/1300192/issues?page=4>; rel=\"next\", "
      + "<https://api.github.com/repositories/1300192/issues?page=515>; rel=\"last\", "
      + "<https://api.github.com/repositories/1300192/issues?page=1>; rel=\"first\"";

  private static final String GITLAB_LINK =
    "<https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=1&per_page=3>; rel=\"prev\", "
      + "<https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=3&per_page=3>; rel=\"next\", "
      + "<https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=1&per_page=3>; rel=\"first\", "
      + "<https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=3&per_page=3>; rel=\"last\"";

  @Test
  void followsNextAmongOtherRelations() {
    assertEquals("https://h/api?page=2",
      next("https://h/api", "<https://h/api?page=2>; rel=\"next\", <https://h/api?page=3>; rel=\"last\""));
  }

  @Test
  void noNextLinkEndsPagination() {
    assertEquals(null, next("https://h/api?page=3", "<https://h/api?page=1>; rel=\"first\""));
    assertEquals(null, next("https://h/api", (String) null));
  }

  @Test
  void severalRelationsInOneEntry() {
    assertEquals("https://h/api?page=2", next("https://h/api", "<https://h/api?page=2>; rel=\"next last\""));
    assertEquals("https://h/api?page=2", next("https://h/api", "<https://h/api?page=2>; rel=\"last  next\""));
  }

  @Test
  void relIsComparedCaseInsensitivelyAndMayBeUnquoted() {
    assertEquals("https://h/api?page=2", next("https://h/api", "<https://h/api?page=2>; REL=\"Next\""));
    assertEquals("https://h/api?page=2", next("https://h/api", "<https://h/api?page=2>; rel=next"));
    assertEquals("https://h/api?page=2", next("https://h/api", "<https://h/api?page=2> ; title=\"x\" ; rel = next"));
  }

  @Test
  void onlyWholeRelationNamesMatch() {
    assertEquals(null, next("https://h/api", "<https://h/api?page=2>; rel=\"nextpage\""));
    assertEquals(null, next("https://h/api", "<https://h/api?page=2>; title=\"next\""));
  }

  @Test
  void relativeLinkIsResolvedAgainstRequestUrl() {
    assertEquals("https://h/api/tags?page=2", next("https://h/api/tags?page=1", "</api/tags?page=2>; rel=\"next\""));
    assertEquals("https://h/api/tags?page=2", next("https://h/api/tags?page=1", "<?page=2>; rel=\"next\""));
  }

  @Test
  void readsSeveralLinkHeaders() {
    Headers headers = new Headers.Builder()
      .add("Link", "<https://h/api?page=1>; rel=\"first\"")
      .add("Link", "<https://h/api?page=2>; rel=\"next\"")
      .build();
    assertEquals("https://h/api?page=2", next("https://h/api", headers));
  }

  @Test
  void commaInsideUrlIsNotAnEntrySeparator() {
    assertEquals("https://h/api?ids=1,2&page=2",
      next("https://h/api", "<https://h/api?ids=1,2&page=1>; rel=\"prev\", <https://h/api?ids=1,2&page=2>; rel=\"next\""));
  }

  @Test
  void unparsableEntriesAreIgnored() {
    assertEquals("https://h/api?page=2",
      next("https://h/api", "garbage; rel=\"next\", <https://h/api?page=2; rel=\"next\", <https://h/api?page=2>; rel=\"next\""));
    assertEquals(null, next("https://h/api", "<http://[bad>; rel=\"next\""));
  }

  @Test
  void githubSample() {
    assertEquals("https://api.github.com/repositories/1300192/issues?page=4",
      next("https://api.github.com/repositories/1300192/issues?page=3", GITHUB_LINK));
  }

  @Test
  void gitlabSample() {
    assertEquals("https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=3&per_page=3",
      next("https://gitlab.example.com/api/v4/projects/8/issues/8/notes?id=8&noteable_id=8&page=2&per_page=3",
        GITLAB_LINK));
  }

  @Test
  void nextPageCarriesNoToken() {
    Optional<Pagination.NextPage> next = new LinkHeaderPagination().next(page("https://h/api",
      new Headers.Builder().add("Link", "<https://h/api?page=2>; rel=\"next\"").build()));
    assertFalse(next.isEmpty());
    assertNull(next.get().getToken());
  }

  private static String next(final String requestUrl, final String link) {
    Headers.Builder headers = new Headers.Builder();
    if (link != null) {
      headers.add("Link", link);
    }
    return next(requestUrl, headers.build());
  }

  private static String next(final String requestUrl, final Headers headers) {
    return new LinkHeaderPagination().next(page(requestUrl, headers))
                                     .map(nextPage -> nextPage.getUrl().toString())
                                     .orElse(null);
  }

  private static Pagination.Page page(final String requestUrl, final Headers headers) {
    return new Pagination.Page(HttpUrl.get("https://h/api"), HttpUrl.get(requestUrl), headers, "[]");
  }
}
