package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class HubServiceAppTest {

    private Javalin app;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Sample test fixture
        HubData[] testHubs = new HubData[]{
                new HubData("HUB-01", "Gauteng", "Johannesburg Central", true),
                new HubData("HUB-02", "Western Cape", "Cape Town Depot", false)
        };

        // Port 0 binds to any available ephemeral port to prevent collisions
        app = HubServiceApp.initApp(testHubs).start(0);
        port = app.port();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("GET /health should return 200 OK")
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    @DisplayName("GET /hubs/{hubId} returns 200 and JSON body when hub exists")
    void testGetHubByIdFound() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/hubs/HUB-01"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));

        HubData returnedHub = mapper.readValue(response.body(), HubData.class);
        assertEquals("HUB-01", returnedHub.getId());
        assertEquals("Gauteng", returnedHub.getProvince());
        assertEquals("Johannesburg Central", returnedHub.getSortingCenter());
        assertTrue(returnedHub.isActive());
    }

    @Test
    @DisplayName("GET /hubs/{hubId} returns 404 when hub does not exist")
    void testGetHubByIdNotFound() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/hubs/HUB-999"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals("Hub not found", response.body());
    }

    @Test
    @DisplayName("HubData getters and constructor test")
    void testHubDataModel() {
        HubData hub = new HubData("H1", "KZN", "Durban South", true);

        assertEquals("H1", hub.getId());
        assertEquals("KZN", hub.getProvince());
        assertEquals("Durban South", hub.getSortingCenter());
        assertTrue(hub.isActive());
    }
}