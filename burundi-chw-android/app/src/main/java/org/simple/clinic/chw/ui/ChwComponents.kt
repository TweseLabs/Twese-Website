package org.simple.clinic.chw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.simple.clinic.R
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BannerAmber = Color(0xFFFFF4CE)
private val BannerRed = Color(0xFFFFE1E1)
private val BannerBlue = Color(0xFFE6F0FF)

enum class BannerTone { INFO, WARNING, DANGER }

@Composable
fun ChwBanner(text: String, tone: BannerTone, modifier: Modifier = Modifier) {
  val bg = when (tone) {
    BannerTone.INFO -> BannerBlue
    BannerTone.WARNING -> BannerAmber
    BannerTone.DANGER -> BannerRed
  }
  Text(
      text = text,
      modifier = modifier
          .fillMaxWidth()
          .background(bg)
          .padding(12.dp),
      style = MaterialTheme.typography.body2,
      fontWeight = if (tone == BannerTone.DANGER) FontWeight.Bold else FontWeight.Normal
  )
}

@Composable
fun ChwSectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Card(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), elevation = 1.dp) {
    Column(Modifier.padding(12.dp)) {
      if (title != null) {
        Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
      }
      content()
    }
  }
}

@Composable
fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
  Column {
    options.forEach { (value, label) ->
      Row(
          Modifier
              .fillMaxWidth()
              .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
      ) {
        RadioButton(selected = value == selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
      }
    }
  }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
  Row(
      Modifier
          .fillMaxWidth()
          .toggleable(value = checked, onValueChange = { onToggle() }, role = Role.Checkbox)
          .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Text(label, Modifier.padding(start = 8.dp))
  }
}

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
fun dangerSignLabel(sign: DangerSign): String = stringResource(when (sign) {
  DangerSign.SEVERE_HEADACHE -> R.string.chw_symptom_severe_headache
  DangerSign.CHEST_PAIN -> R.string.chw_symptom_chest_pain
  DangerSign.SHORTNESS_OF_BREATH -> R.string.chw_symptom_shortness_of_breath
  DangerSign.WEAKNESS_NUMBNESS_OR_SPEECH_DIFFICULTY -> R.string.chw_symptom_weakness
  DangerSign.VISION_CHANGES -> R.string.chw_symptom_vision
  DangerSign.CONFUSION_OR_REDUCED_CONSCIOUSNESS -> R.string.chw_symptom_confusion
  DangerSign.NONE -> R.string.chw_symptom_none
})

@Composable
fun populationLabel(p: SpecialPopulation): String = stringResource(when (p) {
  SpecialPopulation.NONE -> R.string.chw_population_none
  SpecialPopulation.PREGNANT_OR_RECENTLY_DELIVERED -> R.string.chw_population_pregnant
  SpecialPopulation.UNDER_18 -> R.string.chw_population_under_18
  SpecialPopulation.UNKNOWN -> R.string.chw_population_unknown
})

@Composable
fun outcomeLabel(o: ScreeningOutcome): String = stringResource(when (o) {
  ScreeningOutcome.NORMAL_SCREENING -> R.string.chw_result_normal
  ScreeningOutcome.REPEAT_REQUIRED -> R.string.chw_result_repeat
  ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED -> R.string.chw_result_confirmation
  ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT -> R.string.chw_result_urgent
  ScreeningOutcome.NEEDS_CLINICIAN_REVIEW -> R.string.chw_result_review
})

@Composable
fun taskTypeLabel(t: FollowUpTaskType?): String = stringResource(when (t) {
  FollowUpTaskType.REPEAT_SCREENING -> R.string.chw_task_repeat_screening
  FollowUpTaskType.CHECK_CLINIC_CONFIRMATION -> R.string.chw_task_check_clinic_confirmation
  FollowUpTaskType.URGENT_REFERRAL_CHECK -> R.string.chw_task_urgent_referral_check
  FollowUpTaskType.ROUTINE_FOLLOW_UP -> R.string.chw_task_routine_follow_up
  FollowUpTaskType.RESCREEN -> R.string.chw_task_rescreen
  FollowUpTaskType.UNKNOWN, null -> R.string.chw_task_unknown
})

@Composable
fun referralStatusLabel(s: ReferralStatus): String = stringResource(when (s) {
  ReferralStatus.DRAFT -> R.string.chw_referral_status_draft
  ReferralStatus.SUBMITTED_PENDING_SYNC -> R.string.chw_referral_status_pending_sync
  ReferralStatus.RECEIVED -> R.string.chw_referral_status_received
  ReferralStatus.ACCEPTED -> R.string.chw_referral_status_accepted
  ReferralStatus.COMPLETED -> R.string.chw_referral_status_completed
  ReferralStatus.UNABLE_TO_CONTACT -> R.string.chw_referral_status_unable_to_contact
  ReferralStatus.CANCELLED -> R.string.chw_referral_status_cancelled
  ReferralStatus.CLOSED_UNRESOLVED -> R.string.chw_referral_status_closed_unresolved
  ReferralStatus.UNKNOWN -> R.string.chw_referral_status_unknown
})

@Composable
fun contactOutcomeLabel(o: ContactOutcome): String = stringResource(when (o) {
  ContactOutcome.REACHED -> R.string.chw_outcome_reached
  ContactOutcome.NOT_AT_HOME -> R.string.chw_outcome_not_at_home
  ContactOutcome.PHONE_UNREACHABLE -> R.string.chw_outcome_phone_unreachable
  ContactOutcome.DECLINED -> R.string.chw_outcome_declined
  ContactOutcome.TRANSFERRED_OUT -> R.string.chw_outcome_transferred
  ContactOutcome.MOVED_AWAY -> R.string.chw_outcome_moved
  ContactOutcome.UNKNOWN -> R.string.chw_outcome_unknown
})

@Composable
fun medicineLabel(m: ReportedMedicineAvailability): String = stringResource(when (m) {
  ReportedMedicineAvailability.HAS_MEDICINE_TAKING_AS_ADVISED -> R.string.chw_medicine_has_taking
  ReportedMedicineAvailability.HAS_MEDICINE_NOT_TAKING -> R.string.chw_medicine_has_not_taking
  ReportedMedicineAvailability.RAN_OUT -> R.string.chw_medicine_ran_out
  ReportedMedicineAvailability.COULD_NOT_GET_AT_CLINIC_OR_PHARMACY -> R.string.chw_medicine_could_not_get
  ReportedMedicineAvailability.NOT_ON_MEDICINE -> R.string.chw_medicine_not_on
  ReportedMedicineAvailability.NOT_ASKED, ReportedMedicineAvailability.UNKNOWN -> R.string.chw_medicine_not_asked
})
