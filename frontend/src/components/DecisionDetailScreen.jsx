import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Caption,
  Card,
  Field,
  FormActions,
  Grid,
  KeyValue,
  Modal,
  PageHeader,
  Select,
  Split,
  Spinner,
  Stack,
  Tag,
  Textarea,
  TextInput,
  Timeline,
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

export default function DecisionDetailScreen({ applicationId, onBack }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [overrideOpen, setOverrideOpen] = useState(false);
  const [overrideNotice, setOverrideNotice] = useState(null);

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
    setOverrideOpen(false);
    setOverrideNotice(null);
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
          actions={<Button onClick={onBack}>Back to applications</Button>}
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
          actions={<Button onClick={onBack}>Back to applications</Button>}
        />
        <div className="decision-loading">
          <Spinner size="lg" label="Loading policy decision" />
        </div>
      </>
    );
  }

  const overrideAllowed = ['APPROVED', 'REJECTED'].includes(detail.outcome);
  const applyOverride = async (command) => {
    const updated = await api.overrideCase(applicationId, command, detail.lockVersion);
    setDetail(updated);
    setOverrideNotice(
      `Decision changed to ${updated.outcome}. The audit entry is permanent and the orchestrator status update was issued.`
    );
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
        actions={
          <Stack row gap={2}>
            {overrideAllowed && (
              <Button variant="primary" onClick={() => setOverrideOpen(true)}>
                Override decision…
              </Button>
            )}
            <Button onClick={onBack}>Back to applications</Button>
          </Stack>
        }
      />

      {overrideNotice && (
        <Alert tone="positive" title="Override recorded">
          {overrideNotice}
        </Alert>
      )}

      {!detail.outcome && (
        <Alert tone="info" title="Decision in progress">
          The case is durable. This screen will update when the worker stores its result.
        </Alert>
      )}

      <Split
        ratio="wide-main"
        sidebar={<ApplicantPanel key={applicationId} applicationId={applicationId} />}
      >
        <Stack gap={5}>
          <Card title="Decision summary">
            <Grid cols="auto" min={180}>
              <KeyValue
                stacked
                items={[{ label: 'Effective outcome', value: detail.outcome ?? 'IN_PROGRESS' }]}
              />
              <KeyValue
                stacked
                items={[{ label: 'Machine outcome', value: detail.machineOutcome ?? 'Pending' }]}
              />
              <KeyValue
                stacked
                items={[
                  {
                    label: 'Policy config',
                    value:
                      detail.policyConfigVersion == null
                        ? 'Not pinned'
                        : `Version ${detail.policyConfigVersion}`,
                  },
                ]}
              />
            </Grid>
          </Card>

          {detail.decidedBy && (
            <Card title="Current human decision" tone="info">
              <KeyValue
                items={[
                  ['Operator', detail.decidedBy],
                  ['Reason', detail.decisionReason],
                  ['Decided at', formatTimestamp(detail.decidedAt)],
                ]}
              />
            </Card>
          )}

          {detail.ruleResults?.length > 0 && (
            <Grid cols="auto" min={280}>
              {detail.ruleResults.map((rule) => (
                <RuleCard key={rule.ruleName} rule={rule} />
              ))}
            </Grid>
          )}

          {detail.overrides?.length > 0 && (
            <Card
              title="Override history"
              subtitle="append-only audit trail — oldest first"
            >
              <Timeline
                items={detail.overrides.map((entry) => ({
                  id: entry.id,
                  title: `${entry.oldOutcome} → ${entry.newOutcome}`,
                  detail: `${entry.reason} · ${entry.operator}`,
                  when: formatTimestamp(entry.overriddenAt),
                  tone: statusTone(entry.newOutcome),
                }))}
              />
            </Card>
          )}
        </Stack>
      </Split>

      <OverrideDecisionModal
        open={overrideOpen}
        currentOutcome={detail.outcome}
        onClose={() => setOverrideOpen(false)}
        onSubmit={applyOverride}
      />
    </>
  );
}

