package org.simple.clinic.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.simple.clinic.storage.inTransaction
import javax.inject.Inject

/**
 * Burundi CHW module. Purely additive: creates seven new tables and their indices.
 * No existing table, column or row is modified or dropped.
 */
@Suppress("ClassName")
class Migration_124 @Inject constructor() : Migration(123, 124) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.inTransaction {
      execSQL("""
        CREATE TABLE IF NOT EXISTS "CommunityArea" (
          "id" TEXT NOT NULL,
          "name" TEXT NOT NULL,
          "province" TEXT,
          "commune" TEXT,
          "zone" TEXT,
          "colline" TEXT,
          "parentAreaId" TEXT,
          "active" INTEGER NOT NULL,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          PRIMARY KEY("id")
        )
      """)

      execSQL("""
        CREATE TABLE IF NOT EXISTS "ChwAssignment" (
          "id" TEXT NOT NULL,
          "chwUserId" TEXT NOT NULL,
          "communityAreaId" TEXT NOT NULL,
          "supervisingFacilityId" TEXT NOT NULL,
          "startDate" TEXT NOT NULL,
          "endDate" TEXT,
          "status" TEXT NOT NULL,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwAssignment_chwUserId` ON `ChwAssignment` (`chwUserId`)")

      execSQL("""
        CREATE TABLE IF NOT EXISTS "PatientChwAssignment" (
          "id" TEXT NOT NULL,
          "patientId" TEXT NOT NULL,
          "chwUserId" TEXT NOT NULL,
          "communityAreaId" TEXT,
          "assignedAt" TEXT NOT NULL,
          "status" TEXT NOT NULL,
          "endReason" TEXT,
          "previousAssignmentId" TEXT,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          "syncStatus" TEXT NOT NULL,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_PatientChwAssignment_patientId` ON `PatientChwAssignment` (`patientId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_PatientChwAssignment_chwUserId_status` ON `PatientChwAssignment` (`chwUserId`, `status`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_PatientChwAssignment_syncStatus` ON `PatientChwAssignment` (`syncStatus`)")

      execSQL("""
        CREATE TABLE IF NOT EXISTS "ScreeningEpisode" (
          "id" TEXT NOT NULL,
          "patientId" TEXT NOT NULL,
          "chwUserId" TEXT NOT NULL,
          "communityAreaId" TEXT,
          "supervisingFacilityId" TEXT NOT NULL,
          "occurredAt" TEXT NOT NULL,
          "locationType" TEXT NOT NULL,
          "consentVersion" TEXT NOT NULL,
          "firstSystolic" INTEGER NOT NULL,
          "firstDiastolic" INTEGER NOT NULL,
          "secondSystolic" INTEGER,
          "secondDiastolic" INTEGER,
          "minutesBetweenReadings" INTEGER,
          "dangerSigns" TEXT NOT NULL,
          "specialPopulation" TEXT NOT NULL,
          "outcome" TEXT NOT NULL,
          "reasonCodes" TEXT NOT NULL,
          "decisionRuleVersion" TEXT NOT NULL,
          "decisionRuleApproved" INTEGER NOT NULL,
          "referralId" TEXT,
          "followUpTaskId" TEXT,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          "syncStatus" TEXT NOT NULL,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_ScreeningEpisode_patientId` ON `ScreeningEpisode` (`patientId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ScreeningEpisode_chwUserId` ON `ScreeningEpisode` (`chwUserId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ScreeningEpisode_syncStatus` ON `ScreeningEpisode` (`syncStatus`)")

      execSQL("""
        CREATE TABLE IF NOT EXISTS "ChwReferral" (
          "id" TEXT NOT NULL,
          "patientId" TEXT NOT NULL,
          "screeningEpisodeId" TEXT,
          "sourceUserId" TEXT NOT NULL,
          "originatingAreaId" TEXT,
          "destinationFacilityId" TEXT NOT NULL,
          "priority" TEXT NOT NULL,
          "reasonCodes" TEXT NOT NULL,
          "status" TEXT NOT NULL,
          "requestedAt" TEXT NOT NULL,
          "dueDate" TEXT NOT NULL,
          "acknowledgedAt" TEXT,
          "completedAt" TEXT,
          "clinicianUserId" TEXT,
          "outcomeCode" TEXT,
          "nextActionCode" TEXT,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          "syncStatus" TEXT NOT NULL,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwReferral_patientId` ON `ChwReferral` (`patientId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwReferral_destinationFacilityId` ON `ChwReferral` (`destinationFacilityId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwReferral_sourceUserId` ON `ChwReferral` (`sourceUserId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwReferral_syncStatus` ON `ChwReferral` (`syncStatus`)")

      execSQL("""
        CREATE TABLE IF NOT EXISTS "ChwFollowUpTask" (
          "id" TEXT NOT NULL,
          "patientId" TEXT NOT NULL,
          "chwUserId" TEXT NOT NULL,
          "communityAreaId" TEXT,
          "type" TEXT NOT NULL,
          "dueDate" TEXT NOT NULL,
          "status" TEXT NOT NULL,
          "screeningEpisodeId" TEXT,
          "referralId" TEXT,
          "completedByContactId" TEXT,
          "rescheduleCount" INTEGER NOT NULL,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          "syncStatus" TEXT NOT NULL,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwFollowUpTask_patientId` ON `ChwFollowUpTask` (`patientId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwFollowUpTask_chwUserId_status_dueDate` ON `ChwFollowUpTask` (`chwUserId`, `status`, `dueDate`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwFollowUpTask_syncStatus` ON `ChwFollowUpTask` (`syncStatus`)")

      execSQL("""
        CREATE TABLE IF NOT EXISTS "ChwContact" (
          "id" TEXT NOT NULL,
          "patientId" TEXT NOT NULL,
          "chwUserId" TEXT NOT NULL,
          "taskId" TEXT,
          "occurredAt" TEXT NOT NULL,
          "method" TEXT NOT NULL,
          "outcome" TEXT NOT NULL,
          "systolic" INTEGER,
          "diastolic" INTEGER,
          "dangerSigns" TEXT NOT NULL,
          "medicineAvailability" TEXT NOT NULL,
          "sideEffectConcern" INTEGER,
          "nextDueDate" TEXT,
          "note" TEXT,
          "createdAt" TEXT NOT NULL,
          "updatedAt" TEXT NOT NULL,
          "deletedAt" TEXT,
          "syncStatus" TEXT NOT NULL,
          PRIMARY KEY("id")
        )
      """)
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwContact_patientId` ON `ChwContact` (`patientId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwContact_taskId` ON `ChwContact` (`taskId`)")
      execSQL("CREATE INDEX IF NOT EXISTS `index_ChwContact_syncStatus` ON `ChwContact` (`syncStatus`)")
    }
  }
}
