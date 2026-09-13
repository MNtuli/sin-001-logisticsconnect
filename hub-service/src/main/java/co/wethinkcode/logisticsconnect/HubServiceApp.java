package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HubServiceApp {
    private static HubData[] hubs;

    public static void main(String[] args) throws IOException, InterruptedException {
        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/hubs/{hubId}", ctx -> { String requestedId = ctx.pathParam("hubId");
            for (HubData hub : hubs) {
                if (hub.getId().equals(requestedId)) { ctx.json(hub);
                    return;
                }
            }
            ctx.status(404).result("Hub not found"); });

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7050/hubs"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String json = response.body();

        System.out.println(json); ObjectMapper mapper = new ObjectMapper();

        hubs = mapper.readValue(json, HubData[].class);
        for (HubData hub : hubs) {
            System.out.println(hub.getId() + " - " + hub.getProvince());
        }

    }
}


