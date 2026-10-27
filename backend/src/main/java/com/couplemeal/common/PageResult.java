package com.couplemeal.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回体（MyBatis-Plus 分页插件产出后包装）。
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long pages;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long pages, long current, long size, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.pages = pages;
        r.current = current;
        r.size = size;
        r.records = records;
        return r;
    }
}
