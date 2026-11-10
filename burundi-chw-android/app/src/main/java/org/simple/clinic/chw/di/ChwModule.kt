package org.simple.clinic.chw.di

import dagger.Module
import dagger.Provides
import org.simple.clinic.chw.sync.ChwSyncApi
import retrofit2.Retrofit
import javax.inject.Named

@Module
class ChwModule {

  @Provides
  fun provideChwSyncApi(@Named("for_deployment") retrofit: Retrofit): ChwSyncApi {
    return retrofit.create(ChwSyncApi::class.java)
  }
}
