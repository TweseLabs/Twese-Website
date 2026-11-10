package org.simple.clinic.chw

import dagger.Lazy
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.access.ChwSession
import org.simple.clinic.chw.area.ChwAssignment
import org.simple.clinic.chw.area.CommunityArea
import org.simple.clinic.chw.followup.FollowUpPlanResult
import org.simple.clinic.chw.followup.FollowUpPlanner
import org.simple.clinic.chw.followup.FollowUpRequest
import org.simple.clinic.chw.screening.PlanResult
import org.simple.clinic.chw.screening.ScreeningClassifier
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.ScreeningPlanner
import org.simple.clinic.chw.screening.ScreeningProtocol
import org.simple.clinic.chw.screening.ScreeningProtocolSource
import org.simple.clinic.chw.screening.ScreeningRequest
import org.simple.clinic.facility.Facility
import org.simple.clinic.storage.Timestamps
import org.simple.clinic.user.User
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.simple.clinic.uuid.UuidGenerator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Who/where the current CHW is working. */
data class ChwContext(
    val user: User,
    val role: ChwRole,
    val isDemoRole: Boolean,
    val assignment: ChwAssignment?,
    val area: CommunityArea?,
    val supervisingFacilityId: UUID,
    val protocol: ScreeningProtocol
)

/**
 * Glue between screens and the pure planners: collects the session context, plans, persists.
 * Must be called off the main thread.
 */
class ChwActions @Inject constructor(
    private val repository: ChwRepository,
    private val session: ChwSession,
    private val protocolSource: ScreeningProtocolSource,
    private val currentUser: Lazy<User>,
    private val currentFacility: Lazy<Facility>,
    private val uuidGenerator: UuidGenerator,
    private val utcClock: UtcClock,
    private val userClock: UserClock
) {

  fun context(): ChwContext {
    val user = currentUser.get()
    val today = LocalDate.now(userClock)
    val assignment = repository.activeChwAssignment(user.uuid, today)
    return ChwContext(
        user = user,
        role = session.role(),
        isDemoRole = session.isUsingDemoRole(),
        assignment = assignment,
        area = assignment?.let { repository.area(it.communityAreaId) },
        // Without a server-provided assignment we fall back to the user's current clinic.
        supervisingFacilityId = assignment?.supervisingFacilityId ?: user.currentFacilityUuid,
        protocol = protocolSource.current()
    )
  }

  fun currentFacilityName(): String = currentFacility.get().name

  fun recordScreening(
      patientId: UUID,
      locationType: ScreeningLocationType,
      consentGiven: Boolean,
      input: ScreeningInput
  ): PlanResult {
    val ctx = context()
    val planner = ScreeningPlanner(ScreeningClassifier(ctx.protocol), uuidGenerator::v4)
    val result = planner.plan(
        request = ScreeningRequest(
            patientId = patientId,
            chwUserId = ctx.user.uuid,
            role = ctx.role,
            communityAreaId = ctx.assignment?.communityAreaId,
            supervisingFacilityId = ctx.supervisingFacilityId,
            occurredAt = Instant.now(utcClock),
            today = LocalDate.now(userClock),
            locationType = locationType,
            consentVersion = CONSENT_VERSION,
            consentGiven = consentGiven,
            input = input
        ),
        currentAssignments = repository.currentAssignmentsForPatient(patientId),
        openTasksForPatient = repository.openTasksForPatient(patientId)
    )
    if (result is PlanResult.Planned) repository.saveScreening(result.plan)
    return result
  }

  fun recordFollowUp(build: (ChwContext, Instant, LocalDate) -> FollowUpRequest): FollowUpPlanResult {
    val ctx = context()
    val request = build(ctx, Instant.now(utcClock), LocalDate.now(userClock))
    val result = FollowUpPlanner(ScreeningClassifier(ctx.protocol), uuidGenerator::v4).plan(
        request = request,
        currentAssignments = repository.currentAssignmentsForPatient(request.patientId),
        openTasksForPatient = repository.openTasksForPatient(request.patientId)
    )
    if (result is FollowUpPlanResult.Planned) repository.saveFollowUp(result.plan)
    return result
  }

  /**
   * DEBUG BUILDS ONLY: creates a synthetic area + assignment for the current user so the CHW flow
   * can be demonstrated without a server. Clearly named as synthetic; never runs on release builds.
   */
  fun seedDemoAreaIfAllowed(): Boolean {
    if (!session.isDemoRoleAllowed) return false
    val user = currentUser.get()
    val now = Instant.now(utcClock)
    val ts = Timestamps(now, now, null)
    val area = CommunityArea(
        id = uuidGenerator.v4(),
        name = "DEMO synthetic colline",
        province = "DEMO",
        commune = "DEMO commune",
        zone = null,
        colline = "DEMO synthetic colline",
        parentAreaId = null,
        active = true,
        timestamps = ts
    )
    val assignment = ChwAssignment(
        id = uuidGenerator.v4(),
        chwUserId = user.uuid,
        communityAreaId = area.id,
        supervisingFacilityId = user.currentFacilityUuid,
        startDate = LocalDate.now(userClock).minusDays(1),
        endDate = null,
        status = AssignmentStatus.ACTIVE,
        timestamps = ts
    )
    repository.saveAreaAndAssignment(area, assignment)
    return true
  }

  companion object {
    /** Placeholder; the approved consent script/version must be supplied by the programme. */
    const val CONSENT_VERSION = "consent-draft-0.1-unapproved"
  }
}
