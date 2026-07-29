import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Grid,
  KeyValue,
  PageHeader,
  Spinner,
  Stack,
  Tag,
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

export default function DecisionDetailScreen({ applicationId, onBack, backLabel = 'Back' }) {
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

  const machineDone = Boolean(detail.outcome);
  const openReferral = detail.outcome === 'REFERRED' && !detail.decidedBy;

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

      {!machineDone && (
        <Alert tone="info" title="Decision in progress">
          The case is durable. This screen will update when the worker stores its result.
        </Alert>
      )}

      {actionError && (
        <Alert tone="negative" title="Operator action failed">
          {actionError}
        </Alert>
      )}

      <Stack gap={5}>
        {detail.decidedBy && (
          <Card
            title="Human decision"
            tone={statusTone(detail.outcome)}
            headEnd={<Badge tone={statusTone(detail.outcome)}>{detail.outcome}</Badge>}
          >
            <KeyValue
              items={[
                ['Decided by', detail.decidedBy],
                ['Decided at', detail.decidedAt ? new Date(detail.decidedAt).toLocaleString() : '—'],
                ['Reason', detail.decisionReason],
              ]}
            />
          </Card>
        )}

        {openReferral && (
          <Card title="Manual review">
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
                    placeholder="Record why you approve or reject this referral…"
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

        <Card title={detail.decidedBy ? 'Machine decision' : 'Decision summary'}>
          <Grid cols="auto" min={180}>
            {!detail.decidedBy && (
              <KeyValue
                stacked
                items={[{ label: 'Effective outcome', value: detail.outcome ?? 'IN_PROGRESS' }]}
              />
            )}
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

        {detail.ruleResults?.length > 0 && (
          <Grid cols="auto" min={280}>
            {detail.ruleResults.map((rule) => (
              <RuleCard key={rule.ruleName} rule={rule} />
            ))}
          </Grid>
        )}
      </Stack>
    </>
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
