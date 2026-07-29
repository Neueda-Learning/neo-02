import React, { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Field,
  FormGrid,
  Modal,
  Stack,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const blankRestriction = () => ({ fullName: '', dateOfBirth: '', reason: '' });

function toDraft(config) {
  return {
    supportedResidencies: (config?.supportedResidencies ?? []).join(', '),
    excludedResidencies: (config?.excludedResidencies ?? []).join(', '),
    restrictionList: (config?.restrictionList ?? []).map((entry) => ({ ...entry })),
    sampleEvery: String(config?.sampleEvery ?? 7),
  };
}

function parseCountries(value) {
  return value
    .split(/[\s,]+/)
    .map((country) => country.trim().toUpperCase())
    .filter(Boolean);
}

function addError(errors, field, message) {
  errors[field] = errors[field] ? `${errors[field]}; ${message}` : message;
}

function validate(draft) {
  const errors = {};
  const supported = parseCountries(draft.supportedResidencies);
  const excluded = parseCountries(draft.excludedResidencies);
  const malformedSupported = supported.filter((country) => !/^[A-Z]{2}$/.test(country));
  const malformedExcluded = excluded.filter((country) => !/^[A-Z]{2}$/.test(country));
  const overlap = excluded.filter((country) => supported.includes(country));
  const sampleEvery = Number(draft.sampleEvery);

  if (malformedSupported.length > 0) {
    addError(errors, 'supportedResidencies', 'Use two-letter ISO country codes.');
  }
  if (malformedExcluded.length > 0) {
    addError(errors, 'excludedResidencies', 'Use two-letter ISO country codes.');
  }
  if (overlap.length > 0) {
    addError(
      errors,
      'excludedResidencies',
      `${[...new Set(overlap)].join(', ')} cannot appear in both residency lists.`
    );
  }
  if (!Number.isInteger(sampleEvery) || sampleEvery < 1) {
    addError(errors, 'sampleEvery', 'Enter a whole number of 1 or greater.');
  }

  draft.restrictionList.forEach((entry, index) => {
    if (!entry.fullName.trim()) {
      addError(errors, `restrictionList[${index}].fullName`, 'Full name is required.');
    }
    if (!entry.dateOfBirth) {
      addError(errors, `restrictionList[${index}].dateOfBirth`, 'Date of birth is required.');
    }
    if (!entry.reason.trim()) {
      addError(errors, `restrictionList[${index}].reason`, 'Reason is required.');
    }
  });

  return errors;
}

function errorMapFromResponse(error) {
  const errors = {};
  for (const detail of error.details?.errors ?? []) {
    addError(errors, detail.field, detail.message);
  }
  return errors;
}

export default function PolicyConfigEditor({ open, current, onClose, onPublished }) {
  const [draft, setDraft] = useState(() => toDraft(current));
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [saving, setSaving] = useState(false);

  const updateField = (field, value) => {
    setDraft((previous) => ({ ...previous, [field]: value }));
    setFieldErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  const updateRestriction = (index, field, value) => {
    setDraft((previous) => ({
      ...previous,
      restrictionList: previous.restrictionList.map((entry, entryIndex) =>
        entryIndex === index ? { ...entry, [field]: value } : entry
      ),
    }));
    const key = `restrictionList[${index}].${field}`;
    setFieldErrors((previous) => ({ ...previous, [key]: undefined }));
  };

  const removeRestriction = (index) => {
    setDraft((previous) => ({
      ...previous,
      restrictionList: previous.restrictionList.filter((_, entryIndex) => entryIndex !== index),
    }));
    setFieldErrors({});
  };

  const submit = async (event) => {
    event.preventDefault();
    const errors = validate(draft);
    setFieldErrors(errors);
    setSubmitError(null);
    if (Object.keys(errors).length > 0) return;

    const config = {
      supportedResidencies: parseCountries(draft.supportedResidencies),
      excludedResidencies: parseCountries(draft.excludedResidencies),
      restrictionList: draft.restrictionList.map((entry) => ({
        fullName: entry.fullName.trim(),
        dateOfBirth: entry.dateOfBirth,
        reason: entry.reason.trim(),
      })),
      sampleEvery: Number(draft.sampleEvery),
    };

    setSaving(true);
    try {
      const result = await api.createConfig(config);
      onPublished(result.version);
    } catch (error) {
      const responseErrors = errorMapFromResponse(error);
      setFieldErrors(responseErrors);
      setSubmitError(
        Object.keys(responseErrors).length > 0
          ? 'Some fields need attention before this version can be published.'
          : error.message
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      wide
      title={`Publish policy version after v${current.version}`}
      onClose={saving ? undefined : onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button
            variant="primary"
            type="submit"
            form="policy-config-editor"
            busy={saving}
            busyLabel="Publishing…"
          >
            Edit Policy Config
          </Button>
        </>
      }
    >
      <form id="policy-config-editor" onSubmit={submit} noValidate>
        <Stack gap={5}>
          <Alert tone="info" title="This publishes the complete policy document">
            The current version is copied below. Saving inserts a new version that applies to the
            next application; earlier decisions stay pinned to their original version.
          </Alert>

          {submitError && (
            <Alert tone="negative" title="Could not publish policy configuration">
              {submitError}
            </Alert>
          )}

          <Card title="Residency rules">
            <FormGrid>
              <Field
                label="Supported residencies"
                hint="Comma-separated uppercase ISO country codes, for example GB, IE, PL."
                error={fieldErrors.supportedResidencies}
                required
              >
                {({ id, invalid, describedBy }) => (
                  <TextInput
                    id={id}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    value={draft.supportedResidencies}
                    onChange={(event) => updateField('supportedResidencies', event.target.value)}
                    placeholder="GB, IE, PL"
                  />
                )}
              </Field>
              <Field
                label="Excluded residencies"
                hint="Any match rejects, even if the country is also supported."
                error={fieldErrors.excludedResidencies}
                required
              >
                {({ id, invalid, describedBy }) => (
                  <TextInput
                    id={id}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    value={draft.excludedResidencies}
                    onChange={(event) => updateField('excludedResidencies', event.target.value)}
                    placeholder="US"
                  />
                )}
              </Field>
            </FormGrid>
          </Card>

          <Card
            title="Restriction list"
            subtitle={`${draft.restrictionList.length} ${
              draft.restrictionList.length === 1 ? 'entry' : 'entries'
            }`}
            headEnd={
              <Button
                size="sm"
                onClick={() =>
                  setDraft((previous) => ({
                    ...previous,
                    restrictionList: [...previous.restrictionList, blankRestriction()],
                  }))
                }
              >
                Add person
              </Button>
            }
          >
            {draft.restrictionList.length === 0 ? (
              <p className="policy-editor__empty-list">
                No restricted people. Add one to make matching applications reject.
              </p>
            ) : (
              <Stack gap={4}>
                {draft.restrictionList.map((entry, index) => (
                  <div className="policy-editor__restriction" key={index}>
                    <div className="policy-editor__restriction-head">
                      <strong>Entry {index + 1}</strong>
                      <Button size="sm" variant="ghost" onClick={() => removeRestriction(index)}>
                        Remove
                      </Button>
                    </div>
                    <FormGrid cols={3}>
                      <Field
                        label="Full name"
                        error={fieldErrors[`restrictionList[${index}].fullName`]}
                        required
                      >
                        {({ id, invalid, describedBy }) => (
                          <TextInput
                            id={id}
                            invalid={invalid}
                            aria-describedby={describedBy}
                            value={entry.fullName}
                            onChange={(event) =>
                              updateRestriction(index, 'fullName', event.target.value)
                            }
                          />
                        )}
                      </Field>
                      <Field
                        label="Date of birth"
                        error={fieldErrors[`restrictionList[${index}].dateOfBirth`]}
                        required
                      >
                        {({ id, invalid, describedBy }) => (
                          <TextInput
                            id={id}
                            type="date"
                            invalid={invalid}
                            aria-describedby={describedBy}
                            value={entry.dateOfBirth}
                            onChange={(event) =>
                              updateRestriction(index, 'dateOfBirth', event.target.value)
                            }
                          />
                        )}
                      </Field>
                      <Field
                        label="Reason"
                        error={fieldErrors[`restrictionList[${index}].reason`]}
                        required
                      >
                        {({ id, invalid, describedBy }) => (
                          <TextInput
                            id={id}
                            invalid={invalid}
                            aria-describedby={describedBy}
                            value={entry.reason}
                            onChange={(event) =>
                              updateRestriction(index, 'reason', event.target.value)
                            }
                          />
                        )}
                      </Field>
                    </FormGrid>
                  </div>
                ))}
              </Stack>
            )}
          </Card>

          <Card title="Sampling">
            <Field
              label="Sample every (X)"
              hint="Every Xth first-time decision is referred for manual review."
              error={fieldErrors.sampleEvery}
              required
            >
              {({ id, invalid, describedBy }) => (
                <TextInput
                  id={id}
                  type="number"
                  min="1"
                  step="1"
                  invalid={invalid}
                  aria-describedby={describedBy}
                  value={draft.sampleEvery}
                  onChange={(event) => updateField('sampleEvery', event.target.value)}
                />
              )}
            </Field>
          </Card>
        </Stack>
      </form>
    </Modal>
  );
}
