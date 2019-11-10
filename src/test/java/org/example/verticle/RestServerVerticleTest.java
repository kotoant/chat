package org.example.verticle;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_GET_ALL_IDS;
import static org.example.verticle.util.Addresses.DATABASE_IMAGE_SAVE;
import static org.example.verticle.util.ReplyMessages.success;

@ExtendWith(VertxExtension.class)
public class RestServerVerticleTest {

    private JsonObject testImage;

    // Deploy the verticle and execute the test methods when the verticle is successfully deployed
    @BeforeEach
    void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        testImage = createTestImage();
        vertx.deployVerticle(new RestServerVerticle(), testContext.completing());
        vertx.eventBus().consumer(DATABASE_IMAGE_SAVE, this::saveImage);
        vertx.eventBus().consumer(DATABASE_IMAGE_GET, this::getImageById);
        vertx.eventBus().consumer(DATABASE_IMAGE_GET_ALL_IDS, this::getAllImageIds);
    }

    private JsonObject createTestImage() {
        return new JsonObject()
                .put("uploadedFileName", getPath("test-image.jpg"))
                .put("fileName", "image.jpg")
                .put("size", 19656)
                .put("contentType", "image/jpeg");
    }

    private void saveImage(Message<JsonObject> message) {
        final JsonObject image = message.body();
        image.put("_id", "100500");
        message.reply(success(image));
    }

    private void getImageById(Message<String> message) {
        final String messageId = message.body();
        message.reply(success("100500".equals(messageId) ? testImage : null));
    }

    private void getAllImageIds(Message<Void> message) {
        message.reply(success(new JsonObject().put("images", Collections.singletonList(testImage))));
    }

    @Test
    void upload_image(Vertx vertx, VertxTestContext testContext) {
        MultipartForm form = MultipartForm.create()
                .attribute("imageDescription", "a very nice image")
                .binaryFileUpload("imageFile", "image.jpg", getPath("test-image.jpg"), "image/jpeg");

        WebClient client = WebClient.create(vertx);
        client.post(8081, "localhost", "/images")
                .sendMultipartForm(form, testContext.succeeding(response -> testContext.verify(() -> {
                    final JsonObject result = response.bodyAsJsonObject().getJsonObject("result");
                    assertThat(result).isNotNull();
                    assertThat(result.getString("fileName")).isEqualTo("image.jpg");
                    assertThat(result.getLong("size")).isEqualTo(19656);
                    assertThat(result.getString("contentType")).isEqualTo("image/jpeg");
                    testContext.completeNow();
                })));
    }

    @Test
    void upload_multiple_image(Vertx vertx, VertxTestContext testContext) {
        MultipartForm form = MultipartForm.create()
                .attribute("imageDescription", "a very nice image")
                .binaryFileUpload("imageFile", "image.jpg", getPath("test-image.jpg"), "image/jpeg")
                .binaryFileUpload("imageFile2", "image2.jpg", getPath("test-image.jpg"), "image/jpeg");

        WebClient client = WebClient.create(vertx);
        client.post(8081, "localhost", "/images")
                .sendMultipartForm(form, testContext.succeeding(response -> testContext.verify(() -> {
                    final String error = response.bodyAsJsonObject().getString("error");
                    assertThat(error).isEqualTo("Multiple image upload is not supported");
                    testContext.completeNow();
                })));
    }

    @Test
    void get_image(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8081, "localhost", "/images/100500")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.getHeader("Content-Type")).isEqualTo("image/jpeg");
                    assertThat(response.getHeader("Content-Disposition")).startsWith("attachment; filename*=UTF-8''");
                    testContext.completeNow();
                })));
    }

    @Test
    void get_unknown_image(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8081, "localhost", "/images/100501")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    final String error = response.bodyAsJsonObject().getString("error");
                    assertThat(error).isEqualTo("Failed to find image by ID: 100501");
                    testContext.completeNow();
                })));
    }

    @Test
    void get_images(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8081, "localhost", "/images")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    final JsonObject result = response.bodyAsJsonObject().getJsonObject("result");
                    assertThat(result).isNotNull();
                    final JsonArray images = result.getJsonArray("images");
                    assertThat(images).hasSize(1);
                    final JsonObject image = images.getJsonObject(0);
                    assertThat(image.getString("uploadedFileName")).isEqualTo(getPath("test-image.jpg"));
                    assertThat(image.getString("fileName")).isEqualTo("image.jpg");
                    assertThat(image.getLong("size")).isEqualTo(19656);
                    assertThat(image.getString("contentType")).isEqualTo("image/jpeg");
                    testContext.completeNow();
                })));
    }

    private String getPath(String resourcesFile) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(resourcesFile).getFile());
        return file.getAbsolutePath();
    }
}
