package com.neobank.module.dto;

/** UC05 response row for ranked reason-code counts. */
public record ReasonCodeCountView(String code, long count, String kind) {
}
