import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Caption,
  Card,
  Field,
  Grid,
  PageHeader,
  Spinner,
  Stack,
  Textarea,
  TextInput,
} from '../design-system';
import { api } from '../api.js';
import { statusTone } from '../status.js';

const POLL_MS = 2000;

const RULE_LABELS = {
  existingProduct: 'Existing product',
  taxResidency: 'Tax residency',
  restrictionList: 'Restriction list',
  sampling: 'Sampling',
};

export default function DecisionDetailScreen({
  applicationId,
  onBack,
  backLabel = 'Back to applications',
}) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [busyAction, setBusyAction] = useState(null);
  const [operator, setOperator] = useState(() => localStorage.getItem('policyOperator') ?? '');
  const [reason, setReason] = useState('');

  useEffect(() => {
    let active = true;
    let timer;

    async function load() {
      try {
        const next = await api.getCase(applicationId);
        if (!active) return;
        setDetail(next);
        setError(null);
        if (!next.outcome) timer = setTimeout(load, POLL_MS);
      } catch (e) {
        if (!active) return;
        setError(e);
      }
    }

    setDetail(null);
    setError(null);
    load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [applicationId]);

  if (error) {
    return (
      <>
        <PageHeader
          title="Decision detail"
          meta={applicationId}
          actions={<Button onClick={onBack}>{backLabel}</Button>}
        />
        <Alert tone="negative" title="Could not load this case">
          {error.message}
        </Alert>
      </>
    );
  }

  if (!detail) {
    return (
      <>
        <PageHeader
          title="Decision detail"
          meta={applicationId}
          actions={<Button onClick={onBack}>{backLabel}</Button>}
        />
        <div className="decision-loading">
          <Spinner size="lg" label="Loading policy decision" />
        </div>
      </>
    );
  }

  const decided = Boolean(detail.outcome);
  const openReferral = detail.outcome === 'REFERRED' && !detail.decidedBy;
  const policyChecks = sortRuleResults(detail.ruleResults);

  const operatorAction = async (action) => {
    const currentOperator = operator.trim();
    if (!currentOperator) {
      setActionError('Operator ID is mandatory.');
      return;
    }
    localStorage.setItem('policyOperator', currentOperator);
    setBusyAction(action);
    setActionError(null);
    try {
      const next = action === 'claim'
        ? await api.claimReferral(applicationId, currentOperator)
        : await api.releaseReferral(applicationId, currentOperator);
      setDetail(next);
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusyAction(null);
    }
  };

  const decide = async (outcome) => {
    const currentOperator = operator.trim();
    const currentReason = reason.trim();
    if (!currentOperator || !currentReason) {
      setActionError('Operator ID and decision reason are mandatory.');
      return;
    }
    localStorage.setItem('policyOperator', currentOperator);
    setBusyAction(outcome);
    setActionError(null);
    try {
      const next = await api.decideReferral(
        applicationId, outcome, currentReason, currentOperator
      );
      setDetail(next);
      setReason('');
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusyAction(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Decision detail"
        badge={
          <Badge tone={statusTone(detail.outcome ?? 'IN_PROGRESS')}>
            {detail.outcome ?? 'IN_PROGRESS'}
          </Badge>
        }
        lede="stored decision and rule evidence"
        meta={`${applicationId} | ${detail.reference}`}
        actions={<Button onClick={onBack}>{backLabel}</Button>}
      />

      {!decided && (
        <Alert tone="info" title="Decision in progress">
          The case is durable. This screen will update when the worker stores its result.
        </Alert>
      )}

      {actionError && (
        <Alert tone="negative" title="Operator action failed">
          {actionError}
        </Alert>
      )}

      <div className="decision-layout">
        <Stack gap={5}>
          {detail.decidedBy && (
            <Card
              title="Human decision"
              tone={statusTone(detail.outcome)}
              className="decision-panel"
            >
              <Grid cols="auto" min={180} className="decision-summary-grid">
                <SummaryStat
                  icon={<OutcomeGlyph tone="neutral" kind="person" />}
                  label="Decided by"
                  value={detail.decidedBy}
                />
                <SummaryStat
                  icon={<OutcomeGlyph tone="info" kind="calendar" />}
                  label="Decided at"
                  value={detail.decidedAt ? new Date(detail.decidedAt).toLocaleString() : '—'}
                />
                <SummaryStat
                  icon={<OutcomeGlyph tone={statusTone(detail.outcome)} kind="reason" />}
                  label="Decision reason"
                  value={detail.decisionReason ?? '—'}
                />
              </Grid>
            </Card>
          )}

          {openReferral && (
            <Card title="Manual review" className="decision-panel">
              <Stack gap={4}>
                <Grid cols="auto" min={240}>
                  <Field label="Operator ID" required>
                    {({ id, describedBy }) => (
                      <TextInput
                        id={id}
                        aria-describedby={describedBy}
                        value={operator}
                        placeholder="e.g. s.chen"
                        onChange={(event) => setOperator(event.target.value)}
                      />
                    )}
                  </Field>
                  <Field
                    label="Claim state"
                    hint={detail.claimedAt
                      ? `Claimed ${new Date(detail.claimedAt).toLocaleString()}`
                      : 'Claim before reviewing when working in a shared queue.'}
                  >
                    <Stack row gap={2}>
                      <Badge tone={detail.claimedBy ? 'warning' : 'neutral'}>
                        {detail.claimedBy ? `Claimed by ${detail.claimedBy}` : 'Unclaimed'}
                      </Badge>
                      {(!detail.claimedBy || detail.claimedBy === operator.trim()) && (
                        <Button
                          size="sm"
                          busy={busyAction === (detail.claimedBy ? 'release' : 'claim')}
                          onClick={() => operatorAction(detail.claimedBy ? 'release' : 'claim')}
                        >
                          {detail.claimedBy ? 'Release' : 'Claim'}
                        </Button>
                      )}
                    </Stack>
                  </Field>
                </Grid>
                <Field label="Decision reason" required>
                  {({ id, describedBy }) => (
                    <Textarea
                      id={id}
                      aria-describedby={describedBy}
                      value={reason}
                      placeholder="Record why you approve or reject this referral..."
                      onChange={(event) => setReason(event.target.value)}
                    />
                  )}
                </Field>
                <Stack row gap={2}>
                  <Button
                    variant="primary"
                    busy={busyAction === 'APPROVED'}
                    disabled={Boolean(detail.claimedBy) && detail.claimedBy !== operator.trim()}
                    onClick={() => decide('APPROVED')}
                  >
                    Approve
                  </Button>
                  <Button
                    variant="danger"
                    busy={busyAction === 'REJECTED'}
                    disabled={Boolean(detail.claimedBy) && detail.claimedBy !== operator.trim()}
                    onClick={() => decide('REJECTED')}
                  >
                    Reject
                  </Button>
                </Stack>
              </Stack>
            </Card>
          )}

          <Card title="Decision summary" className="decision-panel">
            <Grid cols="auto" min={180} className="decision-summary-grid">
              <SummaryStat
                icon={<OutcomeGlyph tone={statusTone(detail.outcome ?? 'IN_PROGRESS')} kind="outcome" />}
                label="Effective outcome"
                value={detail.outcome ?? 'IN_PROGRESS'}
              />
              <SummaryStat
                icon={<OutcomeGlyph tone={statusTone(detail.machineOutcome ?? 'IN_PROGRESS')} kind="machine" />}
                label="Machine outcome"
                value={detail.machineOutcome ?? 'Pending'}
              />
              <SummaryStat
                icon={<OutcomeGlyph tone="info" kind="config" />}
                label="Policy config"
                value={
                  detail.policyConfigVersion == null
                    ? 'Not pinned'
                    : `Version ${detail.policyConfigVersion}`
                }
              />
              <SummaryStat
                icon={<OutcomeGlyph tone="neutral" kind="reference" />}
                label="Policy reference"
                value={detail.reference}
              />
            </Grid>
          </Card>

          <div className="decision-bottom-grid">
            <Card
              title={detail.outcome === 'REJECTED' ? 'Rejection reasons' : 'Decision reasons'}
              tone={detail.outcome === 'REJECTED' ? 'negative' : undefined}
              className="decision-panel"
            >
              <Stack gap={3}>
                <Caption>
                  {detail.outcome === 'REJECTED'
                    ? 'These policy checks directly explain why the case was rejected.'
                    : 'These policy checks explain the current recorded outcome.'}
                </Caption>

                {policyChecks.length > 0 ? (
                  <div className="decision-rule-list">
                    {policyChecks.map((rule, index) => (
                      <RuleOutcomeRow key={rule.ruleName} rule={rule} index={index} />
                    ))}
                  </div>
                ) : (
                  <div className="decision-empty-state">
                    <span className="decision-empty-state__icon">
                      <OutcomeGlyph
                        tone={detail.outcome === 'REJECTED' ? 'negative' : 'info'}
                        kind={detail.outcome === 'REJECTED' ? 'reason' : 'info'}
                      />
                    </span>
                    <div>
                      <strong>
                        {detail.outcome === 'REJECTED'
                          ? 'No rejection reasons were recorded.'
                          : 'No blocking rejection reason applies.'}
                      </strong>
                      <p>
                        {detail.outcome === 'REJECTED'
                          ? 'This case has a rejected outcome, but no detailed reason code was stored.'
                          : 'The recorded checks did not produce a failed rejection rule for this case.'}
                      </p>
                    </div>
                  </div>
                )}
              </Stack>
            </Card>

            <ApplicantPanel applicationId={applicationId} />
          </div>
        </Stack>
      </div>
    </>
  );
}

function ApplicantPanel({ applicationId }) {
  const [applicant, setApplicant] = useState(null);
  const [error, setError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setApplicant(null);
      setError(null);
      try {
        const view = await api.getCaseApplicant(applicationId);
        if (active) setApplicant(view);
      } catch (e) {
        if (active) setError(e);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [applicationId, attempt]);

  return (
    <Card title="Applicant information" className="decision-panel">
      {error ? (
        <Stack gap={3}>
          <Alert tone="warning" title="Applicant details unavailable">
            {error.message}
          </Alert>
          <Button size="sm" onClick={() => setAttempt((value) => value + 1)}>
            Retry
          </Button>
        </Stack>
      ) : applicant ? (
        <div className="decision-applicant-list">
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="person" />}
            label="Full name"
            value={valueOrDash(applicant.fullName)}
          />
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="calendar" />}
            label="Date of birth"
            value={valueOrDash(applicant.dateOfBirth)}
          />
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="globe" />}
            label="Country of residence"
            value={valueOrDash(applicant.countryOfResidence)}
          />
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="flag" />}
            label="Tax residencies"
            value={listOrDash(applicant.taxResidencies)}
          />
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="product" />}
            label="Product"
            value={valueOrDash(applicant.productCode)}
          />
          <ApplicantField
            icon={<OutcomeGlyph tone="neutral" kind="mobile" />}
            label="Channel"
            value={valueOrDash(applicant.channel)}
          />
        </div>
      ) : (
        <Spinner label="Loading applicant details" />
      )}
      <div className="decision-applicant-note">
        <Caption>
          Fetched live from the orchestrator when this case opens. Applicant data is not stored
          or cached by this module.
        </Caption>
      </div>
    </Card>
  );
}

