package sim.app.firecontrol;

import java.util.LinkedHashSet;
import java.util.Set;
import sim.util.Bag;


public class CellDataPacket {

    public class Header {
        
        // attributes used for avoiding loops in communication
        public UAV sender;
        public Task fire;
        public Set<UAV> visitedUAVs;
        public PacketType type;
        
        public Header(UAV sender, Task fire, PacketType type) {
            this.sender = sender;
            this.fire = fire;
            this.visitedUAVs = new LinkedHashSet<>();
            this.type = type;
        }
        
        public Header(UAV sender, Header header) {
            this.sender = sender;
            this.fire = header.fire;
            this.visitedUAVs = new LinkedHashSet(header.visitedUAVs);
            this.type = header.type;
        }
    };

    public class Payload {
        
        // environment general knowledge
        public Set<UAV> UAVs;
        
        // bidding stage
        public Set<CellBid> bids;
        
        // awarding stage
        public Set<CellAward> awards;
        public Set<WorldCell> soldCells;
        
        
        public Payload(Bag UAVs) {
            this.UAVs = new LinkedHashSet(UAVs);
            this.bids = new LinkedHashSet<>();
            this.awards = new LinkedHashSet<>();
            this.soldCells = new LinkedHashSet<>();
        }
        
        public Payload(Payload payload) {
            this.UAVs = new LinkedHashSet(payload.UAVs);
            this.bids = new LinkedHashSet(payload.bids);
            this.awards = new LinkedHashSet(payload.awards);
            this.soldCells = new LinkedHashSet(payload.soldCells);
        }
    };

    public Header header;
    public Payload payload;
    
    public CellDataPacket(UAV sender, Task fire, PacketType type, Bag UAVs) {
        this.header = new Header(sender, fire, type);
        this.payload = new Payload(UAVs);
    }
    
    public CellDataPacket(UAV sender, CellDataPacket packet) {
        this.header = new Header(sender, packet.header);
        this.payload = new Payload(packet.payload);
    }
    
    public void addBid() {
        this.payload.bids.add(new CellBid(this.header.sender, this.header.fire));
    }
    
    public void addAllBids(Set<CellBid> bids) {
        this.payload.bids.addAll(bids);
    }
    
    public void addAllVisited(Set<UAV> visited) {
        this.header.visitedUAVs.addAll(visited);
    }
    
    public void addSoldCell(WorldCell cell) {
        this.payload.soldCells.add(cell);
    }
    
    public void addAllSoldCells(Set<WorldCell> cells) {
        this.payload.soldCells.addAll(cells);
    }
    
    public PacketType getType() {
        return this.header.type;
    }
    
}
