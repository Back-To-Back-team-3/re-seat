package com.backtoback.reseat.global.common;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageResponse<T> {

    //이번 페이지에 담긴 실제 데이터 목록
    private final List<T> content;
    //현재 페이지 번호 (0부터 시작)
    private final int pageNumber;
    //한 페이지당 보여줄 데이터 개수
    private final int pageSize;
    //DB 전체 데이터 총 개수
    private final long totalElements;
    //계산된 전체 페이지 수
    private final int totalPages;
    //첫 페이지 여부
    private final boolean isFirst;
    //마지막 페이지 여부
    private final boolean isLast;

    private PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.isFirst = page.isFirst();
        this.isLast = page.isLast();
    }
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page);
    }
}
