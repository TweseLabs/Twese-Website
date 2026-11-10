package org.simple.clinic.chw.sync

import org.simple.clinic.sync.DataPushResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * SERVER-BLOCKED: these endpoints do not exist on simple-server today. They are specified in
 * docs/burundi/api-contract.md and docs/burundi/server-implementation.md. The calls are only made
 * when [org.simple.clinic.feature.Feature.BurundiChwServerSync] is enabled.
 */
interface ChwSyncApi {

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @POST("v4/burundi/screening_episodes/sync")
  fun pushScreenings(@Body request: ChwPushRequest<ScreeningEpisodePayload>): Call<DataPushResponse>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/screening_episodes/sync")
  fun pullScreenings(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<ScreeningEpisodePayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @POST("v4/burundi/referrals/sync")
  fun pushReferrals(@Body request: ChwPushRequest<ReferralPayload>): Call<DataPushResponse>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/referrals/sync")
  fun pullReferrals(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<ReferralPayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @POST("v4/burundi/chw_follow_up_tasks/sync")
  fun pushTasks(@Body request: ChwPushRequest<ChwFollowUpTaskPayload>): Call<DataPushResponse>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/chw_follow_up_tasks/sync")
  fun pullTasks(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<ChwFollowUpTaskPayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @POST("v4/burundi/chw_contacts/sync")
  fun pushContacts(@Body request: ChwPushRequest<ChwContactPayload>): Call<DataPushResponse>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/chw_contacts/sync")
  fun pullContacts(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<ChwContactPayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @POST("v4/burundi/patient_chw_assignments/sync")
  fun pushPatientAssignments(@Body request: ChwPushRequest<PatientChwAssignmentPayload>): Call<DataPushResponse>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/patient_chw_assignments/sync")
  fun pullPatientAssignments(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<PatientChwAssignmentPayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/community_areas/sync")
  fun pullAreas(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<CommunityAreaPayload>>

  @Headers("X-Burundi-Chw-Api-Version: $CHW_API_VERSION")
  @GET("v4/burundi/chw_assignments/sync")
  fun pullChwAssignments(@Query("limit") limit: Int, @Query("process_token") token: String?): Call<ChwPullResponse<ChwAssignmentPayload>>
}
