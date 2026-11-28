// Same-origin production API integration. Credentials remain in native cookies or transient memory.
// Call from the existing React UI on an explicit user gesture; do not persist form inputs or results.
let csrfToken;

async function csrf() {
  if (csrfToken) return csrfToken;
  let response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
  // A revoked session is invalidated by the first request; obtain fresh anonymous CSRF state once.
  if (response.status === 401) response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new Error('Authentication service unavailable');
  csrfToken = (await response.json()).token;
  return csrfToken;
}

async function post(path, body, form = false) {
  const token = await csrf();
  const response = await fetch(`/api/v1/auth/${path}`, {
    method: 'POST', credentials: 'same-origin', cache: 'no-store',
    headers: { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json', 'X-CSRF-TOKEN': token },
    body: form ? new URLSearchParams(body) : JSON.stringify(body ?? {}),
  });
  // Never automatically retry factor proofs, recovery codes, or state-changing requests.
  if (!response.ok) {
    if ([401, 403, 503].includes(response.status)) csrfToken = undefined;
    throw new Error(response.status === 429 ? 'Try again later' : 'Authentication request rejected');
  }
  return response.status === 204 || response.status === 202 ? undefined : response.json();
}

export async function beginMfa(email, password) {
  await post('mfa/start', { email, password }, true);
}

export async function webAuthnFactor(registration = false) {
  if (!window.isSecureContext || !navigator.credentials || !window.PublicKeyCredential) throw new Error('WebAuthn requires a supported secure browser');
  const phase = registration ? 'registration' : 'authentication';
  const parser = registration ? PublicKeyCredential.parseCreationOptionsFromJSON : PublicKeyCredential.parseRequestOptionsFromJSON;
  if (typeof parser !== 'function') throw new Error('This browser does not support the required WebAuthn JSON API');
  const options = await post(`mfa/webauthn/${phase}/options`);
  const publicKey = parser.call(PublicKeyCredential, options.publicKey);
  const credential = registration ? await navigator.credentials.create({ publicKey }) : await navigator.credentials.get({ publicKey });
  if (!credential || typeof credential.toJSON !== 'function') throw new Error('Authenticator operation did not complete');
  await post(`mfa/webauthn/${phase}/complete`, { challengeId: options.challengeId, credential: JSON.stringify(credential.toJSON()) });
}

export const enrollTotp = () => post('mfa/totp/enroll'); // Display the one-time provisioning URI only to the enrolling user; never log/store it.
export const confirmTotp = code => post('mfa/totp/confirm', { code });
export const verifyTotp = code => post('mfa/totp/verify', { code });
export const recoveryCodes = () => post('mfa/recovery-codes'); // Show once; clear UI state after acknowledgement.
export const recoverFactors = code => post('mfa/recover', { code });
export const listFactors = () => post('mfa/factors/list');
export const removeFactor = credentialId => post('mfa/factors/remove', { credentialId });
export const changePassword = (currentPassword, newPassword) => post('account/password/change', { currentPassword, newPassword });
export const revokeAllSessions = () => post('account/sessions/revoke-all');
export const requestEmailVerification = email => post('account/email/request', { email });
export async function verifyEmail(code) { await post('account/email/verify', { code }); csrfToken = undefined; }
export const requestPasswordReset = email => post('account/password/request-reset', { email });
export async function resetPassword(token, newPassword) { await post('account/password/reset', { token, newPassword }); csrfToken = undefined; }
export async function finishMfa() {
  await post('mfa/finish');
  csrfToken = undefined; // Native authentication rotates CSRF state; fetch it again for the next mutation.
}

export const cancelMfa = () => post('mfa/cancel');
export async function logout() { await post('logout'); csrfToken = undefined; }

export const requestEnrollment = email => post('account/mfa/request-enrollment', { email });
export async function approveEnrollment(code) { await post('account/mfa/approve-enrollment', { code }); csrfToken = undefined; }