function SummaryStat({ icon, label, value }) {
  return (
    <div className="decision-summary-stat">
      <span className="decision-summary-stat__icon">{icon}</span>
      <div>
        <Caption>{label}</Caption>
        <strong className="decision-summary-stat__value">{valueOrDash(value)}</strong>
      </div>
    </div>
  );
}

function ApplicantField({ icon, label, value }) {
  return (
    <div className="decision-applicant-field">
      <span className="decision-applicant-field__icon">{icon}</span>
      <div>
        <Caption>{label}</Caption>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

function RuleOutcomeRow({ rule, index }) {
  const presentation = getRulePresentation(rule);

  return (
    <div className="decision-rule-row">
      <div className="decision-rule-row__main">
        <div className="decision-rule-row__summary">
          <div className="decision-rule-row__head">
            <div className="decision-rule-row__rail">
              <span className="decision-rule-row__rail-icon">
                <OutcomeGlyph tone={presentation.tone} kind={presentation.icon} />
              </span>
              <span
                className={`decision-rule-row__index decision-rule-row__index--${presentation.tone}`}
              >
                {index + 1}
              </span>
            </div>
            <strong>{presentation.label}</strong>
          </div>
          {presentation.meta.map(([label, value]) => (
            <p key={label} className="decision-rule-row__meta-line">
              <span>{label}: </span>
              <strong>{value}</strong>
            </p>
          ))}
        </div>

        <div className={`decision-rule-row__callout decision-rule-row__callout--${presentation.tone}`}>
          <p>{presentation.message}</p>
        </div>

        <div className="decision-rule-row__status">
          <Badge tone={presentation.tone}>{presentation.badge}</Badge>
        </div>
      </div>
    </div>
  );
}

function OutcomeGlyph({ tone = 'neutral', kind = 'outcome' }) {
  const className = `decision-icon decision-icon--${tone}`;

  if (kind === 'person') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <circle cx="10" cy="6" r="3.25" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M4.5 16c0-2.7 2.3-4.7 5.5-4.7s5.5 2 5.5 4.7" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'calendar') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <rect x="3.5" y="4.5" width="13" height="12" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M6.5 3.5v3M13.5 3.5v3M3.5 8h13" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'globe') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <circle cx="10" cy="10" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M3.5 10h13M10 3.5c2 1.7 3.2 4.1 3.2 6.5S12 14.8 10 16.5M10 3.5C8 5.2 6.8 7.6 6.8 10S8 14.8 10 16.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'flag') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <path d="M5 3.5v13M6 4.5h7l-1.6 2.4L13 9.5H6" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }

  if (kind === 'product') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <rect x="3.5" y="5" width="13" height="10" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M3.5 8.5h13" fill="none" stroke="currentColor" strokeWidth="1.6" />
      </svg>
    );
  }

  if (kind === 'mobile') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <rect x="6" y="2.75" width="8" height="14.5" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="10" cy="14.5" r="0.8" fill="currentColor" />
      </svg>
    );
  }

  if (kind === 'config') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <path d="M10 3.8l1.3.4.9-1 .9.9-1 1 .4 1.3 1.2.6v1.3l-1.2.6-.4 1.3 1 1-.9.9-.9-1-1.3.4-.6 1.2H8.7l-.6-1.2-1.3-.4-.9 1-.9-.9 1-1-.4-1.3-1.2-.6V6.1l1.2-.6.4-1.3-1-1 .9-.9.9 1 1.3-.4.6-1.2h1.3z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
        <circle cx="10" cy="7.5" r="2.2" fill="none" stroke="currentColor" strokeWidth="1.4" />
      </svg>
    );
  }

  if (kind === 'reference') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <path d="M7.2 12.8l-1.7 1.7a2.4 2.4 0 103.4 3.4l1.7-1.7M12.8 7.2l1.7-1.7a2.4 2.4 0 10-3.4-3.4L9.4 3.8M7.5 12.5l5-5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }

  if (kind === 'pass') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <circle cx="10" cy="10" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M7.2 10.3l1.8 1.8 3.8-4.3" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }

  if (kind === 'warning') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <path d="M10 3.5l6 10.7a1 1 0 01-.9 1.5H4.9a1 1 0 01-.9-1.5L10 3.5z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
        <path d="M10 7.4v3.8M10 13.5h.01" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'machine') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <rect x="4" y="5" width="12" height="10" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M7 2.8v2.2M13 2.8v2.2M7 15v2.2M13 15v2.2M2.8 8h2.2M15 8h2.2M2.8 12h2.2M15 12h2.2" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'info') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <circle cx="10" cy="10" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M10 8.6v4M10 6.1h.01" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    );
  }

  if (kind === 'reason') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
        <circle cx="10" cy="10" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
        <path d="M7.4 7.4l5.2 5.2M12.6 7.4l-5.2 5.2" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 20 20" aria-hidden="true" className={className}>
      <circle cx="10" cy="10" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

