package org.example.verticle;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET_ALL_IDS;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_SAVE;
import static org.example.verticle.util.Addresses.GET_HISTORY;
import static org.example.verticle.util.Addresses.ROUTER;
import static org.example.verticle.util.ReplyMessages.error;
import static org.example.verticle.util.ReplyMessages.getResult;
import static org.example.verticle.util.ReplyMessages.isError;

public class RestServerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(RestServerVerticle.class);

    @Override
    public void start() {
        HttpServer httpServer = vertx.createHttpServer();
        Router httpRouter = Router.router(vertx);
        httpRouter.route().handler(BodyHandler.create());
        httpRouter.post("/sendMessage")
                .handler(request -> {
                    vertx.eventBus().send(ROUTER, request.getBodyAsString());
                    request.response().end("ok");
                });
        httpRouter.get("/getHistory")
                .handler(request ->
                        vertx.eventBus().send(GET_HISTORY, request.getBodyAsString(), result ->
                                request.response().end(result.result().body().toString())
                        )
                );
        httpRouter.post("/images").handler(this::uploadImage);
        httpRouter.get("/images").handler(this::getAllImageIds);
        httpRouter.get("/images/:id").handler(this::getImage);
        httpServer.requestHandler(httpRouter::accept);
        httpServer.listen(8081);
    }

    private void getImage(RoutingContext context) {
        final String imageId = context.request().getParam("id");
        if (imageId == null) {
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .putHeader("Content-Type", "application/json")
                    .end(error("Missing required parameter: id").encodePrettily());
            return;
        }

        vertx.eventBus().send(DATABASE_IMAGE_GET, imageId, (AsyncResult<Message<JsonObject>> asyncResult) -> {
            final JsonObject result = asyncResult.result().body();

            if (isError(result)) {
                context.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .putHeader("Content-Type", "application/json")
                        .end(result.encodePrettily());
                return;
            }

            final JsonObject imageFile = getResult(result);

            if (imageFile == null) {
                context.response()
                        .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                        .putHeader("Content-Type", "application/json")
                        .end(error("Failed to find image by ID: " + imageId).encodePrettily());
                return;
            }

            vertx.fileSystem().open(imageFile.getString("uploadedFileName"), new OpenOptions(), asyncRead -> {
                if (asyncRead.failed()) {
                    context.response()
                            .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                            .putHeader("Content-Type", "application/json")
                            .end(error(asyncRead.cause()).encodePrettily());
                    return;
                }

                final String disposition = "inline; filename*=UTF-8''" + encode(imageFile.getString("fileName"));

                final HttpServerResponse response = context.response()
                        .putHeader("Content-Type", imageFile.getString("contentType"))
                        .putHeader("Content-Disposition", disposition);

                response.setChunked(true);

                final AsyncFile asyncFile = asyncRead.result();
                asyncFile.endHandler(ignored -> {
                    asyncFile.close();
                    response.end();
                });

                Pump.pump(asyncFile, response).start();
            });
        });

    }

    private static String encode(String string) {
        try {
            return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void getAllImageIds(RoutingContext context) {
        vertx.eventBus().send(DATABASE_IMAGE_GET_ALL_IDS, null,
                (AsyncResult<Message<JsonObject>> asyncResult) -> {
                    final JsonObject result = asyncResult.result().body();
                    context.response()
                            .setStatusCode(isError(result) ?
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR.code() :
                                    HttpResponseStatus.OK.code())
                            .putHeader("Content-Type", "application/json")
                            .end(result.encodePrettily());
                });
    }

    private void uploadImage(RoutingContext context) {
        final Set<FileUpload> fileUploads = context.fileUploads();
        if (fileUploads.size() != 1) {
            deleteFileUploads(context);
            context.response()
                    .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                    .putHeader("Content-Type", "application/json")
                    .end(error("Multiple image upload is not supported").encodePrettily());
            return;
        }

        final FileUpload fileUpload = fileUploads.iterator().next();
        final String contentType = fileUpload.contentType();
        if (!contentType.startsWith("image/")) {
            deleteFileUploads(context);
            context.response().setStatusCode(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                    .putHeader("Content-Type", "application/json")
                    .end(error("Expected Content-Type: image, actual: " + contentType).encodePrettily());
            return;
        }

        final JsonObject imageMessage = new JsonObject()
                .put("uploadedFileName", fileUpload.uploadedFileName())
                .put("fileName", fileUpload.fileName())
                .put("size", fileUpload.size())
                .put("contentType", fileUpload.contentType());

        vertx.eventBus().send(DATABASE_IMAGE_SAVE, imageMessage, (AsyncResult<Message<JsonObject>> async) -> {
            final JsonObject result = async.result().body();
            if (isError(result)) {
                deleteFileUploads(context);
                context.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            }
            context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(result.encodePrettily());
        });
    }

    private void deleteFileUploads(RoutingContext context) {
        for (FileUpload fileUpload : context.fileUploads()) {
            FileSystem fileSystem = context.vertx().fileSystem();
            String uploadedFileName = fileUpload.uploadedFileName();
            fileSystem.exists(uploadedFileName, existResult -> {
                if (existResult.failed()) {
                    log.warn("Could not detect if uploaded file exists, not deleting: " + uploadedFileName,
                            existResult.cause());
                } else if (existResult.result()) {
                    fileSystem.delete(uploadedFileName, deleteResult -> {
                        if (deleteResult.failed()) {
                            log.warn("Delete of uploaded file failed: " + uploadedFileName, deleteResult.cause());
                        }
                    });
                }
            });
        }
    }
}
