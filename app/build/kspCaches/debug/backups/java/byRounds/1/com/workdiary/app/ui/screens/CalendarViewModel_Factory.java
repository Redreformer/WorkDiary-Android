package com.workdiary.app.ui.screens;

import android.app.Application;
import com.workdiary.app.PreferencesRepository;
import com.workdiary.app.utils.PDFManager;
import com.workdiary.app.utils.PhotoManager;
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
public final class CalendarViewModel_Factory implements Factory<CalendarViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<PreferencesRepository> repoProvider;

  private final Provider<PhotoManager> photoManagerProvider;

  private final Provider<PDFManager> pdfManagerProvider;

  public CalendarViewModel_Factory(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider, Provider<PhotoManager> photoManagerProvider,
      Provider<PDFManager> pdfManagerProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
    this.photoManagerProvider = photoManagerProvider;
    this.pdfManagerProvider = pdfManagerProvider;
  }

  @Override
  public CalendarViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get(), photoManagerProvider.get(), pdfManagerProvider.get());
  }

  public static CalendarViewModel_Factory create(Provider<Application> appProvider,
      Provider<PreferencesRepository> repoProvider, Provider<PhotoManager> photoManagerProvider,
      Provider<PDFManager> pdfManagerProvider) {
    return new CalendarViewModel_Factory(appProvider, repoProvider, photoManagerProvider, pdfManagerProvider);
  }

  public static CalendarViewModel newInstance(Application app, PreferencesRepository repo,
      PhotoManager photoManager, PDFManager pdfManager) {
    return new CalendarViewModel(app, repo, photoManager, pdfManager);
  }
}
