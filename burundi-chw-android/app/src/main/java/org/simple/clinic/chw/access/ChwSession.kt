package org.simple.clinic.chw.access

import android.content.SharedPreferences
import org.simple.clinic.BuildConfig
import org.simple.clinic.storage.SharedPreferencesMode
import org.simple.clinic.storage.SharedPreferencesMode.Mode.Encrypted
import javax.inject.Inject

/**
 * Source of the programme role for the logged-in user.
 *
 * Server-blocked: the role must come from the server's user/session payload (see
 * docs/burundi/api-contract.md §user role). Until that exists, the stored value is empty and the
 * user is treated as [ChwRole.LEGACY_CLINIC_USER] (unchanged upstream behaviour).
 *
 * In debug builds only, a *demo* role may be set locally to exercise CHW screens with synthetic
 * data. Release builds ignore the demo override entirely.
 */
class ChwSession @Inject constructor(
    @SharedPreferencesMode(Encrypted) private val sharedPreferences: SharedPreferences
) {

  fun role(): ChwRole {
    val serverRole = sharedPreferences.getString(KEY_SERVER_ROLE, null)
    if (serverRole != null) return ChwRole.fromCode(serverRole)

    if (isDemoRoleAllowed) {
      val demoRole = sharedPreferences.getString(KEY_DEMO_ROLE, null)
      if (demoRole != null) return ChwRole.fromCode(demoRole)
    }
    return ChwRole.LEGACY_CLINIC_USER
  }

  fun isRoleFromServer(): Boolean = sharedPreferences.getString(KEY_SERVER_ROLE, null) != null

  fun isUsingDemoRole(): Boolean =
      !isRoleFromServer() && isDemoRoleAllowed && sharedPreferences.getString(KEY_DEMO_ROLE, null) != null

  fun can(permission: ChwPermission): Boolean = ChwAccessPolicy.isAllowed(role(), permission)

  /** Debug builds only. Returns false (and does nothing) on release builds. */
  fun setDemoRole(role: ChwRole?): Boolean {
    if (!isDemoRoleAllowed) return false
    sharedPreferences.edit().apply {
      if (role == null) remove(KEY_DEMO_ROLE) else putString(KEY_DEMO_ROLE, role.code)
    }.apply()
    return true
  }

  /** To be called by user refresh once the server sends the role (server-blocked). */
  fun setServerRole(roleCode: String?) {
    sharedPreferences.edit().apply {
      if (roleCode == null) remove(KEY_SERVER_ROLE) else putString(KEY_SERVER_ROLE, roleCode)
    }.apply()
  }

  val isDemoRoleAllowed: Boolean
    get() = BuildConfig.DEBUG

  companion object {
    const val KEY_SERVER_ROLE = "burundi_programme_role_from_server"
    const val KEY_DEMO_ROLE = "burundi_programme_demo_role_debug_only"
  }
}
