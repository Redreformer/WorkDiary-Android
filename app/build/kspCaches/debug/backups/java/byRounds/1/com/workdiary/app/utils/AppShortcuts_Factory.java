package com.workdiary.app.utils;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AppShortcuts_Factory implements Factory<AppShortcuts> {
  private final Provider<Context> contextProvider;

  public AppShortcuts_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AppShortcuts get() {
    return newInstance(contextProvider.get());
  }

  public static AppShortcuts_Factory create(Provider<Context> contextProvider) {
    return new AppShortcuts_Factory(contextProvider);
  }

  public static AppShortcuts newInstance(Context context) {
    return new AppShortcuts(context);
  }
}
