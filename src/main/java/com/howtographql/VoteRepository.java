package com.howtographql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class VoteRepository {

  private final MongoClient mongoClient;

  public VoteRepository(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public void findByUserId(String userId, Handler<AsyncResult<List<Vote>>> handler) {
    Future<List<JsonObject>> future = Future.future();
    mongoClient.find("votes", new JsonObject().put("userId", userId), future);
    future.map(list -> list.stream().map(this::vote).collect(toList())).setHandler(handler);
  }

  public void findByLinkId(String linkId, Handler<AsyncResult<List<Vote>>> handler) {
    Future<List<JsonObject>> future = Future.future();
    mongoClient.find("votes", new JsonObject().put("linkId", linkId), future);
    future.map(list -> list.stream().map(this::vote).collect(toList())).setHandler(handler);
  }

  public void saveVote(Vote vote, Handler<AsyncResult<Vote>> handler) {
    Future<String> future = Future.future();
    JsonObject doc = new JsonObject()
      .put("userId", vote.getUserId())
      .put("linkId", vote.getLinkId())
      .put("createdAt", Scalars.dateTime.getCoercing().serialize(vote.getCreatedAt()));
    mongoClient.insert("votes", doc, future);
    future.map(id -> vote(doc.put("_id", id))).setHandler(handler);
  }

  private Vote vote(JsonObject doc) {
    return new Vote(
      doc.getString("_id"),
      ZonedDateTime.parse(doc.getString("createdAt")),
      doc.getString("userId"),
      doc.getString("linkId")
    );
  }
}