function sortRuleResults(ruleResults = []) {
  const order = ['taxResidency', 'existingProduct', 'restrictionList', 'sampling'];
  return [...ruleResults].sort(
    (left, right) => order.indexOf(left.ruleName) - order.indexOf(right.ruleName)
  );
}

function getRulePresentation(rule) {
  const unavailable = rule.reasonCodes?.includes('POL_REGISTRY_UNAVAILABLE');
  const sampling = rule.ruleName === 'sampling';
  const passed = rule.passed === true;
  const tone = unavailable
    ? 'warning'
    : sampling
      ? rule.sampled
        ? 'warning'
        : 'info'
      : passed
        ? 'positive'
        : 'negative';

  const badge = unavailable
    ? 'UNAVAILABLE'
    : sampling
      ? rule.sampled
        ? 'TRIGGERED'
        : 'NOT TRIGGERED'
      : passed
        ? 'PASSED'
        : 'FAILED';

  const baseLabel =
    rule.ruleName === 'taxResidency'
      ? rule.passed === false
        ? 'Tax residency excluded'
        : 'Tax residency check'
      : rule.ruleName === 'existingProduct'
        ? 'Existing product check'
        : rule.ruleName === 'restrictionList'
          ? 'Restriction list check'
          : 'Sampling';

  const meta = [];
  if (rule.ruleName === 'taxResidency' && rule.matchedList) {
    meta.push(['Matched list', rule.matchedList]);
  }
  if (rule.ruleName === 'existingProduct' && rule.registryChecked != null) {
    meta.push(['Registry checked', rule.registryChecked ? 'Yes' : 'No']);
  }
  if (rule.reasonCodes?.length > 0 || (!passed && !sampling) || unavailable) {
    meta.push([
      'Reason code',
      rule.reasonCodes?.length > 0 ? rule.reasonCodes.join(', ') : '—',
    ]);
  }

  return {
    label: baseLabel,
    badge,
    tone,
    meta,
    message: describeRuleReason(rule),
    icon: unavailable ? 'warning' : sampling ? 'info' : passed ? 'pass' : 'reason',
  };
}

