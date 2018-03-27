/**
 * A single UAV running over the simulation. 
 * This class implements the class Steppable, the latter requires the implementation 
 * of one crucial method: step(SimState).
 * Please refer to Mason documentation for further details about the step method and how the simulation
 * loop is working.
 * 
 * @author dario albani
 * @mail albani@dis.uniroma1.it
 * @thanks Sean Luke
 */
package sim.app.firecontrol;

import java.util.LinkedHashSet;
import java.util.Set;
import sim.util.Bag;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Double2D;
import sim.util.Double3D;
import sim.util.Int3D;

public class UAV implements Steppable {
    private static final long serialVersionUID = 1L;

    // Agent's variable 
    public int id; //unique ID
    public double x; //x position in the world
    public double y; //y position in the world
    public double z; //z position in the world
    public Double3D target; //UAV target
    public AgentAction action; //last action executed by the UAV: SELECT_TASK, SELECT_CELL, MOVE, EXTINGUISH
    public static double communicationRange = 30; //communication range for the UAVs

    // Agent's local knowledge 
    public Set<WorldCell> soldCells;
    public Task myTask;
    public TaskDataPacket taskPacket;
    public CellDataPacket cellPacket;

    // Agent's settings - static because they have to be the same for all the 
    // UAV in the simulation. If you change it once, you change it for all the UAV.
    public static double linearvelocity = 0.02;

    //used to count the steps needed to extinguish a fire in a location
    public static int stepToExtinguish = 10;
    //used to remember when first started to extinguish at current location
    private int startedToExtinguishAt = -1;

    public UAV(int id, Double3D myPosition) {
        //set agent's id
        this.id = id;
        //set agent's position
        this.x = myPosition.x;
        this.y = myPosition.y;
        this.z = myPosition.z;
        //at the beginning agents have no action
        this.action = null;
        //at the beginning agents have no known cells 
        this.soldCells = new LinkedHashSet<>();
    }

    // DO NOT REMOVE
    // Getters and setters are used to display information in the inspectors
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return this.y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    /**
     *  Do one step.
     *  Core of the simulation.   
     */
    public void step(SimState state) {
        Ignite ignite = (Ignite)state;

        //select the next action for the agent
        AgentAction a = nextAction(ignite);
        
        switch(a) {
        case SELECT_TASK:
            selectTask(ignite);
            
            this.action = a;
            break;

        case SELECT_CELL:
            selectCell(ignite);
            break;

        case MOVE:
            move(state);
            recomputeTarget(ignite);
            break;

        case EXTINGUISH:
            //if true set the cell to be normal and foamed
            if(extinguish(ignite)) {
                //retrieve discrete location of this
                Int3D dLoc = ignite.air.discretize(new Double3D(this.x, this.y, this.z));
                //extinguish the fire
                ((WorldCell)ignite.forest.field[dLoc.x][dLoc.y]).extinguish(ignite);
                this.target = null;
                this.cellPacket = null;
            }

            this.action = a;
            break;

        default:	
            System.exit(-1);
        }
    }
    
    private AgentAction nextAction(Ignite ignite){
        //if I do not have a task I need to take one
        if(this.myTask == null) {
            return AgentAction.SELECT_TASK;
        }
        //else, if I have a task but I do not have target I need to take one
        else if(this.target == null) {
            return AgentAction.SELECT_CELL;
        }
        //else, if I have a target and task I need to move toward the target
        //check if I am over the target and in that case execute the right action;
        //if not, continue to move toward the target
        else if(this.target.equals(ignite.air.discretize(new Double3D(x, y, z)))) {
            //if on fire then extinguish, otherwise move on
            WorldCell cell = (WorldCell)ignite.forest.field[(int) x][(int) y];

            if(cell.type.equals(CellType.FIRE)) {
                return AgentAction.EXTINGUISH;
            }
            else {
                this.target = null;
                return AgentAction.SELECT_CELL;
            }

        }
        else {
            return AgentAction.MOVE;
        }
    }

