package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*; // Or jakarta.jms.* depending on your dependency versions
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DelayStageServiceApp {

    private static Map<String, Integer> hubStages = new HashMap<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            Integer stage = hubStages.get(hubId);
            if (stage == null) {
                stage = 0;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("hubId", hubId);
            response.put("stage", stage);
            ctx.json(response);
        });

        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");

            DelayStageRequest request = ctx.bodyAsClass(DelayStageRequest.class);

            hubStages.put(hubId, request.getStage());

            // ActiveMQ publisher
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageProducer producer = session.createProducer(topic);

            String timestamp = Instant.now().toString();
            String jsonString = String.format(
                    "{\"hubId\":\"%s\",\"stage\":%d,\"timestamp\":\"%s\"}",
                    hubId, request.getStage(), timestamp
            );

            TextMessage message = session.createTextMessage(jsonString);
            producer.send(message);

            connection.close();

            ctx.json(request);
        });
    }
}