package org.example.verticle.util;

import io.vertx.core.json.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class ReplyMessages {

    private static final String RESULT = "result";
    private static final String ERROR = "error";

    public static JsonObject success(JsonObject result) {
        return new JsonObject().put(RESULT, result);
    }

    public static JsonObject error(String message) {
        return new JsonObject().put(ERROR, message);
    }

    public static JsonObject error(Throwable cause) {
        final Writer message = new StringWriter();
        cause.printStackTrace(new PrintWriter(message, true));
        return error(message.toString());
    }

    public static boolean isError(JsonObject replyMessage) {
        return replyMessage.containsKey(ERROR);
    }

    public static JsonObject getResult(JsonObject replyMessage) {
        return replyMessage.getJsonObject(RESULT);
    }

    private ReplyMessages() {
    }
}