    /**
     * Take the centroid of the fire and its expected radius and extract the new
     * task for the agent.
     */
    private void selectTask(Ignite ignite) {
        Task newTask = null;
        
        if(this.taskPacket == null) {
            TaskDataPacket packet = new TaskDataPacket(this, PacketType.ANNOUNCEMENT, ignite.UAVs, ignite.tasks);
            taskReceiveData(packet);
            
            packet = new TaskDataPacket(this, PacketType.AWARDING, ignite.UAVs, null);
            double netSize = this.taskPacket.payload.bids.size();
            int[] employedUAVsPerTask = new int[ignite.tasks.size()];
            
            for(TaskBid bid: this.taskPacket.payload.bids) {
                UAV bidder = bid.bidder;
                double uMax = 0;
                int bestTask = 0;
                for(int i = 0; i < ignite.tasks.size(); i++) {
                    Task task = ignite.tasks.get(i);
                    double employedUAVs = employedUAVsPerTask[i];
                    
                    // first part of the utility consists of the bid over the task
                    double u1 = bid.bids.get(i);
                    
                    // second part of the utility depends on the number of employed agents in the task
                    double u2 = 1 - (employedUAVs / netSize);
                    
                    double a = 0.5;
                    double b = 0.5;
                    double u = a * u1 + b * u2;
                    
                    if(u >= uMax) {
                        Set<WorldCell> forSaleCells = task.getCellsOnFire();
                        forSaleCells.removeAll(this.soldCells);
                        if(forSaleCells.size() > 0) {
                            uMax = u;
                            bestTask = i;
                        }
                    }
                }
                employedUAVsPerTask[bestTask]++;
                
                TaskAward award = new TaskAward(bidder, ignite.tasks.get(bestTask));
                packet.payload.awards.add(award);
            }
            
            taskReceiveData(packet);
        }
        
        for(TaskAward award: this.taskPacket.payload.awards)
            if(award.bidder == this)
                newTask = award.award;
            
        try {
            Set<WorldCell> cellsOnFire = newTask.getCellsOnFire();
            if(cellsOnFire.size() > 0) {
                this.myTask = newTask;
                
                Double2D myLoc = new Double2D(x, y);
                double uMax = 0.0;
                WorldCell bestCell = null;
                
                for(WorldCell iterCell: cellsOnFire) {
                    double cellDistFromUav = myLoc.distance(new Double2D(iterCell.x, iterCell.y));
                    double u = 1 - (cellDistFromUav / Math.sqrt(2.0 * Math.pow(ignite.height, 2.0))); ;
                    
                    if(u >= uMax) {
                        uMax = u;
                        bestCell = iterCell;
                    }
                }
                
                this.target = new Double3D(bestCell.x, bestCell.y, z);
                this.soldCells.add(bestCell);
            }
            else {
                this.myTask = null;
            }
        } catch(NullPointerException e) {
            System.err.println("Something is null, have you forgetten to implement some part?");
        }
    }

    /**
     * Take the centroid of the fire and its expected radius and select the next 
     * cell that requires closer inspection or/and foam. 
     */
    private void selectCell(Ignite ignite) {
        CellDataPacket packet = new CellDataPacket(this, this.myTask, PacketType.ANNOUNCEMENT, ignite.UAVs);
        cellReceiveData(packet);

        packet = new CellDataPacket(this, this.myTask, PacketType.AWARDING, ignite.UAVs);
        packet.addAllSoldCells(this.soldCells);
        
        for(CellBid bid: this.cellPacket.payload.bids) {
            UAV bidder = bid.bidder;
            double uMax = 0;
            WorldCell bestCell = null;
            for(int i = 0; i < bid.bids.size(); i++) {
                double u = bid.bids.get(i);
                WorldCell cell = bid.cells.get(i);
                if(u >= uMax && !packet.payload.soldCells.contains(cell)) {
                    uMax = u;
                    bestCell = cell;
                }
            }

            CellAward award = new CellAward(bidder, bestCell);
            packet.payload.awards.add(award);
            packet.addSoldCell(bestCell);
        }
        
        cellReceiveData(packet);
        
        if(this.target == null) {
            this.myTask = null;
            this.taskPacket = null;
            this.cellPacket = null;
        }
    }

    /**
     * Move the agent toward the target position
     * The agent moves at a fixed given velocity
     * @see this.linearvelocity
     */
    public void move(SimState state) {
        Ignite ignite = (Ignite) state;

        // retrieve the location of this 
        Double3D location = ignite.air.getObjectLocationAsDouble3D(this);
        double myx = location.x;
        double myy = location.y;
        double myz = location.z;

        // compute the distance w.r.t. the target
        // the z axis is only used when entering or leaving an area
        double xdistance = this.target.x - myx;
        double ydistance = this.target.y - myy;

        if(xdistance < 0)
            myx -= Math.min(Math.abs(xdistance), linearvelocity);
        else
            myx += Math.min(xdistance, linearvelocity);

        if(ydistance < 0) { 
            myy -= Math.min(Math.abs(ydistance), linearvelocity);
        }
        else {	
            myy += Math.min(ydistance, linearvelocity);
        }

        // update position in the simulation
        ignite.air.setObjectLocation(this, new Double3D(myx, myy, myz));
        // update position local position
        this.x = myx;
        this.y = myy;
        this.z = myz;
    }

