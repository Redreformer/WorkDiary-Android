package com.workdiary.app;

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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<PreferencesRepository> preferencesRepositoryProvider;

  public MainViewModel_Factory(Provider<PreferencesRepository> preferencesRepositoryProvider) {
    this.preferencesRepositoryProvider = preferencesRepositoryProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(preferencesRepositoryProvider.get());
  }

  public static MainViewModel_Factory create(
      Provider<PreferencesRepository> preferencesRepositoryProvider) {
    return new MainViewModel_Factory(preferencesRepositoryProvider);
  }

  public static MainViewModel newInstance(PreferencesRepository preferencesRepository) {
    return new MainViewModel(preferencesRepository);
  }
}
