package org.example.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.ServerWebSocket;

import static org.example.verticle.util.Addresses.ROUTER;

public class WsServerVerticle extends AbstractVerticle {
    @Override
    public void start() {
        vertx.createHttpServer()
                .websocketHandler(this::createWebSocketServer)
                .listen(8080);
    }

    private void createWebSocketServer(ServerWebSocket wsServer) {
        System.out.println("Create WebSocket: " + wsServer.path());
        wsServer.frameHandler(wsFrame -> {
            System.out.println(wsFrame.textData());
            vertx.eventBus().send(ROUTER, wsFrame.textData());
        });

        MessageConsumer<String> consumerSendMessage = vertx.eventBus().consumer(wsServer.path(), data -> {
            wsServer.writeFinalTextFrame(data.body());
            data.reply("ok");
        });

        wsServer.closeHandler(aVoid -> {
            System.out.println("Close WebSocket: " + consumerSendMessage.address());
            consumerSendMessage.unregister();
        });
    }
}
