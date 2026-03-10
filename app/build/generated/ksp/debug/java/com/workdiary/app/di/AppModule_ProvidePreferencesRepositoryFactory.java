package com.workdiary.app.di;

import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.workdiary.app.PreferencesRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_ProvidePreferencesRepositoryFactory implements Factory<PreferencesRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public AppModule_ProvidePreferencesRepositoryFactory(Provider<Context> contextProvider,
      Provider<DataStore<Preferences>> dataStoreProvider) {
    this.contextProvider = contextProvider;
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public PreferencesRepository get() {
    return providePreferencesRepository(contextProvider.get(), dataStoreProvider.get());
  }

  public static AppModule_ProvidePreferencesRepositoryFactory create(
      Provider<Context> contextProvider, Provider<DataStore<Preferences>> dataStoreProvider) {
    return new AppModule_ProvidePreferencesRepositoryFactory(contextProvider, dataStoreProvider);
  }

  public static PreferencesRepository providePreferencesRepository(Context context,
      DataStore<Preferences> dataStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePreferencesRepository(context, dataStore));
  }
}
