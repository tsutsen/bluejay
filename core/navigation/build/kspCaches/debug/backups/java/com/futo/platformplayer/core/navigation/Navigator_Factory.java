package com.futo.platformplayer.core.navigation;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class Navigator_Factory implements Factory<Navigator> {
  @Override
  public Navigator get() {
    return newInstance();
  }

  public static Navigator_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Navigator newInstance() {
    return new Navigator();
  }

  private static final class InstanceHolder {
    static final Navigator_Factory INSTANCE = new Navigator_Factory();
  }
}
