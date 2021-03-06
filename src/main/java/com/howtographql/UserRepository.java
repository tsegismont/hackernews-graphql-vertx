package com.howtographql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class UserRepository {

  private final MongoClient mongoClient;

  public UserRepository(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public void findByEmail(String email, Handler<AsyncResult<User>> handler) {
    JsonObject query = new JsonObject().put("email", email);
    Future<JsonObject> future = Future.future();
    mongoClient.findOne("users", query, null, future);
    future.map(json -> user(json)).setHandler(handler);
  }

  public void findById(String id, Handler<AsyncResult<User>> handler) {
    JsonObject query = new JsonObject().put("_id", id);
    Future<JsonObject> future = Future.future();
    mongoClient.findOne("users", query, null, future);
    future.map(json -> user(json)).setHandler(handler);
  }

  public void saveUser(User user, Handler<AsyncResult<User>> handler) {
    JsonObject doc = new JsonObject()
      .put("name", user.getName())
      .put("email", user.getEmail())
      .put("password", user.getPassword());
    Future<String> future = Future.future();
    mongoClient.insert("users", doc, future);
    future.map(id -> user(doc.put("_id", id))).setHandler(handler);
  }

  private User user(JsonObject doc) {
    if (doc == null) {
      return null;
    }
    return new User(
      doc.getString("_id"),
      doc.getString("name"),
      doc.getString("email"),
      doc.getString("password"));
  }

  public void findByIds(List<String> keys, Handler<AsyncResult<List<User>>> handler) {
    JsonObject query = new JsonObject()
      .put("_id", new JsonObject().put("$in", new JsonArray(keys)));
    Future<List<JsonObject>> future = Future.future();
    mongoClient.find("users", query, future);
    future.map(docs -> docs.stream().map(doc -> user(doc)).collect(toList())).setHandler(handler);
  }
}