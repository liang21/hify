package com.hify.common.web;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Paginated API response
 *
 * @param <T> data type
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {

    private long total;
    private int page;
    private int size;

    public PageResult() {
        super();
    }

    public PageResult(int code, String message, List<T> data, long total, int page, int size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * Success response with paginated data
     */
    public static <T> PageResult<T> of(List<T> data, long total, int page, int size) {
        return new PageResult<>(200, "success", data, total, page, size);
    }

    /**
     * Success response with empty data
     */
    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0L, page, size);
    }

    /**
     * Calculate total pages
     */
    public int getTotalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    /**
     * Check if has next page
     */
    public boolean hasNext() {
        return page < getTotalPages();
    }

    /**
     * Check if has previous page
     */
    public boolean hasPrevious() {
        return page > 1;
    }
}
