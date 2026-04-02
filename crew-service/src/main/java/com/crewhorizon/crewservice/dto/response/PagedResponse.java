package com.crewhorizon.crewservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Generic paginated response wrapper.
 * WHY a generic wrapper: Consistent pagination metadata structure
 * across ALL list endpoints. Frontend can handle pagination
 * uniformly regardless of the resource type.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
