package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionServiceAppTest {

    @BeforeEach
    void resetState() {
        IngestionServiceApp.keyValCSV.clear();
    }

    // ---------- toTitleCase ----------

    @Test
    void toTitleCase_capitalisesEachWord() {
        assertEquals("Johannesburg South", IngestionServiceApp.toTitleCase("  johannesburg SOUTH "));
    }

    @Test
    void toTitleCase_replacesHyphensWithSpaces() {
        assertEquals("Port Elizabeth", IngestionServiceApp.toTitleCase("port-elizabeth"));
    }

    @Test
    void toTitleCase_collapsesMultipleSpaces() {
        assertEquals("Cape Town", IngestionServiceApp.toTitleCase("cape    town"));
    }

    // ---------- normalizeProvince ----------

    @Test
    void normalizeProvince_unifiesKwaZuluNatalVariants() {
        String[] variants = {"KwaZulu-Natal", "kwa-zulu natal", "KWAZULU NATAL", "Kwa Zulu Natal"};
        for (String v : variants) {
            assertEquals("KwaZulu Natal", IngestionServiceApp.normalizeProvince(v), "variant: " + v);
        }
    }

    @Test
    void normalizeProvince_titleCasesOthers() {
        assertEquals("Gauteng", IngestionServiceApp.normalizeProvince("  gAUTENG "));
        assertEquals("Western Cape", IngestionServiceApp.normalizeProvince("WESTERN-CAPE"));
    }

    // ---------- cleanCSV ----------

    @Test
    void cleanCSV_trimsAndFixesCasingPerColumn() {
        List<List<String>> result =
                IngestionServiceApp.cleanCSV(" hub01 , gauteng , johannesburg south , yes ");

        assertEquals(4, result.size());
        assertEquals("HUB01", result.get(0).get(0));
        assertEquals("Gauteng", result.get(1).get(0));
        assertEquals("Johannesburg South", result.get(2).get(0));
        assertEquals("YES", result.get(3).get(0));
    }

    // ---------- groupCSV ----------

    private static List<List<String>> row(String id, String province, String city, String active) {
        return List.of(List.of(id), List.of(province), List.of(city), List.of(active));
    }

    @Test
    void groupCSV_truthyValuesMapToTrue() {
        for (String v : new String[]{"Y", "YES", "1", "TRUE"}) {
            IngestionServiceApp.keyValCSV.clear();
            IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", v));
            assertEquals("true", IngestionServiceApp.keyValCSV.get("Gauteng,Sandton").get(0).get(1), v);
        }
    }

    @Test
    void groupCSV_falsyValuesMapToFalse() {
        for (String v : new String[]{"N", "NO", "0", "FALSE"}) {
            IngestionServiceApp.keyValCSV.clear();
            IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", v));
            assertEquals("false", IngestionServiceApp.keyValCSV.get("Gauteng,Sandton").get(0).get(1), v);
        }
    }

    @Test
    void groupCSV_unknownPlaceholdersDefaultToTrue() {
        for (String v : new String[]{"N/A", "TBD", "UNKNOWN", "-", "NAN"}) {
            IngestionServiceApp.keyValCSV.clear();
            IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", v));
            assertEquals("true", IngestionServiceApp.keyValCSV.get("Gauteng,Sandton").get(0).get(1), v);
        }
    }

    @Test
    void groupCSV_sameProvinceAndCityAreGroupedTogether() {
        IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", "Y"));
        IngestionServiceApp.groupCSV(row("H2", "Gauteng", "Sandton", "N"));
        IngestionServiceApp.groupCSV(row("H3", "Western Cape", "Paarl", "Y"));

        assertEquals(2, IngestionServiceApp.keyValCSV.size());
        assertEquals(2, IngestionServiceApp.keyValCSV.get("Gauteng,Sandton").size());
    }

    // ---------- resolveGroups ----------

    @Test
    void resolveGroups_majorityActiveWins() {
        IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", "Y"));
        IngestionServiceApp.groupCSV(row("H2", "Gauteng", "Sandton", "Y"));
        IngestionServiceApp.groupCSV(row("H3", "Gauteng", "Sandton", "N"));

        List<Hub> hubs = IngestionServiceApp.resolveGroups();

        assertEquals(1, hubs.size());
        assertTrue(hubs.get(0).isActive());
    }

    @Test
    void resolveGroups_majorityInactiveWins() {
        IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", "N"));
        IngestionServiceApp.groupCSV(row("H2", "Gauteng", "Sandton", "N"));
        IngestionServiceApp.groupCSV(row("H3", "Gauteng", "Sandton", "Y"));

        assertFalse(IngestionServiceApp.resolveGroups().get(0).isActive());
    }

    @Test
    void resolveGroups_tieResolvesToActive() {
        IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", "Y"));
        IngestionServiceApp.groupCSV(row("H2", "Gauteng", "Sandton", "N"));

        assertTrue(IngestionServiceApp.resolveGroups().get(0).isActive());
    }

    @Test
    void resolveGroups_populatesHubFieldsCorrectly() {
        IngestionServiceApp.groupCSV(row("H1", "Gauteng", "Sandton", "Y"));

        Hub hub = IngestionServiceApp.resolveGroups().get(0);

        assertEquals("H1", hub.getId());
        assertEquals("Gauteng", hub.getProvince());
        assertEquals("Sandton", hub.getSortingCenter());
    }

    // ---------- readCSV (integration with a temp file) ----------

    @Test
    void readCSV_skipsHeaderBadRowsAndMissingProvince(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("hubs.csv");
        Files.writeString(csv, String.join("\n",
                "hub_id,province,sorting_center,active",
                "hub1, gauteng ,johannesburg, Y",
                "hub2,GAUTENG,JOHANNESBURG,n",
                "hub3,,Durban,Y",              // missing province -> skipped
                "bad,row",                      // wrong column count -> skipped
                "hub4,kwazulu-natal,durban,true"
        ));

        IngestionServiceApp.readCSV(csv.toString());

        assertEquals(2, IngestionServiceApp.keyValCSV.size());
        assertEquals(2, IngestionServiceApp.keyValCSV.get("Gauteng,Johannesburg").size());
        assertEquals(1, IngestionServiceApp.keyValCSV.get("KwaZulu Natal,Durban").size());
        assertFalse(IngestionServiceApp.keyValCSV.containsKey("Province,Sorting Center"),
                "header row must not be ingested");
    }

    @Test
    void readCSV_missingFileThrows() {
        assertThrows(java.io.FileNotFoundException.class,
                () -> IngestionServiceApp.readCSV("does/not/exist.csv"));
    }
}