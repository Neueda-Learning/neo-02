// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-02) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    let details = null;
    try {
      const body = await res.json();
      details = body;
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    error.details = details;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

// This UI only ever READS. Applications arrive from the orchestrator — the real one, or the
// sidecar playing it at http://localhost:9000 — never from a button in here. That is the
// contract: your module is called, it does not call itself.
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: (q) =>
    q != null
      ? request(`/api/v1/applications?q=${encodeURIComponent(q)}`)
      : request('/api/v1/applications'),
  getApplication: (id) => request(`/api/v1/applications/${id}`),
  searchCases: (query, limit = 10) => request(`/api/v1/cases?q=${encodeURIComponent(query)}&limit=${limit}`),
  getApplicant: (id) => request(`/api/v1/cases/${id}/applicant`),
  reasonCodes: (from, to) =>
    request(`/api/v1/reason-codes?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
};
