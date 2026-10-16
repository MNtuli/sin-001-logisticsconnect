package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HubServiceApp {
    private static HubData[] hubs;

    public static void main(String[] args) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();

        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/hubs/{hubId}", ctx -> {
            String requestedId = ctx.pathParam("hubId");
            if (hubs != null) {
                for (HubData hub : hubs) {
                    if (hub.getId().equals(requestedId)) {
                        ctx.json(hub);
                        return;
                    }
                }
            }
            ctx.status(404).result("Hub not found");
        });

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.

        if (args.length > 0) {
            hubs = mapper.readValue(args[0], HubData[].class);
        } else {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7050/hubs"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            System.out.println(json);
            hubs = mapper.readValue(json, HubData[].class);
        }

        for (HubData hub : hubs) {
            System.out.println(hub.getId() + " - " + hub.getProvince());
        }
    }

    public static ProcessBuilder initApp(HubData[] testHubs) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String hubsJson = mapper.writeValueAsString(testHubs);

            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String className = HubServiceApp.class.getName();

            return new ProcessBuilder(javaBin, "-cp", classpath, className, hubsJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct ProcessBuilder for HubServiceApp", e);
        }
    }
}