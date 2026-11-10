# Deployment checklist (pilot readiness)

This app is **not production ready**. Each item must be checked off by the named owner before any
real patient data is entered.

## Legal, governance, privacy
- [ ] Burundi legal review of data protection basis, consent, retention, deletion and cross-border transfer.
- [ ] Hosting location decided and documented (in-country or approved jurisdiction).
- [ ] Data-sharing agreements with Ministry/district (minimum-necessary, aggregated by default).
- [ ] Breach-response plan, incident contacts, notification timelines.
- [ ] Role definitions and access matrix (see `server-implementation.md` §4) signed off.
- [ ] Procurement transparency for devices/hosting/services.

## Clinical
- [ ] Screening protocol approved and versioned (`clinical-rules-to-approve.md`); `approved: true`.
- [ ] Urgent referral SOP (escort, transport, phone escalation, after-hours).
- [ ] Consent script approved (Kirundi + French), version recorded in `ChwActions.CONSENT_VERSION`.
- [ ] Clinician workflows for diagnosis, prescribing and referral disposition confirmed in clinics.

## Operations (not Android features)
- [ ] Validated automated BP devices procured (validated-device list), correct cuff sizes.
- [ ] Device calibration/verification schedule and replacement process.
- [ ] CHW training: BP technique, danger signs, consent, privacy, app use; competency assessment.
- [ ] Supervision plan by field coordinators; quality checks (re-measurement samples).
- [ ] Phone procurement, MDM/remote-wipe policy, shared-phone policy (one CHW per phone recommended), charging.
- [ ] Incentive design based on coverage + completion **with quality safeguards** (no volume-only incentives).

## Technical
- [ ] Burundi server deployed from a fork of simple-server with the changes in `server-implementation.md`; integration tests passing.
- [ ] Build with `-PmanifestEndpoint=<Burundi manifest>`; the repo default is a deliberately unresolvable `.invalid` host.
- [ ] Replace upstream Firebase project files (`app/src/*/google-services.json`) or remove Firebase
      (Analytics in `ReleaseClinicApp`, Remote Config). No third-party analytics may receive patient data without approval.
- [ ] Replace/disable Sentry (`sentryDsn`), and verify crash reports carry no PHI.
- [ ] New `applicationId`, app name, icon and signing key for the Burundi app (see `implementation-plan.md` §Branding) and review of Simple/Resolve to Save Lives trademarks; keep the MIT `LICENSE`.
- [ ] Enable `Feature.BurundiChwServerSync` only after the server passes integration tests.
- [ ] Server pushes `burundi_programme_role`; remove reliance on debug demo role.
- [ ] Kirundi strings translated and clinically reviewed (`docs/burundi/translations/`).
- [ ] Run instrumented tests on a device farm (Room migration 123→124, UI flows).
- [ ] Pen-test / security review (device loss, PIN reset, token expiry, offline-access expiry, purge on reassignment).
- [ ] DHIS2/EHR export: **off**; only aggregated, legally authorised indicators if/when agreed.
- [ ] Backup/restore and disaster-recovery drill on the server.
- [ ] Synthetic-data end-to-end pilot rehearsal with screenshots and sign-off.

## Shared phones, stolen devices, PIN reset, offline expiry (policy decisions needed)
- Shared phone: recommend one CHW account per phone; if shared, each user logs in separately and
  app lock is short; never share PINs.
- Stolen/lost device: coordinator revokes the user on the server → 401 → app logs out and clears
  data on next connection; local DB is encrypted at rest; app lock protects offline access.
- PIN reset: existing Simple flow (server approval) — keep.
- Offline access expiry: define maximum days offline before forced re-authentication.
