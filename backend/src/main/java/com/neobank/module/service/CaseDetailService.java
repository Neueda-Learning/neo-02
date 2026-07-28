package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.repository.PolicyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only UC02 case detail. Reviewing a case never runs the rules again. */
@Service
public class CaseDetailService {

    private final PolicyRecordRepository records;

    public CaseDetailService(PolicyRecordRepository records) {
        this.records = records;
    }

    @Transactional(readOnly = true)
    public CaseDetailView find(String applicationId) {
        return records.findById(applicationId)
                .map(CaseDetailView::of)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
    }
}
