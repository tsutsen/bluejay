package com.tsutsen.platformplayer.feature.dualscreen;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ScreenCoordinator_Factory implements Factory<ScreenCoordinator> {
  private final Provider<Context> contextProvider;

  private ScreenCoordinator_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ScreenCoordinator get() {
    return newInstance(contextProvider.get());
  }

  public static ScreenCoordinator_Factory create(Provider<Context> contextProvider) {
    return new ScreenCoordinator_Factory(contextProvider);
  }

  public static ScreenCoordinator newInstance(Context context) {
    return new ScreenCoordinator(context);
  }
}
