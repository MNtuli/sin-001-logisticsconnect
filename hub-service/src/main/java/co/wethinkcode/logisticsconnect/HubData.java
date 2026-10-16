package co.wethinkcode.logisticsconnect;

public class HubData {
    String id;
    String province;
    String sortingCenter;
    boolean active;

    public HubData(String id, String province, String sortingCenter, boolean active){
        this.id = id;
        this.province = province;
        this.sortingCenter = sortingCenter;
        this.active = active;
    }
    public HubData() {
    }
    public String getId() {
        return this.id;
    }

    public String getProvince() {
        return province;
    }

    public String getSortingCenter() {
        return this.sortingCenter;
    }

    public boolean isActive() {
        return active;
    }
}
