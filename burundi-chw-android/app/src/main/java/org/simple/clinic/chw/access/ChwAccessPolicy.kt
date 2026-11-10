package org.simple.clinic.chw.access

import org.simple.clinic.chw.access.ChwPermission.BROWSE_ALL_REGIONS
import org.simple.clinic.chw.access.ChwPermission.CONFIRM_HYPERTENSION_DIAGNOSIS
import org.simple.clinic.chw.access.ChwPermission.CREATE_REFERRAL
import org.simple.clinic.chw.access.ChwPermission.DISPOSITION_REFERRAL
import org.simple.clinic.chw.access.ChwPermission.EDIT_MEDICATION_ORDERS
import org.simple.clinic.chw.access.ChwPermission.ENROLL_PATIENT
import org.simple.clinic.chw.access.ChwPermission.EXPORT_PATIENT_LISTS
import org.simple.clinic.chw.access.ChwPermission.MANAGE_USERS_AND_ASSIGNMENTS
import org.simple.clinic.chw.access.ChwPermission.REASSIGN_PATIENTS
import org.simple.clinic.chw.access.ChwPermission.RECORD_CHW_FOLLOW_UP
import org.simple.clinic.chw.access.ChwPermission.RECORD_COMMUNITY_SCREENING
import org.simple.clinic.chw.access.ChwPermission.VIEW_AGGREGATE_REPORTS
import org.simple.clinic.chw.access.ChwPermission.VIEW_ASSIGNED_PATIENTS
import org.simple.clinic.chw.access.ChwPermission.VIEW_CLINIC_PATIENTS
import org.simple.clinic.chw.access.ChwPermission.VIEW_LIMITED_CLINICAL_SUMMARY
import java.util.UUID

/**
 * Pure, side-effect free permission table. The same table is specified for the server in
 * docs/burundi/server-implementation.md so that both sides agree; the server is authoritative.
 */
object ChwAccessPolicy {

  private val grants: Map<ChwRole, Set<ChwPermission>> = mapOf(
      ChwRole.CHW to setOf(
          ENROLL_PATIENT,
          RECORD_COMMUNITY_SCREENING,
          RECORD_CHW_FOLLOW_UP,
          CREATE_REFERRAL,
          VIEW_ASSIGNED_PATIENTS,
          VIEW_LIMITED_CLINICAL_SUMMARY
      ),
      ChwRole.CLINICIAN to setOf(
          ENROLL_PATIENT,
          CONFIRM_HYPERTENSION_DIAGNOSIS,
          EDIT_MEDICATION_ORDERS,
          DISPOSITION_REFERRAL,
          VIEW_CLINIC_PATIENTS,
          VIEW_LIMITED_CLINICAL_SUMMARY
      ),
      ChwRole.FIELD_COORDINATOR to setOf(
          VIEW_AGGREGATE_REPORTS,
          REASSIGN_PATIENTS,
          VIEW_ASSIGNED_PATIENTS
      ),
      ChwRole.PROGRAM_ADMIN to setOf(
          VIEW_AGGREGATE_REPORTS,
          MANAGE_USERS_AND_ASSIGNMENTS,
          REASSIGN_PATIENTS
      ),
      // Upstream Simple clinic users keep their existing clinical workflows unchanged.
      ChwRole.LEGACY_CLINIC_USER to setOf(
          ENROLL_PATIENT,
          CONFIRM_HYPERTENSION_DIAGNOSIS,
          EDIT_MEDICATION_ORDERS,
          VIEW_CLINIC_PATIENTS,
          VIEW_LIMITED_CLINICAL_SUMMARY
      )
  )

  fun permissionsFor(role: ChwRole): Set<ChwPermission> = grants.getValue(role)

  fun isAllowed(role: ChwRole, permission: ChwPermission): Boolean = permission in permissionsFor(role)

  /**
   * A CHW may act on a patient only when the patient is actively assigned to them.
   * Coordinators may act on patients assigned within their area set (server decides membership).
   * Clinical roles are scoped by facility, which upstream Simple already handles via sync groups.
   */
  fun canActOnPatient(
      role: ChwRole,
      actingUserId: UUID,
      activeAssigneeIds: Set<UUID>
  ): Boolean {
    return when (role) {
      ChwRole.CHW -> actingUserId in activeAssigneeIds
      ChwRole.FIELD_COORDINATOR -> activeAssigneeIds.isNotEmpty()
      ChwRole.CLINICIAN, ChwRole.LEGACY_CLINIC_USER -> true
      ChwRole.PROGRAM_ADMIN -> false
    }
  }

  /** Nobody can export patient-level lists or browse all regions from the device in V1. */
  val permissionsNeverGrantedOnDevice: Set<ChwPermission> = setOf(EXPORT_PATIENT_LISTS, BROWSE_ALL_REGIONS)
}
