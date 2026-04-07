package com.crewhorizon.rosterservice.dto.response;
import lombok.*; import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page; private int size;
    private long totalElements; private int totalPages;
    private boolean first; private boolean last; private boolean empty;
}
