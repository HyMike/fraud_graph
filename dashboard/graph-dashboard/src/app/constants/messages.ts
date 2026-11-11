import { environment } from '../../environments/environment';

/** Shown when a request to ring-detector fails at the network level (status 0). */
export const API_UNREACHABLE_MESSAGE =
  `Could not reach ring-detector. Is it running on ${new URL(environment.apiBaseUrl).host}?`;
