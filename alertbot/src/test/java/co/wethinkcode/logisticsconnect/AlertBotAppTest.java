

package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jms.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.IllegalStateException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertBotAppTest {

    private static BrokerService broker;
    private static Thread appThread;
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
    private static final PrintStream originalOut = System.out;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. Capture System.out to verify [ALERT] and [INFO] logs
        System.setOut(new PrintStream(outputCapture));

        // 2. Start embedded in-memory ActiveMQ broker
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(MqConfig.BROKER_URL);
        broker.start();
        broker.waitUntilStarted();

        // 3. Start AlertBotApp in background daemon thread
        appThread = new Thread(() -> AlertBotApp.main(new String[]{}));
        appThread.setDaemon(true);
        appThread.start();

        // 4. Poll http://localhost:7054/health until Javalin is ready
        long deadline = System.currentTimeMillis() + 8000;
        boolean ready = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest ping = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7054/health"))
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
            throw new IllegalStateException("AlertBotApp failed to start on port 7054 within timeout.");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        System.setOut(originalOut);

        if (broker != null) {
            broker.stop();
        }
        if (appThread != null) {
            appThread.interrupt();
        }
    }

    private void publishTopicMessage(String jsonPayload) throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageProducer producer = session.createProducer(topic);

        TextMessage message = session.createTextMessage(jsonPayload);
        producer.send(message);

        connection.close();
    }

    @Test
    @DisplayName("GET /health returns 200 and body 'OK'")
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7054/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    @DisplayName("Message with stage < 6 prints [INFO] and no alert")
    void testBelowThresholdLog() throws Exception {
        String hubId = "HUB-UNDER-THRESHOLD";
        int stage = 3;
        String jsonPayload = String.format("{\"hubId\":\"%s\",\"stage\":%d}", hubId, stage);

        publishTopicMessage(jsonPayload);

        // Allow async consumer thread to process message
        Thread.sleep(300);

        String consoleOutput = outputCapture.toString();
        assertTrue(consoleOutput.contains("[INFO] Hub " + hubId + " delay stage " + stage
                + " - below alert threshold. No notification required."));
    }

    @Test
    @DisplayName("Message with stage >= 6 prints [ALERT] and triggers notification output")
    void testAboveThresholdLog() throws Exception {
        String hubId = "HUB-OVER-THRESHOLD";
        int stage = 6;
        String jsonPayload = String.format("{\"hubId\":\"%s\",\"stage\":%d}", hubId, stage);

        publishTopicMessage(jsonPayload);

        // Allow async consumer thread to process message
        Thread.sleep(300);

        String consoleOutput = outputCapture.toString();
        assertTrue(consoleOutput.contains("[ALERT] Hub " + hubId + " delay stage " + stage
                + " (>= 6) - posting notification to social media..."));
    }
}