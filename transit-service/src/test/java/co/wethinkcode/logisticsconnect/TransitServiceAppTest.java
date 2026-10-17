package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.*;

import javax.jms.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TransitServiceAppTest {

    private static BrokerService broker;
    private static HttpServer mockHubService;
    private static Thread appThread;
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        // 1. Start in-memory ActiveMQ broker on tcp://localhost:61616
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(MqConfig.BROKER_URL);
        broker.start();
        broker.waitUntilStarted();

        // 2. Start Mock Hub Service on http://localhost:7051/hubs/
        mockHubService = HttpServer.create(new InetSocketAddress(7051), 0);
        mockHubService.createContext("/hubs/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String hubId = path.substring(path.lastIndexOf('/') + 1);

            String responseBody;
            int statusCode;

            if ("HUB-01".equals(hubId)) {
                statusCode = 200;
                responseBody = "{" +
                        "\"id\":\"HUB-01\"," +
                        "\"province\":\"Gauteng\"," +
                        "\"sortingCenter\":\"Johannesburg Central\"," +
                        "\"active\":true" +
                        "}";
            } else {
                statusCode = 404;
                responseBody = "{\"error\":\"Hub not found\"}";
            }

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        mockHubService.start();

        // 3. Start TransitServiceApp on a daemon thread
        appThread = new Thread(() -> TransitServiceApp.main(new String[]{}));
        appThread.setDaemon(true);
        appThread.start();

        // 4. Poll http://localhost:7053/health until Javalin is active
        long deadline = System.currentTimeMillis() + 8000;
        boolean ready = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest ping = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7053/health"))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(ping, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
                Thread.sleep(150);
            }
        }

        if (!ready) {
            throw new IllegalStateException("TransitServiceApp failed to start on port 7053 within timeout.");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (mockHubService != null) {
            mockHubService.stop(0);
        }
        if (broker != null) {
            broker.stop();
        }
        if (appThread != null) {
            appThread.interrupt();
        }
    }

    @Test
    @DisplayName("GET /health returns status 200 and 'OK'")
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7053/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    @DisplayName("GET /transit/{hubId} returns default stage 0 and ETA when no MQ message received")
    void testGetTransitDefaultStage() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7053/transit/HUB-01"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        JsonNode json = objectMapper.readTree(response.body());

        // HubInfo assertion
        JsonNode hubNode = json.get("hub");
        assertEquals("HUB-01", hubNode.get("id").asText());
        assertEquals("Gauteng", hubNode.get("province").asText());
        assertEquals("Johannesburg Central", hubNode.get("sortingCenter").asText());
        assertTrue(hubNode.get("active").asBoolean());

        // Calculation assertions (stage = 0 -> delayHours = 0 -> totalEtaHours = 24)
        assertEquals(0, json.get("stage").asInt());
        assertEquals(0, json.get("delayHours").asInt());
        assertEquals(24, json.get("totalEtaHours").asInt());
    }

    @Test
    @DisplayName("GET /transit/{hubId} computes delay and ETA dynamically after MQ stage update")
    void testGetTransitWithMqStageUpdate() throws Exception {
        String hubId = "HUB-01";
        int stage = 3;
        int expectedDelayHours = 3;
        int expectedTotalEta = 24 + expectedDelayHours; // 27

        // 1. Publish MQ message to update stageCache
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageProducer producer = session.createProducer(topic);

        String jsonPayload = String.format("{\"hubId\":\"%s\",\"stage\":%d}", hubId, stage);
        TextMessage message = session.createTextMessage(jsonPayload);
        producer.send(message);

        connection.close();

        // Give the asynchronous JMS listener a moment to update stageCache
        Thread.sleep(300);

        // 2. Request transit calculation via HTTP
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7053/transit/" + hubId))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        JsonNode json = objectMapper.readTree(response.body());
        assertEquals(stage, json.get("stage").asInt());
        assertEquals(expectedDelayHours, json.get("delayHours").asInt());
        assertEquals(expectedTotalEta, json.get("totalEtaHours").asInt());
    }

    @Test
    @DisplayName("HubInfo getters and state test")
    void testHubInfoModel() throws IOException {
        String json = "{\"id\":\"H1\",\"province\":\"KZN\",\"sortingCenter\":\"Durban\",\"active\":true}";
        HubInfo hub = objectMapper.readValue(json, HubInfo.class);

        assertEquals("H1", hub.getId());
        assertEquals("KZN", hub.getProvince());
        assertEquals("Durban", hub.getSortingCenter());
        assertTrue(hub.isActive());
    }

    @Test
    @DisplayName("DelayStageInfo getters test")
    void testDelayStageInfoModel() throws IOException {
        String json = "{\"hubId\":\"H1\",\"stage\":2}";
        DelayStageInfo info = objectMapper.readValue(json, DelayStageInfo.class);

        assertEquals("H1", info.getHubId());
        assertEquals(2, info.getStage());
    }
}