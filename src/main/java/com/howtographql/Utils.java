package com.howtographql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import java.util.concurrent.CompletableFuture;

public class Utils {

  public static <T> Handler<AsyncResult<T>> toHandler(CompletableFuture<T> cf) {
    return ar -> {
      if (ar.succeeded()) {
        cf.complete(ar.result());
      } else {
        cf.completeExceptionally(ar.cause());
      }
    };
  }
}
