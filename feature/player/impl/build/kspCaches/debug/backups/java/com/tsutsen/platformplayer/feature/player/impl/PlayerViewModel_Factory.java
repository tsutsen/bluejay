package com.tsutsen.platformplayer.feature.player.impl;

import com.tsutsen.platformplayer.core.data.repository.PlayerRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<PlayerRepository> playerRepositoryProvider;

  private PlayerViewModel_Factory(Provider<PlayerRepository> playerRepositoryProvider) {
    this.playerRepositoryProvider = playerRepositoryProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(playerRepositoryProvider.get());
  }

  public static PlayerViewModel_Factory create(
      Provider<PlayerRepository> playerRepositoryProvider) {
    return new PlayerViewModel_Factory(playerRepositoryProvider);
  }

  public static PlayerViewModel newInstance(PlayerRepository playerRepository) {
    return new PlayerViewModel(playerRepository);
  }
}
