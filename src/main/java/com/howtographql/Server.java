package com.howtographql;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.howtographql.Utils.toHandler;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

public class Server extends AbstractVerticle {

  private LinkRepository linkRepository;
  private UserRepository userRepository;
  private UserBatchLoader userBatchLoader;
  private VoteRepository voteRepository;
  private GraphQL graphQL;

  @Override
  public void start() {

    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    linkRepository = new LinkRepository(mongoClient);
    userRepository = new UserRepository(mongoClient);
    userBatchLoader = new UserBatchLoader(userRepository);
    voteRepository = new VoteRepository(mongoClient);

    String schema = vertx.fileSystem().readFileBlocking("schema.graphqls").toString();

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

    RuntimeWiring runtimeWiring = newRuntimeWiring()
      .scalar(Scalars.dateTime)
      .type("Query", builder -> builder.dataFetcher("allLinks", this::getAllLinks))
      .type("Mutation", builder -> {
        return builder.dataFetcher("createLink", this::createLink)
          .dataFetcher("createUser", this::createUser)
          .dataFetcher("signinUser", this::signinUser)
          .dataFetcher("createVote", this::createVote);
      })
      .type("Link", builder -> builder.dataFetcher("postedBy", this::getLinkPostedBy))
      .type("Vote", builder -> {
        return builder.dataFetcher("user", this::getVoteOwner)
          .dataFetcher("link", this::getVoteSubject);
      })
      .build();

    SchemaGenerator schemaGenerator = new SchemaGenerator();
    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

    DataLoaderDispatcherInstrumentationOptions options = DataLoaderDispatcherInstrumentationOptions
      .newOptions().includeStatistics(true);

    DataLoaderDispatcherInstrumentation dispatcherInstrumentation
      = new DataLoaderDispatcherInstrumentation(options);

    graphQL = GraphQL.newGraphQL(graphQLSchema)
      .instrumentation(dispatcherInstrumentation)
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

  private CompletableFuture<User> getVoteOwner(DataFetchingEnvironment env) {
    CompletableFuture<User> cf = new CompletableFuture<>();
    Vote vote = env.getSource();
    String userId = vote.getUserId();
    if (userId == null) {
      cf.complete(null);
    } else {
      userRepository.findById(userId, toHandler(cf));
    }
    return cf;
  }

  private CompletableFuture<Link> getVoteSubject(DataFetchingEnvironment env) {
    CompletableFuture<Link> cf = new CompletableFuture<>();
    Vote vote = env.getSource();
    String linkId = vote.getLinkId();
    if (linkId == null) {
      cf.complete(null);
    } else {
      linkRepository.findById(linkId, toHandler(cf));
    }
    return cf;
  }

  private CompletableFuture<Vote> createVote(DataFetchingEnvironment env) {
    CompletableFuture<Vote> cf = new CompletableFuture<>();
    ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
    Vote vote = new Vote(now, env.getArgument("userId"), env.getArgument("linkId"));
    voteRepository.saveVote(vote, toHandler(cf));
    return cf;
  }

  private CompletableFuture<User> getLinkPostedBy(DataFetchingEnvironment env) {
    Link link = env.getSource();
    String userId = link.getUserId();
    return userId == null ? CompletableFuture.completedFuture(null) : env.<String, User>getDataLoader("user").load(userId);
  }

  private CompletableFuture<SigninPayload> signinUser(DataFetchingEnvironment env) {
    CompletableFuture<SigninPayload> cf = new CompletableFuture<>();
    AuthData auth = new JsonObject((Map<String, Object>) env.getArgument("auth")).mapTo(AuthData.class);
    Future<User> future = Future.future();
    userRepository.findByEmail(auth.getEmail(), future);
    future.compose(user -> {
      if (user.getPassword().equals(auth.getPassword())) {
        return Future.succeededFuture(new SigninPayload(user.getId(), user));
      }
      return Future.failedFuture(new GraphQLException("Invalid credentials"));
    }).setHandler(toHandler(cf));
    return cf;
  }

  private CompletableFuture<Link> createLink(DataFetchingEnvironment env) {
    CompletableFuture<Link> cf = new CompletableFuture<>();
    RoutingContext rc = env.getContext();
    User user = rc.get("user");
    Link link = new Link(env.getArgument("url"), env.getArgument("description"), user == null ? null : user.getId());
    linkRepository.saveLink(link, toHandler(cf));
    return cf;
  }

  private CompletableFuture<User> createUser(DataFetchingEnvironment env) {
    CompletableFuture<User> cf = new CompletableFuture<>();
    AuthData auth = new JsonObject((Map<String, Object>) env.getArgument("authProvider")).mapTo(AuthData.class);
    User user = new User(env.getArgument("name"), auth.getEmail(), auth.getPassword());
    userRepository.saveUser(user, toHandler(cf));
    return cf;
  }

  private CompletableFuture<List<Link>> getAllLinks(DataFetchingEnvironment env) {
    CompletableFuture<List<Link>> cf = new CompletableFuture<>();
    Map<String, Object> arg = env.getArgument("filter");
    LinkFilter filter = arg == null ? null : new JsonObject(arg).mapTo(LinkFilter.class);
    linkRepository.getAllLinks(filter, env.getArgument("skip"), env.getArgument("first"), toHandler(cf));
    return cf;
  }

  private void handleGraphQL(RoutingContext rc) {
    String authorization = rc.request().getHeader("Authorization");
    String token = authorization == null ? null : authorization.replace("Bearer ", "");

    Future<User> future = Future.future();
    if (token == null) {
      future.complete();
    } else {
      userRepository.findById(token, future);
    }

    future.setHandler(ar -> {
      if (ar.succeeded()) {

        rc.put("user", ar.result());

        ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
          .context(rc);

        JsonObject body = new JsonObject(rc.getBody());
        String query = body.getString("query");
        builder.query(query);

        JsonObject variables = body.getJsonObject("variables");
        if (variables != null) {
          builder.variables(variables.getMap());
        }

        DataLoader<String, User> characterDataLoader = DataLoader.newDataLoader(userBatchLoader);
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("user", characterDataLoader);
        builder.dataLoaderRegistry(registry);

        graphQL.executeAsync(builder.build())
          .whenComplete((executionResult, throwable) -> {
            if (throwable == null) {
              rc.response().end(new JsonObject(executionResult.toSpecification()).toBuffer());
            } else {
              rc.fail(throwable);
            }
          });
      } else {
        rc.fail(ar.cause());
      }
    });
  }
}
