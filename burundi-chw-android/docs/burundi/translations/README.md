# Kirundi translation workflow

- Source of truth: `app/src/main/res/values/strings_burundi_chw.xml` (English).
- Review sheet: `kirundi-review.csv` (one row per string; `needs_clinical_review=yes` for medical wording).
- Target file: `app/src/main/res/values-rn-rBI/strings_burundi_chw.xml` — intentionally empty today, so
  the app falls back to English for every CHW string.
- Process: professional Kirundi translator → Burundi clinician review of every `needs_clinical_review`
  row → back-translation spot check → add to `values-rn-rBI` → screenshot review on device.
- Do **not** machine-translate medical text. Keep placeholders (`%1$s`, `%1$d`) intact.
- French (`values-fr`) may also be needed for clinic staff; not started.
- Upstream Simple strings (non-CHW screens) are not translated to Kirundi either; they are managed via
  Transifex upstream (`.transifex/`). A Burundi deployment needs its own translation project.
- The language picker lists "Ikirundi (translation pending)" on non-production builds only.
