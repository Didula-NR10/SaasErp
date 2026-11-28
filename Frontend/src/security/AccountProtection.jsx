import { useState } from 'react'
import * as protection from './accountProtection.js'
import './AccountProtection.css'

// Real same-origin flows on the existing React application. Server policy owns every decision.
export default function AccountProtection() {
  const [phase, setPhase] = useState('start')
  const [verified, setVerified] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [provisioning, setProvisioning] = useState('')
  const [codes, setCodes] = useState([])
  const [factors, setFactors] = useState([])
  const [session, setSession] = useState(null)

  async function perform(action) {
    setBusy(true); setMessage('')
    try { await action() } catch (error) { setMessage(error.message || 'Authentication request rejected') }
    finally { setBusy(false) }
  }
  function clearSecrets() { setProvisioning(''); setCodes([]); setFactors([]) }
  async function begin(event) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const email = data.get('email'), password = data.get('password')
    form.reset()
    await perform(async () => {
      await protection.beginMfa(email, password)
      clearSecrets(); setVerified(false); setSession(null); setPhase('factor')
      setMessage('Use an enrolled authenticator. For first setup, request an enrollment email below, approve it, then begin again.')
    })
  }
  async function webauthn(registration) {
    await perform(async () => {
      await protection.webAuthnFactor(registration)
      setVerified(!registration); setMessage(registration ? 'Authenticator added. Use it now to confirm your identity.' : 'Authenticator verified. You can finish signing in or manage your security settings.')
    })
  }
  async function totp(event) {
    event.preventDefault()
    const form = event.currentTarget, code = new FormData(form).get('code')
    form.reset()
    await perform(async () => {
      if (provisioning) await protection.confirmTotp(code)
      else await protection.verifyTotp(code)
      setProvisioning(''); setVerified(true); setMessage('Authenticator verified.')
    })
  }
  async function recover(event) {
    event.preventDefault()
    const form = event.currentTarget, code = new FormData(form).get('code')
    form.reset()
    await perform(async () => {
      await protection.recoverFactors(code)
      clearSecrets(); setVerified(false); setMessage('Set up a replacement factor before finishing sign-in.')
    })
  }
  async function finish() {
    await perform(async () => {
      await protection.finishMfa()
      const response = await fetch('/api/v1/auth/session', { credentials: 'same-origin', cache: 'no-store' })
      if (!response.ok) throw new Error('Session could not be verified')
      setSession(await response.json()); clearSecrets(); setVerified(false); setPhase('complete')
    })
  }
  async function passwordChange(event) {
    event.preventDefault()
    const form = event.currentTarget, data = new FormData(form)
    const current = data.get('currentPassword'), next = data.get('newPassword')
    form.reset()
    await perform(async () => {
      await protection.changePassword(current, next)
      clearSecrets(); setVerified(false); setPhase('start'); setMessage('Password changed. Sign in again.')
    })
  }
  async function emailAction(event) {
    event.preventDefault()
    const form = event.currentTarget, data = new FormData(form), action = event.nativeEvent.submitter?.value
    const email = data.get('email')
    form.reset()
    await perform(async () => {
      if (action === 'verify') await protection.requestEmailVerification(email)
      else if (action === 'enroll') await protection.requestEnrollment(email)
      else await protection.requestPasswordReset(email)
      setMessage('Request accepted.')
    })
  }
  async function completeEmail(event) {
    event.preventDefault()
    const form = event.currentTarget, data = new FormData(form), action = event.nativeEvent.submitter?.value
    const token = data.get('token'), password = data.get('newPassword')
    form.reset()
    await perform(async () => {
      if (action === 'verify') await protection.verifyEmail(token)
      else if (action === 'enroll') await protection.approveEnrollment(token)
      else await protection.resetPassword(token, password)
      clearSecrets(); setVerified(false); setPhase('start'); setMessage('Account updated. Sign in again.')
    })
  }

  return <main className="account-protection">
    <h1>Account security</h1>
    <p>Company: {window.location.hostname}</p>
    <p role="status" aria-live="polite">{busy ? 'Working…' : message}</p>
    <fieldset disabled={busy}>
      <legend>Sign in</legend>
      {phase === 'start' && <form onSubmit={begin}>
        <label>Email<input name="email" type="email" autoComplete="username" required maxLength={254} /></label>
        <label>Password<input name="password" type="password" autoComplete="current-password" required maxLength={128} /></label>
        <button type="submit">Continue</button>
      </form>}
      {phase === 'factor' && <>
        <button onClick={() => perform(async () => { await protection.cancelMfa(); clearSecrets(); setVerified(false); setPhase('start') })}>Start over</button>
        <div className="security-actions">
          <button onClick={() => webauthn(false)}>Use passkey or security key</button>
          <button onClick={() => webauthn(true)}>Add a passkey or security key</button>
          <button onClick={() => perform(async () => { const result = await protection.enrollTotp(); setProvisioning(result.provisioningUri) })}>Set up an authenticator app</button>
        </div>
        {provisioning && <section>
          <h2>One-time authenticator setup</h2>
          <p>Import this address into your authenticator app, then enter its code below. Keep it private.</p>
          <label>Setup address<textarea value={provisioning} readOnly autoComplete="off" /></label>
        </section>}
        <form onSubmit={totp}>
          <label>Authenticator code<input name="code" inputMode="numeric" pattern="[0-9]{6}" autoComplete="one-time-code" required maxLength={6} /></label>
          <button type="submit">{provisioning ? 'Confirm authenticator setup' : 'Verify authenticator code'}</button>
        </form>
        <form onSubmit={recover}>
          <label>Recovery code<input name="code" type="password" autoComplete="off" required maxLength={128} /></label>
          <button type="submit">Recover factors</button>
        </form>
        {verified && <section>
          <h2>Security settings</h2>
          <div className="security-actions">
            <button onClick={finish}>Finish sign-in</button>
            <button onClick={() => perform(async () => { const result = await protection.recoveryCodes(); setCodes(result.codes) })}>Replace recovery codes</button>
            <button onClick={() => perform(async () => setFactors(await protection.listFactors()))}>Manage authenticators</button>
            <button onClick={() => perform(async () => { await protection.revokeAllSessions(); clearSecrets(); setVerified(false); setPhase('start'); setMessage('Sessions revoked. Sign in again.') })}>Revoke all sessions</button>
          </div>
          {codes.length > 0 && <section>
            <h3>Save your recovery codes now</h3>
            <ul>{codes.map(code => <li key={code}><code>{code}</code></li>)}</ul>
            <button onClick={() => setCodes([])}>I saved these codes</button>
          </section>}
          <ul>{factors.map(factor => <li key={factor.credentialId}>
            {factor.type} <button onClick={() => perform(async () => {
              await protection.removeFactor(factor.credentialId); setFactors([]); setVerified(false)
              setMessage('Authenticator removed. Verify a remaining factor before continuing.')
            })}>Remove</button>
          </li>)}</ul>
          <form onSubmit={passwordChange}>
            <label>Current password<input name="currentPassword" type="password" autoComplete="current-password" required maxLength={128} /></label>
            <label>New password<input name="newPassword" type="password" autoComplete="new-password" required minLength={15} maxLength={128} /></label>
            <button type="submit">Change password</button>
          </form>
        </section>}
      </>}
      {phase === 'complete' && <section>
        <h2>Signed in</h2>
        <p>Company: {session?.hostname}</p><p>Authentication: {session?.assurance}</p>
        <a href="/">Return to ERP</a>
        <button onClick={() => perform(async () => { await protection.logout(); clearSecrets(); setSession(null); setPhase('start') })}>Sign out</button>
        <button onClick={() => { clearSecrets(); setSession(null); setVerified(false); setPhase('start') }}>Start another security session</button>
      </section>}
    </fieldset>
    <fieldset disabled={busy}>
      <legend>Email verification and password recovery</legend>
      <form onSubmit={emailAction}>
        <label>Email<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>
        <button type="submit" value="verify">Request verification</button>
        <button type="submit" value="enroll">Request factor enrollment</button>
        <button type="submit" value="reset">Request password reset</button>
      </form>
      <form onSubmit={completeEmail}>
        <label>Code from your email<input name="token" type="password" autoComplete="off" required maxLength={128} /></label>
        <label>New password for a reset<input name="newPassword" type="password" autoComplete="new-password" minLength={15} maxLength={128} /></label>
        <button type="submit" value="verify">Verify email</button>
        <button type="submit" value="enroll">Approve factor enrollment</button>
        <button type="submit" value="reset">Reset password</button>
      </form>
    </fieldset>
  </main>
}
