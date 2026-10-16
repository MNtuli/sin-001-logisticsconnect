package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class HubServiceAppTest {

    private static Process process;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        HubData[] testHubs = new HubData[]{
                new HubData("HUB-01", "Gauteng", "Johannesburg Central", true),
                new HubData("HUB-02", "Western Cape", "Cape Town Depot", false)
        };

        // Initialize and start the sub-process using initApp
        ProcessBuilder processBuilder = HubServiceApp.initApp(testHubs);
        processBuilder.inheritIO(); // Streams logs to console for debugging
        process = processBuilder.start();

        // Poll /health endpoint until the Javalin server is up
        long timeout = System.currentTimeMillis() + 8000;
        boolean ready = false;

        while (System.currentTimeMillis() < timeout) {
            try {
                HttpRequest ping = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:7051/health"))
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
            if (process != null) {
                process.destroyForcibly();
            }
            throw new IllegalStateException("HubServiceApp process failed to start on port 7051.");
        }
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    @DisplayName("GET /health returns 200 and OK")
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7051/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    @DisplayName("GET /hubs/{hubId} returns 404 when hub ID is missing")
    void testGetHubByIdNotFound() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7051/hubs/HUB-999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals("Hub not found", response.body());
    }
}