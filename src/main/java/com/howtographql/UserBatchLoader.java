package com.howtographql;

import org.dataloader.BatchLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class UserBatchLoader implements BatchLoader<String, User> {

  private final UserRepository userRepository;

  public UserBatchLoader(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public CompletionStage<List<User>> load(List<String> keys) {
    CompletableFuture<List<User>> future = new CompletableFuture<>();
    userRepository.findByIds(keys, Utils.toHandler(future));
    return future;
  }
}