    /**
     * Start to extinguish the fire at current location.
     * @return true if enough time has passed and the fire is gone, false otherwise
     * @see this.stepToExtinguish
     * @see this.startedToExtinguishAt
     */
    private boolean extinguish(Ignite ignite) {
        if(startedToExtinguishAt==-1) {
            this.startedToExtinguishAt = (int) ignite.schedule.getSteps();
        }
        //enough time has passed, the fire is gone
        if(ignite.schedule.getSteps() - startedToExtinguishAt >= stepToExtinguish) {
            startedToExtinguishAt = -1;
            return true;
        }		
        return false;
    }
    
    private void recomputeTarget(Ignite ignite) {
        WorldCell cell = (WorldCell)ignite.forest.field[(int) this.target.x][(int) this.target.y];
        if(!cell.type.equals(CellType.FIRE))
            this.target = null;
    }

    /**
     * COMMUNICATION
     * Check if the input location is within communication range
     */
    public boolean isInCommunicationRange(Double3D otherLoc) {
        Double3D myLoc = new Double3D(x, y, z);
        return myLoc.distance(otherLoc) <= UAV.communicationRange;
    }
    
    /**
     * COMMUNICATION
     * Returns all the agents within the communication range
     */
    public Set agentsInRange(Set UAVs) {
        Set set = new LinkedHashSet();
        
        for(Object obj : UAVs) {
            UAV other = (UAV) obj;
            if(isInCommunicationRange(new Double3D(other.x, other.y, other.z)))
                set.add(other);
        }

        return set;
    }
    
    /**
     * COMMUNICATION
     * Send a message to the team
     */
    public void taskSendData(TaskDataPacket packet) {
        switch(packet.getType()) {
            case ANNOUNCEMENT: {
                this.taskPacket = new TaskDataPacket(this, packet);
                this.taskPacket.header.visitedUAVs.add(this);

                Set inRange = agentsInRange(this.taskPacket.payload.UAVs);
                Set visited = this.taskPacket.header.visitedUAVs;
                inRange.removeAll(visited);
                while(!inRange.isEmpty()) {
                    Iterator<UAV> it = inRange.iterator();
                    UAV recipient = it.next();
                    inRange.remove(recipient);
                    
                    recipient.taskReceiveData(this.taskPacket);
                    inRange.removeAll(visited);
                }

                this.taskPacket.addBid();
                this.taskPacket.header.type = PacketType.BIDDING;

                if(!this.equals(packet.header.sender)) {
                    UAV recipient = packet.header.sender;
                    recipient.taskReceiveData(this.taskPacket);
                }
                
                break;
            }
            case AWARDING: {
                this.taskPacket = new TaskDataPacket(this, packet);
                this.taskPacket.header.visitedUAVs.add(this);
                
                Set inRange = agentsInRange(this.taskPacket.payload.UAVs);
                Set visited = this.taskPacket.header.visitedUAVs;
                inRange.removeAll(visited);
                while(!inRange.isEmpty()) {
                    Iterator<UAV> it = inRange.iterator();
                    UAV recipient = it.next();
                    inRange.remove(recipient);
                    
                    recipient.taskReceiveData(this.taskPacket);
                    inRange.removeAll(visited);
                }
                
                this.taskPacket.header.type = PacketType.EXPEDITING;

                if(!this.equals(packet.header.sender)) {
                    UAV recipient = packet.header.sender;
                    recipient.taskReceiveData(this.taskPacket);
                }
                
                break;
            }
            default: {
                System.exit(-1);
            }
        }
    }

    /**
     * COMMUNICATION
     * Receive a message from the team
     */
    public void taskReceiveData(TaskDataPacket packet) {        
        switch(packet.getType()) {
            case ANNOUNCEMENT: {
                this.taskSendData(packet);
                break;
            }
            case BIDDING: {
                this.taskPacket.addAllBids(packet.payload.bids);
                this.taskPacket.addAllVisited(packet.header.visitedUAVs);
                break;
            }
            case AWARDING: {
                this.taskSendData(packet);
                break;
            }
            case EXPEDITING: {
                this.taskPacket.addAllVisited(packet.header.visitedUAVs);
                break;
            }
            default: {
                System.exit(-1);
            }
        }
    }
    
