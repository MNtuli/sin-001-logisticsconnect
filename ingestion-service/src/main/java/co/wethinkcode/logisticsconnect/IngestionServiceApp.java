package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class IngestionServiceApp {
    // package-private so tests in the same package can reset/inspect it
    static Map<String, List<List<String>>> keyValCSV = new HashMap<>();

    public static void main(String[] args) throws FileNotFoundException {
        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
        readCSV();
        List<Hub> store = resolveGroups();
        app.get("/hubs", ctx -> ctx.json(store));
        System.out.println(store);
    }

    static void readCSV() throws FileNotFoundException {
        readCSV("src/main/resources/hubs-global.csv");
    }

    static void readCSV(String path) throws FileNotFoundException {

        try (Scanner scanner = new Scanner(new File(path))) {

            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }

            while (scanner.hasNextLine()) {

                String line = scanner.nextLine();
                String[] parts = line.split(",");

                if (parts.length != 4) {
                    continue;
                }
                if (parts[1].strip().isEmpty()) {
                    continue;
                }

                List<List<String>> cleanline = cleanCSV(line);
                groupCSV(cleanline);
            }
        }

        System.out.println(keyValCSV);
    }


    static List<List<String>> cleanCSV(String CSV) {

        List<List<String>> separateCSV = new ArrayList<>();

        String[] parts = CSV.split(",");

        for (int i = 0; i < parts.length; i++) {

            List<String> singleCSV = new ArrayList<>();

            String cleanedPart = parts[i].strip();
            if (i == 0) {
                cleanedPart = cleanedPart.toUpperCase();

            } else if (i == 1) {
                cleanedPart = normalizeProvince(cleanedPart);

            } else if (i == 2) {
                cleanedPart = toTitleCase(cleanedPart);

            } else if (i == 3) {
                cleanedPart = cleanedPart.toUpperCase();
            }

            singleCSV.add(cleanedPart);
            separateCSV.add(singleCSV);
        }

        return separateCSV;
    }


    static String normalizeProvince(String province) {

        province = province.strip();

        if (province.equalsIgnoreCase("KwaZulu-Natal")
                || province.equalsIgnoreCase("Kwa-Zulu Natal")
                || province.equalsIgnoreCase("KwaZulu Natal")
                || province.equalsIgnoreCase("Kwa Zulu Natal")) {

            return "KwaZulu Natal";
        }

        return toTitleCase(province);
    }


    static String toTitleCase(String text) {

        text = text.replace("-", " ");

        String[] words = text.trim().toLowerCase().split("\\s+");

        StringBuilder result = new StringBuilder();

        for (String word : words) {

            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }


    static Map<String, List<List<String>>> groupCSV(
            List<List<String>> csvList) {

        String key = csvList.get(1).get(0) + ","
                + csvList.get(2).get(0);

        List<String> value = new ArrayList<>();

        value.add(csvList.get(0).get(0));

        String active = csvList.get(3).get(0).toUpperCase();

        if (active.equals("Y") || active.equals("YES")
                || active.equals("1") || active.equals("TRUE")) {

            value.add("true");

        } else if (active.equals("N") || active.equals("NO")
                || active.equals("0") || active.equals("FALSE")) {

            value.add("false");

        } else {

            value.add("true");
        }

        if (!keyValCSV.containsKey(key)) {

            List<List<String>> values = new ArrayList<>();
            values.add(value);
            keyValCSV.put(key, values);

        } else {

            keyValCSV.get(key).add(value);
        }

        return keyValCSV;
    }

    static List<Hub> resolveGroups() {

        List<Hub> resolve = new ArrayList<>();

        for (Map.Entry<String, List<List<String>>> entry : keyValCSV.entrySet()) {
            String key = entry.getKey();
            List<List<String>> value = entry.getValue();
            List<String> separateString = List.of(key.split(","));

            String city = separateString.get(1);
            String province = separateString.get(0);

            int countTrue = 0;
            int countFalse = 0;
            String hubId = value.get(0).get(0);

            for (List<String> item : value) {
                if (item.get(1).equals("true")) {
                    countTrue += 1;
                } else {
                    countFalse += 1;
                }
            }
            boolean activebool = countTrue >= countFalse;

            Hub hub = new Hub(hubId, province, city, activebool);
            resolve.add(hub);
        }
        return resolve;
    }
}