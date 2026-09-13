package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public class DelayStageServiceApp {

    private static Map<String, Integer> hubStages = new HashMap<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).)
        // Add domain endpoints for delay-stage-service here.

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            Integer stage = hubStages.get(hubId);
            if (stage == null) {
                stage = 0;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("hubId", hubId); response.put("stage", stage); ctx.json(response); }
        );
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            DelayStageRequest request = ctx.bodyAsClass(DelayStageRequest.class);

            hubStages.put(hubId, request.getStage());

            ctx.json(request);
        });
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
