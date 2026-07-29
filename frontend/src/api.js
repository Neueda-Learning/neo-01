// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-01) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const LOCAL_BACKEND =
  typeof window !== 'undefined' && /^(localhost|127\.0\.0\.1)$/.test(window.location.hostname)
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : '';

const BASE = import.meta.env.VITE_API_BASE || LOCAL_BACKEND || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
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
  listApplications: () => request('/api/v1/applications'),
  getApplication: (id) => request(`/api/v1/applications/${id}`),
  getCaseDetail: (applicationId) => request(`/cases/${applicationId}`),
  searchCases: (q = '', limit = 10, outcome = null) => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (limit != null) params.set('limit', String(limit));
    if (outcome && outcome !== 'All') params.set('outcome', outcome);
    const query = params.toString();
    return request(`/cases${query ? `?${query}` : ''}`);
  },
  listProductCodes: () => request('/products'),
  getProductVersions: (code) => request(`/products/${code}/versions`),
  
  // UC-05: Override Case
  overrideCase: (applicationId, overrideCaseRequest) =>
    request(`/cases/${applicationId}/override`, {
      method: 'POST',
      body: JSON.stringify(overrideCaseRequest),
    }),

  // UC-04: Failure Patterns
  reasonCodeCounts: (from, to) => {
    const params = new URLSearchParams();
    params.set('from', from);
    params.set('to', to);
    return request(`/reason-codes?${params.toString()}`);
  },

  // UC-06: Create Product Version
  createProductVersion: (createProductVersionRequest) =>
    request('/products', {
      method: 'POST',
      body: JSON.stringify(createProductVersionRequest),
    }),

  // Get applicant details from the orchestrator
  getApplicantFromOrchestrator: async (applicationId) => {
    const orchestratorUrl = import.meta.env.VITE_ORCHESTRATOR_URL || 
      'http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com';
    try {
      const res = await fetch(`${orchestratorUrl}/api/v1/applications/${applicationId}`, {
        headers: { 'Content-Type': 'application/json' },
      });
      if (!res.ok) {
        const error = new Error(`HTTP ${res.status}`);
        error.status = res.status;
        throw error;
      }
      return res.json();
    } catch (e) {
      // If orchestrator URL fails, try the fallback local backend endpoint
      return request(`/api/v1/applications/${applicationId}`);
    }
  },
};
