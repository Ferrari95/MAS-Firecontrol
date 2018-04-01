package sim.app.firecontrol;

import java.lang.Math;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import sim.util.Double2D;
import sim.util.Double3D;

public class CellBid {
    
    class Bid {
        public double bid;
        public double cellDistFromUav;
        public WorldCell cell;
        
        public Bid(double bid, WorldCell cell, double cellDistFromUav) {
            this.bid = bid;
            this.cell = cell;
            this.cellDistFromUav = cellDistFromUav;
        }
    }    
    
    class BidComparator implements Comparator {
        @Override
        public int compare(Object o1, Object o2) {
            try {
                Bid bid1 = (Bid) o1;
                Bid bid2 = (Bid) o2;
                if(bid1.cellDistFromUav == bid2.cellDistFromUav)
                    return 0;
                else if(bid1.cellDistFromUav < bid2.cellDistFromUav)
                    return -1;
                else
                    return 1;
            }
            catch(UnsupportedOperationException e) {
            }
            return -1;
        }
    }

    public UAV bidder;
    PriorityQueue bids;
    
    public CellBid(UAV bidder, Task fire) {
        this.bidder = bidder;
        BidComparator comparator = new BidComparator();
        this.bids = new PriorityQueue(1, comparator);
        this.addBids(fire);
    }

    public void addBids(Task fire) {
        double uavTheta = Math.atan2(bidder.y - fire.centroid.y, bidder.x - fire.centroid.x);
        Double2D centroid = new Double2D(fire.centroid.x, fire.centroid.y);
        Double2D myLoc = new Double2D(bidder.x, bidder.y);
        
        LinkedList<WorldCell> forSaleCells = new LinkedList<>(fire.cells);
        forSaleCells.removeAll(bidder.soldCells);
        
        for(WorldCell iterCell: forSaleCells) {
            if(iterCell.getType().equals(CellType.FIRE)) {                
                
                // utility 1
                double cellTheta = Math.atan2(iterCell.y - fire.centroid.y, iterCell.x - fire.centroid.x);
                double angDispl = Math.abs(cellTheta - uavTheta);
                if(angDispl > Math.PI)
                    angDispl = Math.abs(angDispl - 2.0 * Math.PI);

                double normAngDispl = angDispl / Math.PI;
                
                double a = 4.0;
                double u1 = Math.pow(Math.E, -a * normAngDispl);
                
                // utility 2
                double cellDistFromCentr = centroid.distance(new Double2D(iterCell.x, iterCell.y));
                double normCellDistFromCentr = cellDistFromCentr / (fire.radius + 0.01);
                
                double b = 5.0;
                double u2 = Math.pow(Math.E, b * (normCellDistFromCentr - 1));

                // utility 3
                double cellDistFromUav = myLoc.distance(new Double2D(iterCell.x, iterCell.y));
                double normCellDistFromUav = cellDistFromUav / (fire.radius * 2 + 0.01);
                
                double c = 7.0;
                double u3 = Math.pow(Math.E, -c * normCellDistFromUav);
                
                // product of utilities
                double p = u1 * u2 * u3;
                
                // final utility
                double u = Math.pow(Math.E, p / 0.01);
                
                Bid bid = new Bid(u, iterCell, cellDistFromUav);
                bids.offer(bid);
            }
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj.getClass().equals(CellBid.class)) {
            CellBid cellBid = (CellBid) obj;
            return cellBid.bidder == this.bidder;
        }
        return false;            
    }
}