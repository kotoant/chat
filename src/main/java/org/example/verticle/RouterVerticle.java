package org.example.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import org.example.data.Data;

import static org.example.verticle.util.Addresses.DATABASE_MESSAGE_SAVE;
import static org.example.verticle.util.Addresses.ROUTER;

public class RouterVerticle extends AbstractVerticle {
    @Override
    public void start() {
        vertx.eventBus().consumer(ROUTER, this::router);
    }

    private void router(Message<String> message) {
        if (message.body() != null && !message.body().isEmpty()) {
            System.out.println("Router message: " + message.body());
            Data data = Json.decodeValue(message.body(), Data.class);
            System.out.println(data);
            vertx.eventBus().send("/token/" + data.getAddress(), message.body());

            // Save message in database
            vertx.eventBus().send(DATABASE_MESSAGE_SAVE, message.body());
        }
    }
}
