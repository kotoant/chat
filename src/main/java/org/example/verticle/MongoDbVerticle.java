package org.example.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET_ALL_IDS;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_SAVE;
import static org.example.verticle.util.Addresses.DATABASE_MESSAGE_SAVE;
import static org.example.verticle.util.Addresses.GET_HISTORY;
import static org.example.verticle.util.ReplyMessages.error;
import static org.example.verticle.util.ReplyMessages.success;

public class MongoDbVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MongoDbVerticle.class);

    private static final String MESSAGE_COLLECTION = "message";
    private static final String IMAGE_COLLECTION = "image";

    private MongoClient client;

    @Override
    public void start() {
        client = MongoClient.createShared(vertx, new JsonObject()
                .put("db_name", "my_DB"));
        vertx.eventBus().consumer(DATABASE_MESSAGE_SAVE, this::saveMessage);
        vertx.eventBus().consumer(GET_HISTORY, this::getHistory);
        vertx.eventBus().consumer(DATABASE_IMAGE_SAVE, this::saveImage);
        vertx.eventBus().consumer(DATABASE_IMAGE_GET, this::getImageById);
        vertx.eventBus().consumer(DATABASE_IMAGE_GET_ALL_IDS, this::getAllImageIds);
    }

    private void getHistory(Message<String> message) {
        client.find(MESSAGE_COLLECTION, new JsonObject(),
                result -> message.reply(Json.encode(result.result()))
        );
    }

    private void saveMessage(Message<String> message) {
        client.insert(MESSAGE_COLLECTION, new JsonObject(message.body()), asyncResult -> {
            if (asyncResult.succeeded()) {
                log.info("Message has been successfully saved: {}", asyncResult.result());
            } else {
                log.error("Failed to save message", asyncResult.cause());
            }
        });
    }

    private void saveImage(Message<JsonObject> message) {
        final JsonObject image = message.body();
        client.insert(IMAGE_COLLECTION, image, asyncResult -> message.reply(asyncResult.failed() ?
                error(asyncResult.cause()) :
                success(image)
        ));
    }

    private void getImageById(Message<String> message) {
        final String messageId = message.body();
        final JsonObject query = new JsonObject().put("_id", messageId);
        client.find(IMAGE_COLLECTION, query, asyncResult -> message.reply(asyncResult.succeeded() ?
                success(asyncResult.result().stream().findAny().orElse(null)) :
                error(asyncResult.cause())
        ));
    }

    private void getAllImageIds(Message<Void> message) {
        client.find(IMAGE_COLLECTION, new JsonObject(), asyncResult -> message.reply(asyncResult.succeeded() ?
                success(new JsonObject().put("images", asyncResult.result())) :
                error(asyncResult.cause())
        ));
    }
}
