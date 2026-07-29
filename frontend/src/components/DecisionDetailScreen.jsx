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
