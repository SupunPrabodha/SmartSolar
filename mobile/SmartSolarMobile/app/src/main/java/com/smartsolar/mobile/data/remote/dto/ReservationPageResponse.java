package com.smartsolar.mobile.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

public class ReservationPageResponse {
    private List<ReservationResponse> items = new ArrayList<>();
    private int page;
    private int pageSize;
    private boolean hasMore;

    public ReservationPageResponse() { }

    public ReservationPageResponse(List<ReservationResponse> items, int page, int pageSize, boolean hasMore) {
        this.items = items != null ? items : new ArrayList<>();
        this.page = page;
        this.pageSize = pageSize;
        this.hasMore = hasMore;
    }

    public List<ReservationResponse> getItems() { return items != null ? items : new ArrayList<>(); }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public boolean isHasMore() { return hasMore; }
}
