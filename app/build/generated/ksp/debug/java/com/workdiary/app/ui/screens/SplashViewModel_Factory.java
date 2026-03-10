package com.workdiary.app.ui.screens;

import com.workdiary.app.PreferencesRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SplashViewModel_Factory implements Factory<SplashViewModel> {
  private final Provider<PreferencesRepository> preferencesRepositoryProvider;

  public SplashViewModel_Factory(Provider<PreferencesRepository> preferencesRepositoryProvider) {
    this.preferencesRepositoryProvider = preferencesRepositoryProvider;
  }

  @Override
  public SplashViewModel get() {
    return newInstance(preferencesRepositoryProvider.get());
  }

  public static SplashViewModel_Factory create(
      Provider<PreferencesRepository> preferencesRepositoryProvider) {
    return new SplashViewModel_Factory(preferencesRepositoryProvider);
  }

  public static SplashViewModel newInstance(PreferencesRepository preferencesRepository) {
    return new SplashViewModel(preferencesRepository);
  }
}
