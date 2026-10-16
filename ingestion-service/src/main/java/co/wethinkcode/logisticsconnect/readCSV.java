package co.wethinkcode.logisticsconnect;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class readCSV {

    public static void main(String[] args) throws FileNotFoundException {
        try(
                Scanner scanner = new Scanner(new File("/resources/hubs-global.csv"))){
            while(scanner.hasNextLine()){

            }
        }
    }
}
