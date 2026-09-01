package com.platform.uop.admin.dto;

import java.util.List;

public record AdminUserPageResponse(

    List<AdminUserResponse> content,

    int number,

    int size,

    long totalElements,

    int totalPages,

    boolean first,

    boolean last

) {
}