package com.workdiary.app.ui.screens;

import android.app.Application;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<PreferencesRepository> repoProvider;

  public SettingsViewModel_Factory(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    return new SettingsViewModel_Factory(appProvider, repoProvider);
  }

  public static SettingsViewModel newInstance(Application app, PreferencesRepository repo) {
    return new SettingsViewModel(app, repo);
  }
}