function OverrideDecisionModal({ open, currentOutcome, onClose, onSubmit }) {
  const [newOutcome, setNewOutcome] = useState('');
  const [reason, setReason] = useState('');
  const [operator, setOperator] = useState('');
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [busy, setBusy] = useState(false);

  const outcomes = ['APPROVED', 'REJECTED', 'REFERRED'].filter(
    (outcome) => outcome !== currentOutcome
  );

  useEffect(() => {
    if (!open) return;
    setNewOutcome(outcomes[0] ?? '');
    setReason('');
    setOperator('');
    setErrors({});
    setSubmitError(null);
    setBusy(false);
  }, [open, currentOutcome]);

  const submit = async (event) => {
    event.preventDefault();
    const nextErrors = {};
    if (!newOutcome) nextErrors.newOutcome = 'Choose a new outcome.';
    if (!reason.trim()) nextErrors.reason = 'A reason is required for the permanent audit trail.';
    if (!operator.trim()) nextErrors.operator = 'Operator is required.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setBusy(true);
    setSubmitError(null);
    try {
      await onSubmit({
        newOutcome,
        reason: reason.trim(),
        operator: operator.trim(),
      });
      onClose();
    } catch (nextError) {
      setSubmitError(nextError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      title="Override decision"
      onClose={busy ? undefined : onClose}
      footer={
        <>
          <Button
            variant="primary"
            type="submit"
            form="override-decision-form"
            busy={busy}
            busyLabel="Recording override…"
          >
            Confirm override
          </Button>
          <Button onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </>
      }
    >
      <form id="override-decision-form" onSubmit={submit}>
        <Stack gap={5}>
          <Alert tone="warning" title={`Current outcome: ${currentOutcome}`}>
            This changes the effective decision, writes an immutable audit entry, and sends the
            orchestrator a status update. The original machine outcome and rule evidence remain
            unchanged.
          </Alert>

          {submitError && (
            <Alert tone="negative" title="Override could not be recorded">
              {submitError.message}
            </Alert>
          )}

          <Field label="New outcome" required error={errors.newOutcome}>
            {({ id, invalid, describedBy }) => (
              <Select
                id={id}
                value={newOutcome}
                invalid={invalid}
                aria-describedby={describedBy}
                onChange={(event) => setNewOutcome(event.target.value)}
                options={outcomes}
              />
            )}
          </Field>

          <Field
            label="Reason"
            required
            hint="Explain why the stored decision is wrong. This text is retained permanently; do not enter applicant PII."
            error={errors.reason}
          >
            {({ id, invalid, describedBy }) => (
              <Textarea
                id={id}
                value={reason}
                maxLength={1000}
                invalid={invalid}
                aria-describedby={describedBy}
                onChange={(event) => setReason(event.target.value)}
                placeholder="e.g. Registry entry stale — card closed in May"
              />
            )}
          </Field>

          <Field label="Operator" required error={errors.operator}>
            {({ id, invalid, describedBy }) => (
              <TextInput
                id={id}
                value={operator}
                maxLength={100}
                invalid={invalid}
                aria-describedby={describedBy}
                onChange={(event) => setOperator(event.target.value)}
                placeholder="e.g. b.dimovski"
                autoComplete="username"
              />
            )}
          </Field>

          <FormActions>
            <Caption>
              Repeating this exact command is safe: it will not create another audit entry or
              change the decision again; the idempotent status callback is reissued.
            </Caption>
          </FormActions>
        </Stack>
      </form>
    </Modal>
  );
}

function ApplicantPanel({ applicationId }) {
  const [application, setApplication] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      try {
        const next = await api.getApplicant(applicationId);
        if (!active) return;
        setApplication(next);
        setError(null);
      } catch (nextError) {
        if (!active) return;
        setApplication(null);
        setError(nextError);
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => {
      active = false;
    };
  }, [applicationId, attempt]);

  const applicant = application?.applicant;
  const product = application?.product;

  return (
    <Card title="Applicant" subtitle="live from the orchestrator">
      {loading && <Spinner label="Loading applicant" />}

      {!loading && error && (
        <Alert
          tone="warning"
          title="Applicant unavailable"
          action={
            <Button size="sm" onClick={() => setAttempt((value) => value + 1)}>
              Retry
            </Button>
          }
        >
          {error.message}
        </Alert>
      )}

      {!loading && !error && (
        <Stack gap={4}>
          <KeyValue
            stacked
            items={[
              ['Full name', valueOrDash(applicant?.fullName)],
              ['Date of birth', valueOrDash(applicant?.dateOfBirth)],
              ['Tax residencies', listOrDash(applicant?.taxResidencies)],
              ['Product', valueOrDash(product?.productCode)],
              ['Channel', valueOrDash(application?.channel)],
              ['Country of residence', valueOrDash(applicant?.countryOfResidence)],
            ]}
          />
          <Caption>
            Fetched on open via the application proxy. Nothing in this panel is stored by
            Customer Policy.
          </Caption>
        </Stack>
      )}
    </Card>
  );
}

function RuleCard({ rule }) {
  const unavailable = rule.reasonCodes?.includes('POL_REGISTRY_UNAVAILABLE');
  const sampling = rule.ruleName === 'sampling';
  const label = sampling
    ? rule.sampled
      ? 'TRIGGERED'
      : 'NOT TRIGGERED'
    : unavailable
      ? 'UNAVAILABLE'
      : rule.passed
        ? 'PASSED'
        : 'FAILED';
  const tone = unavailable
    ? 'warning'
    : sampling && rule.sampled
      ? 'warning'
      : rule.passed
        ? 'positive'
        : 'negative';

  const evidence = [];
  if (rule.registryChecked != null) {
    evidence.push(['Registry checked', yesNo(rule.registryChecked)]);
  }
  if (rule.matchedList != null) {
    evidence.push(['Matched list', rule.matchedList]);
  }
  if (rule.sampled != null) {
    evidence.push(['Sampled', yesNo(rule.sampled)]);
  }
  if (rule.position != null) {
    evidence.push(['Decision position', rule.position]);
  }

  return (
    <Card
      title={RULE_LABELS[rule.ruleName] ?? rule.ruleName}
      tone={tone}
      headEnd={<Badge tone={tone}>{label}</Badge>}
    >
      <Stack gap={4}>
        {evidence.length > 0 && <KeyValue items={evidence} />}
        <Stack row gap={2} className="decision-reasons">
          {rule.reasonCodes?.length > 0 ? (
            rule.reasonCodes.map((code) => <Tag key={code}>{code}</Tag>)
          ) : (
            <span className="decision-no-reason">No reason codes</span>
          )}
        </Stack>
      </Stack>
    </Card>
  );
}

function yesNo(value) {
  return value ? 'Yes' : 'No';
}

function valueOrDash(value) {
  return value == null || value === '' ? '—' : value;
}

function listOrDash(values) {
  return values?.length ? values.join(', ') : '—';
}

function formatTimestamp(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'UTC',
  }).format(new Date(value));
}
