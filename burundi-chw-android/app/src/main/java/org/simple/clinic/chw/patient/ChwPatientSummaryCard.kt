package org.simple.clinic.chw.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.simple.clinic.R
import org.simple.clinic.chw.ui.ChwSectionCard
import org.simple.clinic.common.ui.components.OutlinedButton

/** Entry point from the upstream patient summary into the CHW patient screen. */
@Composable
fun ChwPatientSummaryCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
  ChwSectionCard(stringResource(R.string.chw_patient_summary_card_title), modifier.testTag("chw_patient_summary_card")) {
    Column {
      Text(stringResource(R.string.chw_patient_summary_card_body), style = MaterialTheme.typography.body2)
      OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.chw_open))
      }
    }
  }
}
