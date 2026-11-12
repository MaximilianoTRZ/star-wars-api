package dev.starwars.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaginatorTest {

    private static final List<Integer> TWENTY_FIVE =
            IntStream.rangeClosed(1, 25).boxed().toList();

    @Test
    @DisplayName("empty list: empty page with 0 elements and 0 pages")
    void emptyList() {
        PageResponse<Integer> page = Paginator.paginate(List.of(), new PageRequestParams(1, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    @DisplayName("full first page")
    void firstPage() {
        PageResponse<Integer> page = Paginator.paginate(TWENTY_FIVE, new PageRequestParams(1, 10));

        assertThat(page.content()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("partial last page")
    void lastPartialPage() {
        PageResponse<Integer> page = Paginator.paginate(TWENTY_FIVE, new PageRequestParams(3, 10));

        assertThat(page.content()).containsExactly(21, 22, 23, 24, 25);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("out-of-range page: empty content with the real totals")
    void outOfRangePage() {
        PageResponse<Integer> page = Paginator.paginate(TWENTY_FIVE, new PageRequestParams(4, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(4);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("max size returns everything in one page")
    void maxSize() {
        PageResponse<Integer> page = Paginator.paginate(TWENTY_FIVE, new PageRequestParams(1, 100));

        assertThat(page.content()).hasSize(25);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("total that is an exact multiple of size adds no extra page")
    void exactMultiple() {
        assertThat(Paginator.totalPages(20, 10)).isEqualTo(2);
        assertThat(Paginator.totalPages(21, 10)).isEqualTo(3);
    }
}
