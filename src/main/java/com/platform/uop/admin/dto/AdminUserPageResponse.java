package com.platform.uop.admin.dto;

import java.util.List;

public record AdminUserPageResponse(

    List<AdminUserResponse> content,

    int pageNumber,

    int pageSize,

    long totalElements,

    int totalPages,

    boolean first,

    boolean last

) {
}