package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideLogView;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.PolicyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only UC02 case detail. Reviewing a case never runs the rules again. */
@Service
public class CaseDetailService {

    private final PolicyRecordRepository records;
    private final OverrideLogRepository overrides;

    public CaseDetailService(
            PolicyRecordRepository records,
            OverrideLogRepository overrides) {
        this.records = records;
        this.overrides = overrides;
    }

    @Transactional(readOnly = true)
    public CaseDetailView find(String applicationId) {
        return records.findById(applicationId)
                .map(record -> CaseDetailView.of(
                        record,
                        overrides.findByApplicationIdOrderByOverriddenAtAscIdAsc(applicationId)
                                .stream()
                                .map(OverrideLogView::of)
                                .toList()))
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
    }
}
