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
public final class PhotoManager_Factory implements Factory<PhotoManager> {
  private final Provider<Context> contextProvider;

  public PhotoManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PhotoManager get() {
    return newInstance(contextProvider.get());
  }

  public static PhotoManager_Factory create(Provider<Context> contextProvider) {
    return new PhotoManager_Factory(contextProvider);
  }

  public static PhotoManager newInstance(Context context) {
    return new PhotoManager(context);
  }
}
