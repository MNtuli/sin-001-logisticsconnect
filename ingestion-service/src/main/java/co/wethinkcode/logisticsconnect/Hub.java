package co.wethinkcode.logisticsconnect;

import java.util.List;

public class Hub {
    String id_Hub;
    String province;
    String sorting_center;
    boolean active;

    public Hub(String id_Hub, String province, String sorting_center, boolean active){
        this.id_Hub = id_Hub;
        this.province = province;
        this.sorting_center = sorting_center;
        this.active = active;
    }
    public String getId() {
        return this.id_Hub;
    }

    public String getProvince() {
        return province;
    }

    public String getSortingCenter() {
        return this.sorting_center;
    }

    public boolean isActive() {
        return active;
    }
}
// hub_id, Province ,sorting_center,active