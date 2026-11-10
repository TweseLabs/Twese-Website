package org.simple.clinic.chw.access

/**
 * Burundi programme roles.
 *
 * IMPORTANT: the role shown on the device is a *client-side hint* used to shape the UI and to
 * refuse actions early. It is NOT a security boundary. Every permission listed in
 * [ChwAccessPolicy] must also be enforced by the server (see docs/burundi/server-implementation.md).
 *
 * [LEGACY_CLINIC_USER] is the role assumed when the server has not (yet) sent a programme role.
 * It preserves upstream Simple behaviour for existing clinic users so that this fork does not
 * silently remove clinical capabilities from clinicians who were already using the app.
 */
enum class ChwRole(val code: String) {
  CHW("chw"),
  CLINICIAN("clinician"),
  FIELD_COORDINATOR("field_coordinator"),
  PROGRAM_ADMIN("program_admin"),
  LEGACY_CLINIC_USER("legacy_clinic_user");

  companion object {
    /**
     * Unknown or missing codes fall back to [LEGACY_CLINIC_USER] rather than to a more
     * privileged programme role.
     */
    fun fromCode(code: String?): ChwRole {
      return entries.firstOrNull { it.code == code } ?: LEGACY_CLINIC_USER
    }
  }
}

enum class ChwPermission {
  ENROLL_PATIENT,
  RECORD_COMMUNITY_SCREENING,
  RECORD_CHW_FOLLOW_UP,
  CREATE_REFERRAL,
  VIEW_ASSIGNED_PATIENTS,
  VIEW_LIMITED_CLINICAL_SUMMARY,

  CONFIRM_HYPERTENSION_DIAGNOSIS,
  EDIT_MEDICATION_ORDERS,
  DISPOSITION_REFERRAL,
  VIEW_CLINIC_PATIENTS,

  VIEW_AGGREGATE_REPORTS,
  REASSIGN_PATIENTS,
  MANAGE_USERS_AND_ASSIGNMENTS,
  EXPORT_PATIENT_LISTS,
  BROWSE_ALL_REGIONS
}
