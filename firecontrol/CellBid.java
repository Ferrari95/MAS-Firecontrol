package sim.app.firecontrol;

import java.lang.Math;
import java.util.LinkedList;
import sim.util.Double2D;
import sim.util.Double3D;

public class CellBid {

    public UAV bidder;
    public LinkedList<Double> bids; // for the bidder, one bid for each cell
    public LinkedList<WorldCell> cells;

    public CellBid(UAV bidder, Task fire) {
        this.bidder = bidder;
        this.bids = new LinkedList<>();
        this.cells = new LinkedList<>();
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
                
                
                double u = u1 * u2 * u3;

                bids.add(u);
                cells.add(iterCell);
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