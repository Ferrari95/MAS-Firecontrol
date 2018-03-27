package sim.app.firecontrol;

public class TaskAward {

    public UAV bidder;
    public Task award; // manager awards each bidder with one task

    public TaskAward(UAV bidder, Task award) {
        this.bidder = bidder;
        this.award = award;
    }
}