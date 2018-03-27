package sim.app.firecontrol;

import java.lang.Math;
import java.util.LinkedList;
import sim.util.Double2D;

public class TaskBid {

    public UAV bidder;
    public LinkedList<Double> bids; // for the bidder, one bid for each task

    public TaskBid(UAV bidder, LinkedList<Task> tasks) {
        this.bidder = bidder;
        this.bids = new LinkedList<>();
        this.addBids(tasks);
    }

    public void addBids(LinkedList<Task> tasks) {
        Double2D bidderPos = new Double2D(bidder.x, bidder.y);
        for(Task task: tasks) {
            double bid = 1 - (bidderPos.distance(task.centroid) /           // the bid depends only by the distance wrt task
                             (Math.sqrt(Math.pow(Ignite.height, 2) + Math.pow(Ignite.width, 2))));
            bids.add(bid);
        }
    }
}