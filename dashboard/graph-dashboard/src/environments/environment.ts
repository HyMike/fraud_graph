// Production build (the dockerized nginx image). The browser always talks to the
// host-mapped ring-detector port, whether Angular is served by `ng serve` or by
// nginx — see infra/docker-compose.yml — so both environments use the same URL today.
export const environment = {
  apiBaseUrl: 'http://localhost:8081',
};
