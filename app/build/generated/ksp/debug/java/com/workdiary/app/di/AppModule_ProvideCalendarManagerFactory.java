package com.workdiary.app.di;

import android.content.Context;
import com.workdiary.app.utils.CalendarManager;
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
public final class AppModule_ProvideCalendarManagerFactory implements Factory<CalendarManager> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideCalendarManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CalendarManager get() {
    return provideCalendarManager(contextProvider.get());
  }

  public static AppModule_ProvideCalendarManagerFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideCalendarManagerFactory(contextProvider);
  }

  public static CalendarManager provideCalendarManager(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCalendarManager(context));
  }
}
