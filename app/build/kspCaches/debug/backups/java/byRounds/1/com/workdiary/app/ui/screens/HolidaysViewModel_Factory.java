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
public final class HolidaysViewModel_Factory implements Factory<HolidaysViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<PreferencesRepository> repoProvider;

  public HolidaysViewModel_Factory(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
  }

  @Override
  public HolidaysViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get());
  }

  public static HolidaysViewModel_Factory create(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider) {
    return new HolidaysViewModel_Factory(appProvider, repoProvider);
  }

  public static HolidaysViewModel newInstance(Application app, PreferencesRepository repo) {
    return new HolidaysViewModel(app, repo);
  }
}
