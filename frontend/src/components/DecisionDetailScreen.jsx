import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Caption,
  Card,
  Grid,
  KeyValue,
  PageHeader,
  Split,
  Spinner,
  Stack,
  Tag,
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

  const decided = Boolean(detail.outcome);
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
        actions={<Button onClick={onBack}>Back to applications</Button>}
      />

      {!decided && (
        <Alert tone="info" title="Decision in progress">
          The case is durable. This screen will update when the worker stores its result.
        </Alert>
      )}

      <Split
        ratio="wide-main"
        sidebar={<ApplicantPanel applicationId={applicationId} />}
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

          {detail.ruleResults?.length > 0 && (
            <Grid cols="auto" min={280}>
              {detail.ruleResults.map((rule) => (
                <RuleCard key={rule.ruleName} rule={rule} />
              ))}
            </Grid>
          )}
        </Stack>
      </Split>
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
    <Card title="Applicant">
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
        <KeyValue
          stacked
          items={[
            { label: 'Full name', value: valueOrDash(applicant.fullName) },
            { label: 'Date of birth', value: valueOrDash(applicant.dateOfBirth) },
            { label: 'Country of residence', value: valueOrDash(applicant.countryOfResidence) },
            { label: 'Tax residencies', value: listOrDash(applicant.taxResidencies) },
            { label: 'Product', value: valueOrDash(applicant.productCode) },
            { label: 'Channel', value: valueOrDash(applicant.channel) },
          ]}
        />
      ) : (
        <Spinner label="Loading applicant details" />
      )}
      <Caption>
        Fetched live from the orchestrator when this case opens. Applicant data is not stored or
        cached by this module.
      </Caption>
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
  return Array.isArray(values) && values.length > 0 ? values.join(', ') : '—';
}
