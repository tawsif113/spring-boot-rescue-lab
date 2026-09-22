import http from 'k6/http';
import {check} from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const pageSize = __ENV.PAGE_SIZE || '100';

export const options = {
  scenarios: {
    steady_order_search: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250'],
  },
};

export function setup() {
  const response = http.get(`${baseUrl}/actuator/health`);
  check(response, {'application is ready': (result) => result.status === 200});
}

export default function () {
  const response = http.get(`${baseUrl}/api/orders?page=0&size=${pageSize}`, {
    tags: {incident: 'INC-001', endpoint: 'order-search'},
  });

  check(response, {
    'order search returns 200': (result) => result.status === 200,
    'response contains a full page': (result) => {
      const body = result.json();
      return body.content && body.content.length === Number(pageSize);
    },
  });
}

export function handleSummary(data) {
  return {
    'build/evidence/inc-001/k6-summary.json': JSON.stringify(data, null, 2),
    stdout: `INC-001 p95: ${data.metrics.http_req_duration.values['p(95)']} ms\n`,
  };
}
