package com.workdiary.app.ui.screens;

import android.app.Application;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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

  private final Provider<DataStore<Preferences>> storeProvider;

  public SettingsViewModel_Factory(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider,
      Provider<DataStore<Preferences>> storeProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
    this.storeProvider = storeProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get(), storeProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider,
      Provider<DataStore<Preferences>> storeProvider) {
    return new SettingsViewModel_Factory(appProvider, repoProvider, storeProvider);
  }

  public static SettingsViewModel newInstance(Application app, PreferencesRepository repo,
      DataStore<Preferences> store) {
    return new SettingsViewModel(app, repo, store);
  }
}
