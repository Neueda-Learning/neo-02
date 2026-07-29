// Thin fetch wrapper. Vite/nginx proxy these same-origin paths in dev and deployment.
const BASE = import.meta.env.VITE_API_BASE || '';

async function fetchJson(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
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
  listApplications: (query) =>
    query != null
      ? request(`/api/v1/applications?q=${encodeURIComponent(query)}`)
      : request('/api/v1/applications'),
  getCase: (id) => request(`/cases/${encodeURIComponent(id)}`),
  getApplicant: (id) => request(`/cases/${encodeURIComponent(id)}/applicant`),
  overrideCase: (id, body) =>
    request(`/cases/${encodeURIComponent(id)}/override`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  listConfigVersions: () => request('/config/versions'),
  searchCases: async (query, limit = 10) => {
    const { body, headers } = await fetchJson(
      `/api/v1/cases?q=${encodeURIComponent(query)}&limit=${limit}`
    );
    return { results: body ?? [], more: headers.get('X-More-Results') === 'true' };
  },
  getApplicant: (id) => request(`/api/v1/cases/${encodeURIComponent(id)}/applicant`),
};
