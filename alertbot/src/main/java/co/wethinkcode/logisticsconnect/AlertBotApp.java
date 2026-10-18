package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class AlertBotApp {

    private static final int ALERT_THRESHOLD = 6;

    public static void main(String[] args) {
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


                        if (stage >= ALERT_THRESHOLD) {
                            System.out.println("[ALERT] Hub " + hubId + " delay stage " + stage
                                    + " (>= " + ALERT_THRESHOLD + ") - posting notification to social media...");
                        } else {
                            System.out.println("[INFO] Hub " + hubId + " delay stage " + stage
                                    + " - below alert threshold. No notification required.");
                        }

                    } catch (Exception e) {
                        System.err.println("Error processing alert event: " + e.getMessage());
                    }
                }
            });

        } catch (JMSException e) {
            System.err.println("Failed to start ActiveMQ alert listener: " + e.getMessage());
            e.printStackTrace();
        }

        Javalin app = Javalin.create().start(7054);

        app.get("/health", ctx -> ctx.result("OK"));
    }
}