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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<PreferencesRepository> repoProvider;

  public DashboardViewModel_Factory(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    return new DashboardViewModel_Factory(appProvider, repoProvider);
  }

  public static DashboardViewModel newInstance(Application app, PreferencesRepository repo) {
    return new DashboardViewModel(app, repo);
  }
}