function describeRuleReason(rule) {
  if (!rule) return 'No rule evidence available.';
  if (rule.reasonCodes?.includes('POL_REGISTRY_UNAVAILABLE')) {
    return 'A required registry was unavailable when the check ran.';
  }
  if (rule.ruleName === 'taxResidency' && rule.matchedList) {
    return `The applicant's tax residency is on the excluded list (${rule.matchedList}).`;
  }
  if (rule.ruleName === 'existingProduct' && rule.passed === false) {
    return 'The applicant already holds an incompatible existing product.';
  }
  if (rule.ruleName === 'existingProduct' && rule.passed === true) {
    return 'No matching existing products were found.';
  }
  if (rule.ruleName === 'restrictionList' && rule.passed === false) {
    return `A restriction list match was found${rule.matchedList ? ` (${rule.matchedList})` : ''}.`;
  }
  if (rule.ruleName === 'restrictionList' && rule.passed === true) {
    return "No matches found on the bank's restriction list.";
  }
  if (rule.ruleName === 'sampling') {
    return rule.sampled
      ? `This case was sampled for manual review at position ${valueOrDash(rule.position)}.`
      : 'Not sampled. Sampled: No.';
  }
  if (rule.ruleName === 'taxResidency' && rule.passed === true) {
    return 'No excluded tax residency was found.';
  }
  if (rule.passed) {
    return 'This rule passed without a blocking reason.';
  }
  if (rule.reasonCodes?.length > 0) {
    return `Reason code: ${rule.reasonCodes[0]}`;
  }
  return 'This rule failed and contributed to the current outcome.';
}

function valueOrDash(value) {
  return value == null || value === '' ? '—' : value;
}

function listOrDash(values) {
  return Array.isArray(values) && values.length > 0 ? values.join(', ') : '—';
}
