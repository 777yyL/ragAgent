package com.company.knowledge.common.result;

import lombok.Data;

import java.util.List;

/**
 * 分页结果包装。
 */
@Data
public class PageResult<T> {

    private long total;
    private int pageNo;
    private int pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, int pageNo, int pageSize, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.pageNo = pageNo;
        r.pageSize = pageSize;
        r.list = list;
        return r;
    }
}
