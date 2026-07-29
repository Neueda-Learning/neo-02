package com.neobank.module.dto;

import java.util.List;

/**
 * UC-01 search result: the rows to show (capped at the caller's limit) plus whether the
 * underlying match count exceeded that cap.
 *
 * <p>{@code more} lets the controller flag "more — refine your search" (spec acceptance
 * criterion 2) without changing the response body's shape from a plain array.</p>
 */
public record CaseSearchResult(List<PolicyRecordView> results, boolean more) {
}
