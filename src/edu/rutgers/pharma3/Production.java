package  edu.rutgers.pharma3;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

import edu.rutgers.util.*;

/** A Production plant receives QA-inspected ingredients pushed to it from upstream
    suppliers, puts them through production and QA delays, and pushes the output 
    to a specified Receiver.

    <p>Sample usage:
    <ol>

    <li>Create a Production element: p=new Production(...);

    <li>Tell the Production element where to push the finished product:
    p.setQaReceiver(Receiver _rcv).

    <li>Ensure that upstream suppliers will push raw materials to each p.getEntrance(j)

    <li>Schedule the production element, so that it will know to initiate production daily.
    </ol>



  */
public class Production // extends sim.des.Queue
    implements Reporting,	       Steppable, Named
{


    /** Represents the storage of input materials (in Batches). They are already QA-tested by previous
	stages of the chain. These Queues are not scheduled; instead, Production.step() pulls
	stuff from them (by calling Queue.provide(..) when needed. 
    */
    sim.des.Queue[] inputStore;
    public sim.des.Queue[] getInputStore() { return inputStore;}
    
    public static class ProdDelay extends Delay implements Reporting {
	/** Total batches started */
	int batchCnt=0;
	public int getBatchCnt() { return batchCnt; }
	/** Total pills started */
	double totalStarted=0;
        public double getTotalStarted() { return totalStarted; }

	ProdDelay(SimState state,       Batch resource) {
	    super(state, resource);
	}
	public boolean accept(Provider provider, Resource batch, double atLeast, double atMost) {
	    double amt = ((Batch)batch).getContentAmount();
	    //if (Demo.verbose) System.out.println("ProdDelay accepts batch of " + amt);
	    batchCnt++;
	    totalStarted+=amt;
	    return super.accept( provider, batch, atLeast, atMost);
	}
	public String hasBatches() {
	    String s = "" + (long)getTotal();
	    if (getAvailable()>0) s += "+"+(long)getAvailable();
	    return s;
	}
	       
	public String report() {
	    return "[Production line ("+getTypical().getName()+"): accepted " +  batchCnt+" ba, totaling " + (long)totalStarted+"]";
	}
	    
    }
    
    ProdDelay prodDelay;
    /** Models the delay taken by the QA testing at the output	*/
    QaDelay qaDelay;


    public ProdDelay getProdDelay() { return prodDelay; }
    public QaDelay getQaDelay() { return qaDelay; }


    /** How many units of each input need to be taken to start cooking a batch? */
    final double[] inBatchSizes;
    /** How big is the output batch? */
    final double outBatchSize;

    /** The maximum number of batches that can be started each day */
    final int batchesPerDay;

    /** Where inputs come from */
    //final PreprocStorage[] preprocStore;
    //final sim.des.Queue postprocStore;

    /** Dummy receivers used for the consumption of ingredients, and metering */
    final MSink[] sink;

    /** What is the "entry point" for input No. j? */
    Receiver getEntrance(int j) {
	return inputStore[j];
    }


    final Batch outResource; 
    
    /** @param inResource Inputs (e.g. API and excipient). Each of them is either a Batch or CountableResource
	@param outResource batches of output (e.g. bulk drug)
     */

    Production(SimState state, String name, Config config,
	       Resource[] inResources,
	       Batch _outResource ) throws IllegalInputException
    {
	//super(state, outResource);
	outResource = _outResource;
	setName(name);
	ParaSet para = config.get(name);
	if (para==null) throw new  IllegalInputException("No config parameters specified for element named '" + name +"'");
	//setCapacity(para.getDouble("capacity"));

	// Storage for input ingredients
	inputStore = new sim.des.Queue[inResources.length];
	for(int j=0; j<inputStore.length; j++) {
	    inputStore[j] = new sim.des.Queue(state,inResources[j]);
	    inputStore[j].setName(getName() + "/Input store for " + inResources[j].getName());
	}
	
	inBatchSizes = para.getDoubles("inBatch");
	if (inBatchSizes.length!=inputStore.length) throw new  IllegalInputException("Mismatch of the number of inputs for "+getName()+": given " + inputStore.length + " resources ("+Util.joinNonBlank(";",inputStore)+"), but " + inBatchSizes.length + " input batch sizes");

	outBatchSize = para.getDouble("batch");
	batchesPerDay = (int)para.getLong("batchesPerDay");

	qaDelay = QaDelay.mkQaDelay( para, state, outResource);
	
	prodDelay = new ProdDelay(state,outResource);
	prodDelay.setDelayDistribution(para.getDistribution("prodDelay",state.random));				       
	prodDelay.addReceiver(qaDelay);

	if (qaDelay.reworkProb >0) {
	    qaDelay.setRework( prodDelay);
	}
	
	sink = new MSink[inputStore.length];
	for(int j=0; j<sink.length; j++) {
	    sink[j] = new MSink(state,inputStore[j].getTypical());
	}
	 
    }

    /** Sets the destination for the product that has passed the QA. This
	should be called after the constructor has returned, and before
	the simulation starts.
       @param _rcv The place to which good stuff goes after QA
     */
    void setQaReceiver(Receiver _rcv) {
	qaDelay.addReceiver( _rcv);
    }

    /** Do we have enough input materials of each kind to make a batch? 
	FIXME: we have a simplifying assumption that all batches are same size.
     */
    private boolean hasEnoughInputs() {
	for(int j=0; j<inBatchSizes.length; j++) {
	    Provider p = inputStore[j];
	    if (p.getTypical() instanceof Batch) {
		return (p.getAvailable()>0);
	    } else if (p.getTypical()  instanceof CountableResource) {
		return p.getAvailable()>=inBatchSizes[j];
	    } else throw new IllegalArgumentException("Wrong input resource type");
	}
	return true;
    }


    int batchesStarted=0;
    double everStarted = 0;

    public double getEverStarted() { return everStarted; }
    
    public void step​(SimState state) {
	// FIXME: should stop working if the production plan has been fulfilled
	//double haveNow = getAvailable() + prodDelay.getTotal() +	    qaDelay.getTotal();
	//if (haveNow  + outBatchSize < getCapacity() &&

	if (!hasEnoughInputs()) {
	    if (Demo.verbose)  System.out.println("At t=" + state.schedule.getTime() + ", Production of "+ prodDelay.getTypical()+" is starved. Input stores: " +
			       reportInputs(true));
	    return;
	}
	
	for(int nb=0; nb<batchesPerDay && hasEnoughInputs(); nb++) {

	    
	    for(int j=0; j<inBatchSizes.length; j++) {

		Provider p = inputStore[j];
		boolean z;
		//System.out.println("Available ("+p.getTypical()+")=" + p.getAvailable());
		if (p.getTypical() instanceof Batch) {
		    z = p.provide(sink[j], 1);
		} else if (p.getTypical() instanceof CountableResource) {
		    z = p.provide(sink[j], inBatchSizes[j]);		    
		} else throw new IllegalArgumentException("Wrong input resource type");
		if (!z) throw new IllegalArgumentException("Broken sink? Accept() fails!");
		if (sink[j].lastConsumed != inBatchSizes[j]) {
		    String msg = "Batch size mismatch on sink["+j+"]=" +
			sink[j] +": have " + sink[j].lastConsumed+", expected " + inBatchSizes[j];
		    throw new IllegalArgumentException(msg);
		}
	    		
	    }
	    
	    if (Demo.verbose) System.out.println("At t=" + state.schedule.getTime() + ", Production starts on a batch; still available inputs="+ reportInputs() +"; in works=" +	    prodDelay.getTotal()+"+"+prodDelay.getAvailable());
	    Batch onTheTruck = outResource.mkNewLot(outBatchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, onTheTruck, 1, 1);
	    batchesStarted++;
	    everStarted += outBatchSize;
	}
		
	//  the Queue.step() call resource offers to registered receivers
	//super.step(state);
    }

    private String reportInputs(boolean showBatchSize) {
	Vector<String> v= new Vector<>();
	int j=0;
	for(sim.des.Queue input: inputStore) {	    
	    String s = input.getTypical().getName() +":" +  input.getAvailable();
	    s += " batches";
	    //if (showBatchSize) s += "/" + inBatchSizes[j];
	    v.add(s);
	    j++;
	}
	return "[" + String.join(", ",v) + "]";
    }

    private String reportInputs() {
	return  reportInputs(false);
    }


    public double getDiscarded() {
	return qaDelay.badResource;
    }


    public double getReleased() {
	return qaDelay.releasedGoodResource;
    }

    public String report() {
	
	String s = "[" + cname()+"."+getName()+"; stored inputs=("+ reportInputs() +"). "+
	    "Ever started: "+(long)everStarted + " ("+batchesStarted+" ba)";
	if (qaDelay.reworkProb>0) s += " + (rework="+qaDelay.reworkResource+")";
	s += " = ("+
	    "in prod=" +   prodDelay.hasBatches() +
	    " ba; in QA= " +  qaDelay.hasBatches() +
	    " ba; discarded="+(long)qaDelay.badResource  +
	    " ("+qaDelay.badBatches+" ba)";
	if (qaDelay.reworkProb>0) s+= "; rework="+(long)qaDelay.reworkResource +
				      " ("+qaDelay.reworkBatches+" ba)";

	s += "; good=" + (long)qaDelay.releasedGoodResource+" ("+qaDelay.releasedBatches+" ba))]";
	s += "\n" + prodDelay.report();
	return s;

    }

    String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }    
    public void reset(SimState state)     	{ }  //{ 	clear();    	}
}
