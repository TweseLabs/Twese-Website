package org.simple.clinic.home

import org.simple.clinic.chw.home.ChwHomeScreen
import androidx.annotation.StringRes
import org.simple.clinic.R
import org.simple.clinic.home.overdue.OverdueScreen
import org.simple.clinic.home.patients.PatientsTabScreen
import org.simple.clinic.home.report.ReportsScreen
import org.simple.clinic.navigation.v2.ScreenKey

enum class HomeTab(
    val key: ScreenKey,
    @StringRes val title: Int
) {

  PATIENTS(PatientsTabScreen.Key(), R.string.tab_patient),

  OVERDUE(OverdueScreen.Key(), R.string.tab_overdue),

  REPORTS(ReportsScreen.Key(), R.string.tab_progress),

  // Burundi CHW work queue. Declared last so upstream ordinals are unchanged.
  CHW(ChwHomeScreen.Key(), R.string.chw_tab_title)
}