    /**
     * COMMUNICATION
     * Send a message to the team
     */
    public void cellSendData(CellDataPacket packet) {
        switch(packet.getType()) {
            case ANNOUNCEMENT: {
                this.cellPacket = new CellDataPacket(this, packet);
                this.cellPacket.header.visitedUAVs.add(this);

                Set inRange = agentsInRange(this.cellPacket.payload.UAVs);
                Set visited = this.cellPacket.header.visitedUAVs;
                inRange.removeAll(visited);
                while(!inRange.isEmpty()) {
                    Iterator<UAV> it = inRange.iterator();
                    UAV recipient = it.next();
                    inRange.remove(recipient);
                    
                    recipient.cellReceiveData(this.cellPacket);
                    inRange.removeAll(visited);
                }
                
                if(packet.header.fire == this.myTask && this.target == null)
                    this.cellPacket.addBid();
                this.cellPacket.header.type = PacketType.BIDDING;

                if(!this.equals(packet.header.sender)) {
                    UAV recipient = packet.header.sender;
                    recipient.cellReceiveData(this.cellPacket);
                }
                
                break;
            }
            case AWARDING: {
                this.cellPacket = new CellDataPacket(this, packet);
                this.cellPacket.header.visitedUAVs.add(this);

                Set inRange = agentsInRange(this.cellPacket.payload.UAVs);
                Set visited = this.cellPacket.header.visitedUAVs;
                inRange.removeAll(visited);
                while(!inRange.isEmpty()) {
                    Iterator<UAV> it = inRange.iterator();
                    UAV recipient = it.next();
                    inRange.remove(recipient);
                    
                    recipient.cellReceiveData(this.cellPacket);
                    inRange.removeAll(visited);
                }
                
                this.cellPacket.header.type = PacketType.EXPEDITING;

                if(!this.equals(packet.header.sender)) {
                    UAV recipient = packet.header.sender;
                    recipient.cellReceiveData(this.cellPacket);
                }
                
                break;
            }
            default: {
                System.exit(-1);
            }
        }
    }

    /**
     * COMMUNICATION
     * Receive a message from the team
     */
    public void cellReceiveData(CellDataPacket packet) {
        switch(packet.getType()) {
            case ANNOUNCEMENT: {
                this.cellSendData(packet);
                break;
            }
            case BIDDING: {
                this.cellPacket.addAllBids(packet.payload.bids);
                this.cellPacket.addAllVisited(packet.header.visitedUAVs);
                break;
            }
            case AWARDING: {
                if(this.target == null) {
                    for(CellAward award: packet.payload.awards) {
                        if(award.bidder == this) {
                            if(award.award != null) {
                                this.target = new Double3D(award.award.x, award.award.y, z);
                                
                            }
                            break;
                        }
                    }
                }
                
                this.soldCells.addAll(packet.payload.soldCells);
                this.cellSendData(packet);
                
                break;
            }
            case EXPEDITING: {
                this.cellPacket.addAllVisited(packet.header.visitedUAVs);
                break;
            }
            default: {
                System.exit(-1);
            }
        }
    }

    /**
     * COMMUNICATION
     * Retrieve the status of all the agents in the communication range.
     * @return an array of size Ignite.tasks().size+1 where at position i you have 
     * the number of agents enrolled in task i (i.e. Ignite.tasks().get(i)). 
     * 
     * HINT: you can easily assume that the number of uncommitted agents is equal to:
     * Ignite.numUAVs - sum of all i in the returned array
     */
    public int[] retrieveAgents(Ignite ignite) {
        int[] status = new int[ignite.tasks.size()];

        for(Object obj : ignite.UAVs) { //count also this uav
            UAV other = (UAV) obj;
            if(isInCommunicationRange(new Double3D(other.x, other.y, other.z))) {
                Task task = other.myTask;
                if(task != null)
                    status[ignite.tasks.indexOf(task)]++;
            }
        }

        return status;
    }

    @Override
    public boolean equals(Object obj) {
        UAV uav = (UAV) obj;
        return uav.id == this.id;
    }

    @Override
    public String toString() { 
        return id+"UAV-"+x+","+y+","+z+"-"+action;
    } 	
}


