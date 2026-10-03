package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*; // Or jakarta.jms.* depending on your project's JMS dependency
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransitServiceApp {

    private static final int BASE_HOURS = 24;


    private static final Map<String, Integer> stageCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();


        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createConsumer(topic);

            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage) {
                    try {
                        String jsonText = ((TextMessage) message).getText();
                        JsonNode node = objectMapper.readTree(jsonText);

                        String hubId = node.get("hubId").asText();
                        int stage = node.get("stage").asInt();

                        stageCache.put(hubId, stage);
                    } catch (Exception e) {
                        System.err.println("Error processing MQ message: " + e.getMessage());
                    }
                }
            });
        } catch (JMSException e) {
            System.err.println("Failed to initialize ActiveMQ subscriber: " + e.getMessage());
            e.printStackTrace();
        }

        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/transit/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            try {

                HttpRequest hubRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7051/hubs/" + hubId))
                        .GET()
                        .build();
                HttpResponse<String> hubResponse = httpClient.send(hubRequest, HttpResponse.BodyHandlers.ofString());
                HubInfo hubInfo = objectMapper.readValue(hubResponse.body(), HubInfo.class);


                int stage = stageCache.getOrDefault(hubId, 0);


                int delayHours = stage * 1;
                int totalEtaHours = BASE_HOURS + delayHours;


                Map<String, Object> responseData = new HashMap<>();
                responseData.put("hub", hubInfo);
                responseData.put("stage", stage);
                responseData.put("delayHours", delayHours);
                responseData.put("totalEtaHours", totalEtaHours);

                ctx.json(responseData);

            } catch (Exception e) {
                ctx.status(500).result("Failed to retrieve transit information: " + e.getMessage());
            }
        });
    }
}