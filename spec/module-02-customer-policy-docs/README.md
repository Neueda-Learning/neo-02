# Module 2 · Customer Policy — AI implementation briefs

One self-contained brief per use case: context, contract, acceptance criteria, data changes and the mermaid source for sequence / entity / state diagrams. Generated from the spec — regenerate, don't hand-edit.

| UC | file | track · prerequisite |
|---|---|---|
| 00 | [uc-00-process-application.md](uc-00-process-application.md) | B · none (foundation) |
| 01 | [uc-01-search-cases.md](uc-01-search-cases.md) | A · after 00 — the rows it lists come from intake |
| 02 | [uc-02-review-decision.md](uc-02-review-decision.md) | B · after 00 + 07 — the engine decides against PolicyConfig |
| 03 | [uc-03-view-applicant.md](uc-03-view-applicant.md) | D · screen shell from 02 |
| 04 | [uc-04-work-referral-queue.md](uc-04-work-referral-queue.md) | B · after 02 is wired |
| 05 | [uc-05-rejection-patterns.md](uc-05-rejection-patterns.md) | A · after 01 |
| 06 | [uc-06-override-case.md](uc-06-override-case.md) | B · after 02 is wired |
| 07 | [uc-07-edit-policy-config.md](uc-07-edit-policy-config.md) | C · none (independent) |
| 08 | [uc-08-view-config-history.md](uc-08-view-config-history.md) | C · after 07 |
| 09 (candidate) | [uc-09-minimum-relationship-tenure.md](uc-09-minimum-relationship-tenure.md) | B · after 01–08 |
| 10 (candidate) | [uc-10-marketing-consent-for-partner-products.md](uc-10-marketing-consent-for-partner-products.md) | C · after 01–08 |

Component/system diagram: ![component](diagrams/component.jpg)
