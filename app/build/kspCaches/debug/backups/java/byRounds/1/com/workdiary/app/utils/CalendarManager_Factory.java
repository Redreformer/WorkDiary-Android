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
public final class CalendarManager_Factory implements Factory<CalendarManager> {
  private final Provider<Context> contextProvider;

  public CalendarManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CalendarManager get() {
    return newInstance(contextProvider.get());
  }

  public static CalendarManager_Factory create(Provider<Context> contextProvider) {
    return new CalendarManager_Factory(contextProvider);
  }

  public static CalendarManager newInstance(Context context) {
    return new CalendarManager(context);
  }
}
