package io.jenkins.plugins.restlistparam;

import io.jenkins.plugins.restlistparam.model.LinkHeaderPagination;
import io.jenkins.plugins.restlistparam.model.Pagination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginationTest {

  @Test
  void maxPagesDefaultsToTen() {
    Pagination pagination = new LinkHeaderPagination();
    assertEquals(10, pagination.getMaxPages());
    assertEquals(10, pagination.getEffectiveMaxPages());
  }

  @Test
  void configuredMaxPagesIsKeptAsEntered() {
    assertEquals(5, maxPages(5).getEffectiveMaxPages());
    assertEquals(1, maxPages(1).getEffectiveMaxPages());
    assertEquals(100, maxPages(100).getEffectiveMaxPages());
    assertEquals(500, maxPages(500).getMaxPages());
    assertEquals(0, maxPages(0).getMaxPages());
  }

  @Test
  void effectiveMaxPagesIsClampedToOneToHundred() {
    assertEquals(1, maxPages(0).getEffectiveMaxPages());
    assertEquals(1, maxPages(-3).getEffectiveMaxPages());
    assertEquals(100, maxPages(101).getEffectiveMaxPages());
    assertEquals(100, maxPages(500).getEffectiveMaxPages());
  }

  private static Pagination maxPages(final int maxPages) {
    Pagination pagination = new LinkHeaderPagination();
    pagination.setMaxPages(maxPages);
    return pagination;
  }
}
