package com.howtographql;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class LinkRepository {

  private final MongoClient mongoClient;

  public LinkRepository(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public void findById(String id, Handler<AsyncResult<Link>> handler) {
    JsonObject query = new JsonObject().put("_id", id);
    Future<JsonObject> future = Future.future();
    mongoClient.findOne("links", query, null, future);
    future.map(json -> link(json)).setHandler(handler);
  }

  public void getAllLinks(LinkFilter filter, Handler<AsyncResult<List<Link>>> handler) {
    Future<List<JsonObject>> future = Future.future();
    mongoClient.find("links", buildFilter(filter), future);
    future.map(list -> list.stream().map(this::link).collect(toList())).setHandler(handler);
  }

  private JsonObject buildFilter(LinkFilter filter) {
    JsonObject query = new JsonObject();
    if (filter == null) {
      return query;
    }
    String descriptionPattern = filter.getDescriptionContains();
    String urlPattern = filter.getUrlContains();
    if (descriptionPattern != null && !descriptionPattern.isEmpty()) {
      query.put("description", new JsonObject().put("$regex", ".*" + descriptionPattern + ".*"));
    }
    if (urlPattern != null && !urlPattern.isEmpty()) {
      query.put("url", new JsonObject().put("$regex", ".*" + urlPattern + ".*"));
    }
    return query;
  }

  public void saveLink(Link link, Handler<AsyncResult<Link>> handler) {
    Future<String> future = Future.future();
    JsonObject doc = new JsonObject().put("url", link.getUrl()).put("description", link.getDescription()).put("postedBy", link.getUserId());
    mongoClient.insert("links", doc, future);
    future.map(id -> link(doc.put("_id", id))).setHandler(handler);
  }

  private Link link(JsonObject doc) {
    return new Link(
      doc.getString("_id"),
      doc.getString("url"),
      doc.getString("description"),
      doc.getString("postedBy"));
  }
}
