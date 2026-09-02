package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class IngestionServiceApp {

    public static void main(String[] args) throws FileNotFoundException {
        //Javalin app = Javalin.create().start(7050);

        //app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
       // Scanner scanner = new Scanner(new File("src/main/resources/hubs-global.csv"));
        readCSV();
//        groupCSV(readCSV())
    }
    private static void readCSV() throws FileNotFoundException {

        try( Scanner scanner = new Scanner(new File("ingestion-service/src/main/resources/hubs-global.csv"))){
            while (scanner.hasNextLine()){
                String line = scanner.nextLine();
                //System.out.println(line);
                //cleanCSV(line);
                List<List<String>> cleanline = cleanCSV(line);
                System.out.println(groupCSV(cleanline));

                System.out.println(cleanline);
            }

        }
    }
    private static List cleanCSV(String CSV){
        List<List<String>> separateCSV = new ArrayList<>();


        String[] parts = CSV.split(",");
        for (String part : parts){
            List<String> singleCSV = new ArrayList<>();
            singleCSV.add(part.strip()); //i remove unwanted spaces and add it to the list
            separateCSV.add(singleCSV);

        }
        return separateCSV;
    }
    private static Map<String,List<String>> groupCSV(List<List<String>> csvList) {
        Map<String, List<String>> keyValCSV = new HashMap<>();

        String key = csvList.get(1).get(0) +","+csvList.get(2).get(0);

        List<String> value = new ArrayList<>();
        value.add(csvList.get(0).get(0));
        value.add(csvList.get(3).get(0));
        keyValCSV.put(key,value);
        return keyValCSV;
    }
}
