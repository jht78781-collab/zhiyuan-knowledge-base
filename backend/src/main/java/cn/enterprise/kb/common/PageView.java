package cn.enterprise.kb.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record PageView<T>(List<T> items, int page, int pages, long total) {
    public static <A, B> PageView<B> from(Page<A> page, Function<A, B> mapper) {
        return new PageView<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getTotalPages(), page.getTotalElements());
    }

    public static PageRequest request(int page, Sort sort) {
        if (page < 0 || page > 10_000) throw ApiProblem.bad("页码超出范围。");
        return PageRequest.of(page, 25, sort);
    }
}
