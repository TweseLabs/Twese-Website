package org.simple.clinic.chw.referral

import androidx.room.TypeConverter
import org.simple.clinic.chw.CodedEnum
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.codeToEnum

enum class ReferralStatus(override val code: String) : CodedEnum {
  DRAFT("draft"),

  /** Saved on this phone; the clinic has NOT received it. */
  SUBMITTED_PENDING_SYNC("submitted_pending_sync"),

  /** Server has recorded it in the destination clinic's queue. Only the server may set this. */
  RECEIVED("received"),
  ACCEPTED("accepted"),
  COMPLETED("completed"),
  UNABLE_TO_CONTACT("unable_to_contact"),
  CANCELLED("cancelled"),
  CLOSED_UNRESOLVED("closed_unresolved"),
  UNKNOWN("unknown");

  val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED || this == CLOSED_UNRESOLVED

  /** True once the clinic side has demonstrably got the referral (never true on device only). */
  val isReceivedByClinic: Boolean get() = this in setOf(RECEIVED, ACCEPTED, COMPLETED, UNABLE_TO_CONTACT, CLOSED_UNRESOLVED)

  class RoomTypeConverter {
    @TypeConverter fun from(v: ReferralStatus?) = v?.code
    @TypeConverter fun to(v: String?) = v?.let { codeToEnum(it, UNKNOWN) }
  }
}

/** Who performs a transition. SERVER transitions can never be performed from the device. */
enum class ReferralActor { CHW, CLINICIAN, FIELD_COORDINATOR, SERVER }

sealed interface TransitionResult {
  data class Allowed(val to: ReferralStatus) : TransitionResult
  data class Denied(val reason: String) : TransitionResult
}

/**
 * Referral lifecycle. Mirrors the server's authoritative state machine
 * (docs/burundi/server-implementation.md §Referral state machine).
 */
object ReferralStateMachine {

  private val transitions: Map<Pair<ReferralStatus, ReferralStatus>, Set<ReferralActor>> = mapOf(
      (ReferralStatus.DRAFT to ReferralStatus.SUBMITTED_PENDING_SYNC) to setOf(ReferralActor.CHW),
      (ReferralStatus.DRAFT to ReferralStatus.CANCELLED) to setOf(ReferralActor.CHW),
      (ReferralStatus.SUBMITTED_PENDING_SYNC to ReferralStatus.CANCELLED) to setOf(ReferralActor.CHW),
      (ReferralStatus.SUBMITTED_PENDING_SYNC to ReferralStatus.RECEIVED) to setOf(ReferralActor.SERVER),
      (ReferralStatus.RECEIVED to ReferralStatus.ACCEPTED) to setOf(ReferralActor.CLINICIAN),
      (ReferralStatus.RECEIVED to ReferralStatus.UNABLE_TO_CONTACT) to setOf(ReferralActor.CLINICIAN),
      (ReferralStatus.ACCEPTED to ReferralStatus.UNABLE_TO_CONTACT) to setOf(ReferralActor.CLINICIAN),
      (ReferralStatus.UNABLE_TO_CONTACT to ReferralStatus.ACCEPTED) to setOf(ReferralActor.CLINICIAN),
      (ReferralStatus.ACCEPTED to ReferralStatus.COMPLETED) to setOf(ReferralActor.CLINICIAN),
      (ReferralStatus.RECEIVED to ReferralStatus.CLOSED_UNRESOLVED) to setOf(ReferralActor.CLINICIAN, ReferralActor.FIELD_COORDINATOR),
      (ReferralStatus.ACCEPTED to ReferralStatus.CLOSED_UNRESOLVED) to setOf(ReferralActor.CLINICIAN, ReferralActor.FIELD_COORDINATOR),
      (ReferralStatus.UNABLE_TO_CONTACT to ReferralStatus.CLOSED_UNRESOLVED) to setOf(ReferralActor.CLINICIAN, ReferralActor.FIELD_COORDINATOR)
  )

  fun transition(
      from: ReferralStatus,
      to: ReferralStatus,
      actor: ReferralActor,
      outcomeCode: String? = null
  ): TransitionResult {
    val allowedActors = transitions[from to to]
        ?: return TransitionResult.Denied("No transition from ${from.code} to ${to.code}")
    if (actor !in allowedActors) {
      return TransitionResult.Denied("${actor.name} may not move a referral from ${from.code} to ${to.code}")
    }
    if (to == ReferralStatus.COMPLETED && outcomeCode.isNullOrBlank()) {
      return TransitionResult.Denied("Completing a referral requires a clinician outcome")
    }
    return TransitionResult.Allowed(to)
  }

  fun actorFor(role: ChwRole): ReferralActor? = when (role) {
    ChwRole.CHW -> ReferralActor.CHW
    ChwRole.CLINICIAN -> ReferralActor.CLINICIAN
    ChwRole.FIELD_COORDINATOR -> ReferralActor.FIELD_COORDINATOR
    // Legacy clinic users have not been granted programme referral disposition.
    ChwRole.LEGACY_CLINIC_USER, ChwRole.PROGRAM_ADMIN -> null
  }
}
