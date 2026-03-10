package com.workdiary.app.di;

import android.content.Context;
import com.workdiary.app.utils.NotificationScheduler;
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
public final class AppModule_ProvideNotificationSchedulerFactory implements Factory<NotificationScheduler> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideNotificationSchedulerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NotificationScheduler get() {
    return provideNotificationScheduler(contextProvider.get());
  }

  public static AppModule_ProvideNotificationSchedulerFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvideNotificationSchedulerFactory(contextProvider);
  }

  public static NotificationScheduler provideNotificationScheduler(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNotificationScheduler(context));
  }
}
