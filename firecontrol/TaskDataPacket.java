/*
 * Simple structure for a data packet.
 * 
 * @author dario albani
 * @mail dario.albani@istc.cnr.it
 */

package sim.app.firecontrol;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import sim.util.Bag;


public class TaskDataPacket {

    public class Header {
        
        // attributes used for avoiding loops in communication
        public UAV sender;
        public Set<UAV> visitedUAVs;
        public PacketType type;
        
        public Header(UAV sender, PacketType type) {
            this.sender = sender;
            this.visitedUAVs = new LinkedHashSet();
            this.type = type;
        }
        
        public Header(UAV sender, Header header) {
            this.sender = sender;
            this.visitedUAVs = new LinkedHashSet(header.visitedUAVs);
            this.type = header.type;
        }
    };

    public class Payload {
        
        // environment general knowledge
        public Set<UAV> UAVs;
        public LinkedList<Task> tasks;
        
        // bidding stage
        public Set<TaskBid> bids;
        
        // awarding stage
        public Set<TaskAward> awards;
        
        public Payload(Bag UAVs, LinkedList<Task> tasks) {
            this.UAVs = new LinkedHashSet(UAVs);
            this.bids = new LinkedHashSet<>();
            this.awards = new LinkedHashSet<>();
            
            if(tasks == null)
                this.tasks = null;
            else
                this.tasks = new LinkedList(tasks);
        }
        
        public Payload(Payload payload) {
            this.UAVs = new LinkedHashSet(payload.UAVs);
            this.bids = new LinkedHashSet(payload.bids);
            this.awards = new LinkedHashSet(payload.awards);
            
            if(payload.tasks == null)
                this.tasks = null;
            else
                this.tasks = new LinkedList<>(payload.tasks);
        }
    };

    public Header header;
    public Payload payload;
    
    public TaskDataPacket(UAV sender, PacketType type, Bag UAVs, LinkedList<Task> tasks) {
        this.header = new Header(sender, type);
        this.payload = new Payload(UAVs, tasks);
    }
    
    public TaskDataPacket(UAV sender, TaskDataPacket packet) {
        this.header = new Header(sender, packet.header);
        this.payload = new Payload(packet.payload);
    }
    
    public void addBid() {
        this.payload.bids.add(new TaskBid(this.header.sender, this.payload.tasks));
    }
    
    public void addAllBids(Set<TaskBid> bids) {
        this.payload.bids.addAll(bids);
    }
    
    public void addAllVisited(Set visited) {
        this.header.visitedUAVs.addAll(visited);
    }
    
    public PacketType getType() {
        return this.header.type;
    }
    
}
