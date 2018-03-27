package sim.app.firecontrol;

public class CellAward {

    public UAV bidder;
    public WorldCell award; // manager awards each bidder with one cell
                            // award is NULL in case the fire has been estinguished

    public CellAward(UAV bidder, WorldCell award) {
        this.bidder = bidder;
        this.award = award;
    }
}