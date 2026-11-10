# Clinical rules requiring approval before any pilot

**Nothing below is approved.** Every value is a software placeholder, loosely shaped after common
WHO HEARTS conventions, so the workflow can be built and tested. They must be reviewed, changed as
needed and signed off by qualified Burundi clinicians, aligned with national guidance (Ministère de
la Santé Publique et de la Lutte contre le Sida) and WHO guidance, before use with real people.

Where the values live (one place, versioned):
- `app/src/main/assets/burundi/screening_protocol.json` — loaded at runtime (`ScreeningProtocolSource`).
- `ScreeningProtocol.DRAFT_UNAPPROVED` — in-code fallback; `ScreeningProtocolAssetTest` fails if the two diverge.
- Every `ScreeningEpisode` stores `decision_rule_version` and `decision_rule_approved`, so records made
  under the draft can be identified and re-reviewed later.

To approve: change the JSON, set a new `version`, `"approved": true`, `"approved_by": "<name/committee, date>"`,
update `DRAFT_UNAPPROVED` or rename it, update `ScreeningProtocolAssetTest` deliberately, and record
the sign-off in this file. The "DRAFT screening rules" banner disappears only when `approved` is true.

## A. Screening protocol (current placeholder values)

| # | Decision | Placeholder | Question for clinicians |
|---|---|---|---|
| A1 | Implausible reading bounds | SBP 70–300, DBP 40–200, DBP < SBP | Correct bounds for re-measure prompt? |
| A2 | "Elevated" screening threshold | ≥140 systolic **or** ≥90 diastolic | Confirm for community screening in Burundi |
| A3 | "Urgent" threshold | ≥180 systolic **or** ≥110 diastolic → urgent clinical assessment (same day) | With vs. without symptoms — same action? |
| A4 | Second reading | Required when the first is elevated; ≥1 minute apart | Number of readings, interval, which to use (average vs. last) |
| A5 | Inconsistent readings | Systolic difference >25 mmHg → repeat required | Threshold? |
| A6 | Decision on two readings | Average of the two | Average, second only, or lowest? |
| A7 | Danger signs (any → urgent) | severe headache, chest pain, shortness of breath, weakness/numbness/speech difficulty, sudden vision change, confusion/reduced consciousness | Final list, wording in Kirundi, and whether each is "urgent" or "same-week" |
| A8 | Symptom not on the protocol list | → needs clinician review | OK? |
| A9 | Special populations | pregnant/≤6 weeks post-partum, under 18, unknown → needs clinician review | Pregnancy: separate obstetric pathway? Age limits for screening? |
| A10 | Repeat-screen interval | 7 days | |
| A11 | Confirmation referral due | 14 days | Target for "confirmations completed within target period" |
| A12 | Clinician review due | 7 days | |
| A13 | Normal → re-screen | 365 days | Different for ≥40 y / risk factors? |
| A14 | Urgent referral due | same day | Plus escort/transport SOP (see C) |

Rule order is fixed in code (`ScreeningClassifier`): implausible → reject; danger sign or urgent
reading → urgent; incomplete checklist / unlisted symptom / special population / no reading →
clinician review; elevated single reading → repeat; inconsistent → repeat; average elevated →
confirmation; else normal. Anything unanticipated → clinician review. Changing the *order* is a code
change and must be re-approved.

## B. Follow-up policy

| # | Decision | Placeholder |
|---|---|---|
| B1 | Follow-up visit frequency after diagnosis | CHW chooses 3/7/14/30/90 days; default 30 |
| B2 | Failed-attempt reschedule default | 3 days |
| B3 | "Lost to follow-up" | ≥3 failed attempts on ≥3 distinct days AND ≥90 days since last successful contact |
| B4 | Follow-up danger signs / urgent BP | Same rules as screening → urgent referral |
| B5 | Side-effect concern | Advise clinic visit (no CHW medicine advice) |
| B6 | Medicine questions | Patient-reported availability only; CHW never changes medicines |

## C. Urgent referral SOP (operational, not software)

The app shows "go to <clinic> or emergency care NOW" and states it does **not** alert the clinic.
The programme must define: escort/transport, phone escalation to the clinic, what to do at night,
and how the CHW records that the person reached care. Push notifications/SMS are **not** proof of receipt.

## D. Consent

`consent-draft-0.1-unapproved` placeholder. Needed: approved consent script (Kirundi/French),
legal basis, what is explained about storage/retention/sharing, how refusals are recorded, minors.

## E. Explicitly out of scope (not implemented, by design)

Automatic diagnosis; automatic dosing or medicine recommendation; the previously discussed
amlodipine/telmisartan escalation as an automatic algorithm; AI risk scores; OCR/photo of BP
devices (the "photo readout" idea is a deferred, optional pilot experiment); Bluetooth/home devices;
SMS-only registration.

## Sign-off record

| Version | Approved by | Date | Notes |
|---|---|---|---|
| draft-0.1-unapproved | — | — | Engineering placeholder only |
