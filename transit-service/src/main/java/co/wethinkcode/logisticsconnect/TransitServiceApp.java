package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class TransitServiceApp {

    private static final int BASE_HOURS = 24;


    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7053);
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        // TODO (Calculates estimated arrival windows based on hub and delay stage.)
        // Add domain endpoints for transit-service here.

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/transit/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            try {
                // Fetch HubInfo from hub-service
                HttpRequest hubRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7051/hubs/" + hubId))
                        .GET()
                        .build();
                HttpResponse<String> hubResponse = httpClient.send(hubRequest, HttpResponse.BodyHandlers.ofString());
                HubInfo hubInfo = objectMapper.readValue(hubResponse.body(), HubInfo.class);

                // Fetch DelayStageInfo from delay-stage-service
                HttpRequest delayRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7052/delay-stage/" + hubId))
                        .GET()
                        .build();
                HttpResponse<String> delayResponse = httpClient.send(delayRequest, HttpResponse.BodyHandlers.ofString());
                DelayStageInfo delayStageInfo = objectMapper.readValue(delayResponse.body(), DelayStageInfo.class);

                // Calculate delays and ETA
                int stage = delayStageInfo != null ? delayStageInfo.getStage() : 0;
                int delayHours = stage * 1;
                int totalEtaHours = BASE_HOURS + delayHours;

                // Building response map and return as JSON
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

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
