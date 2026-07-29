// Thin fetch wrapper. Vite/nginx proxy these same-origin paths in dev and deployment.
const BASE = import.meta.env.VITE_API_BASE || '';

async function fetchJson(path, options = {}) {
  let res;
  try {
    res = await fetch(BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (cause) {
    const error = new Error('The service is temporarily unreachable.');
    error.status = 0;
    error.cause = cause;
    throw error;
  }
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    let details = null;
    try {
      details = await res.json();
      if (details.message) message = details.message;
    } catch {
      // Non-JSON error body.
    }
    const error = new Error(message);
    error.status = res.status;
    error.details = details;
    throw error;
  }
  const body = res.status === 204 ? null : await res.json();
  return { body, headers: res.headers };
}

async function request(path, options = {}) {
  const { body } = await fetchJson(path, options);
  return body;
}

export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: async (page = 0, status = 'All') => {
    const statusParam = status === 'All' ? '' : `&status=${encodeURIComponent(status)}`;
    const { body, headers } = await fetchJson(
      `/api/v1/applications?page=${page}${statusParam}`
    );
    return {
      results: body ?? [],
      page: Number(headers.get('X-Page') ?? page),
      more: headers.get('X-More-Results') === 'true',
      total: Number(headers.get('X-Total-Count') ?? body?.length ?? 0),
      allTotal: Number(headers.get('X-All-Count') ?? body?.length ?? 0),
      counts: {
        IN_PROGRESS: Number(headers.get('X-Status-In-Progress-Count') ?? 0),
        APPROVED: Number(headers.get('X-Status-Approved-Count') ?? 0),
        REJECTED: Number(headers.get('X-Status-Rejected-Count') ?? 0),
        REFERRED: Number(headers.get('X-Status-Referred-Count') ?? 0),
      },
    };
  },
  searchApplications: (query) =>
    request(`/api/v1/applications?q=${encodeURIComponent(query)}`),
  getCase: (id) => request(`/cases/${encodeURIComponent(id)}`),
  getCaseApplicant: (id) => request(`/cases/${encodeURIComponent(id)}/applicant`),
  listConfigVersions: () => request('/config/versions'),
  createConfig: (config) =>
    request('/config', {
      method: 'POST',
      body: JSON.stringify(config),
    }),
  listReasonCodes: (from, to) =>
    request(`/reason-codes?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  searchCases: async (query, limit = 10) => {
    const { body, headers } = await fetchJson(
      `/api/v1/cases?q=${encodeURIComponent(query)}&limit=${limit}`
    );
    return { results: body ?? [], more: headers.get('X-More-Results') === 'true' };
  },
  getApplicant: (id) => request(`/api/v1/cases/${encodeURIComponent(id)}/applicant`),
  listReferrals: () => request('/api/v1/referrals'),
  claimReferral: (id, operator) =>
    request(`/cases/${encodeURIComponent(id)}/claim`, {
      method: 'POST',
      body: JSON.stringify({ operator }),
    }),
  releaseReferral: (id, operator) =>
    request(`/cases/${encodeURIComponent(id)}/release`, {
      method: 'POST',
      body: JSON.stringify({ operator }),
    }),
  decideReferral: (id, outcome, reason, operator) =>
    request(`/cases/${encodeURIComponent(id)}/decision`, {
      method: 'POST',
      body: JSON.stringify({ outcome, reason, operator }),
    }),
};
