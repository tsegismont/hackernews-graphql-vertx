package com.howtographql;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

public class Server extends AbstractVerticle {

  private LinkRepository linkRepository;
  private GraphQL graphQL;

  @Override
  public void start() {

    linkRepository = new LinkRepository();

    String schema = vertx.fileSystem().readFileBlocking("schema.graphqls").toString();

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

    RuntimeWiring runtimeWiring = newRuntimeWiring()
      .type("Query", builder -> builder.dataFetcher("allLinks", this::getAllLinks))
      .build();

    SchemaGenerator schemaGenerator = new SchemaGenerator();
    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

    graphQL = GraphQL.newGraphQL(graphQLSchema)
      .build();

    Router router = Router.router(vertx);
    router.route("/graphql").handler(BodyHandler.create());
    router.route("/graphql").handler(this::handleGraphQL);
    router.route().handler(StaticHandler.create());

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888, ar -> {
        if (ar.succeeded()) {
          System.out.println("Ready");
        } else {
          ar.cause().printStackTrace();
        }
      });
  }

  private CompletableFuture<List<Link>> getAllLinks(DataFetchingEnvironment env) {
    CompletableFuture<List<Link>> cf = new CompletableFuture<>();
    cf.complete(linkRepository.getAllLinks());
    return cf;
  }

  private void handleGraphQL(RoutingContext rc) {
    ExecutionInput.Builder builder = ExecutionInput.newExecutionInput();

    JsonObject body = new JsonObject(rc.getBody());
    String query = body.getString("query");
    builder.query(query);

    JsonObject variables = body.getJsonObject("variables");
    if (variables != null) {
      builder.variables(variables.getMap());
    }

    graphQL.executeAsync(builder.build())
      .whenComplete((executionResult, throwable) -> {
        if (throwable == null) {
          rc.response().end(new JsonObject(executionResult.toSpecification()).toBuffer());
        } else {
          rc.fail(throwable);
        }
      });
  }
}
