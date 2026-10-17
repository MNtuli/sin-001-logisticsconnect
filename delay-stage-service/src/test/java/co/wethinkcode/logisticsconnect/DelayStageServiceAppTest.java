package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.*;

import javax.jms.*;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DelayStageServiceAppTest {

    private static BrokerService broker;
    private static Thread appThread;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {

        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(MqConfig.BROKER_URL);
        broker.start();
        broker.waitUntilStarted();


        appThread = new Thread(() -> DelayStageServiceApp.main(new String[]{}));
        appThread.setDaemon(true);
        appThread.start();


        long deadline = System.currentTimeMillis() + 8000;
        boolean ready = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest ping = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7052/health"))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(ping, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
                Thread.sleep(150);
            }
        }

        if (!ready) {
            throw new IllegalStateException("DelayStageServiceApp failed to start on port 7052 within timeout.");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (broker != null) {
            broker.stop();
        }
        if (appThread != null) {
            appThread.interrupt();
        }
    }

    @Test
    @DisplayName("GET /health returns 200 with body 'OK'")
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7052/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    @DisplayName("GET /delay-stage/{hubId} returns default stage 0 when hub is unknown")
    void testGetDelayStageDefault() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7052/delay-stage/UNKNOWN-HUB"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        JsonNode json = mapper.readTree(response.body());
        assertEquals("UNKNOWN-HUB", json.get("hubId").asText());
        assertEquals(0, json.get("stage").asInt());
    }

    @Test
    @DisplayName("POST /delay-stage/{hubId} updates stage and publishes message to ActiveMQ topic")
    void testPostDelayStageAndMqPublication() throws Exception {
        String hubId = "HUB-JHB-01";
        int expectedStage = 3;


        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageConsumer consumer = session.createConsumer(topic);

        BlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(1);
        consumer.setMessageListener(message -> {
            if (message instanceof TextMessage textMessage) {
                try {
                    receivedMessages.put(textMessage.getText());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        String requestBody = "{\"stage\":" + expectedStage + "}";
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7052/delay-stage/" + hubId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, postResponse.statusCode());


        DelayStageRequest responseObj = mapper.readValue(postResponse.body(), DelayStageRequest.class);
        assertEquals(expectedStage, responseObj.getStage());


        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7052/delay-stage/" + hubId))
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());

        JsonNode getJson = mapper.readTree(getResponse.body());
        assertEquals(hubId, getJson.get("hubId").asText());
        assertEquals(expectedStage, getJson.get("stage").asInt());


        String mqPayload = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(mqPayload, "ActiveMQ topic message should have been published and received");

        JsonNode mqJson = mapper.readTree(mqPayload);
        assertEquals(hubId, mqJson.get("hubId").asText());
        assertEquals(expectedStage, mqJson.get("stage").asInt());
        assertTrue(mqJson.has("timestamp"), "MQ message should include a timestamp field");

        connection.close();
    }

    @Test
    @DisplayName("DelayStageRequest model getters and setters test")
    void testDelayStageRequestModel() {
        DelayStageRequest request = new DelayStageRequest();
        request.setStage(5);
        assertEquals(5, request.getStage());
    }
}