package  edu.rutgers.pharma;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.util.distribution.*;
//import sim.field.continuous.*;
import sim.des.*;

/** A Packaging facility reprocessing Storage unit receievs an ingredient from
    an Ingredient Storage unit (via a built-in quality check unit),
    and supplies quality-assured ingredient to the Production unit.


  */
class Packaging extends sim.des.Queue implements Reporting {

    Delay prodDelay;
    /** Models the delay taking by the QA testing at the input
	*/
    QaDelay qaDelay;

    Sink sinkProduct, sinkPackaging;

    sim.des.Queue postprocStore;
    PreprocStorage testedPackmatStore;
    sim.des.Queue dispatchStore;
    //Source packagingMachine;
    int batchSize;
    
    Packaging(SimState state, String name,
	      sim.des.Queue _postprocStore,
	      PreprocStorage _testedPackmatStore,
	      int _batchSize,
	      Resource outResource,
	       int maximum,		   
		   AbstractDistribution  prodDelayDistribution,
		   AbstractDistribution  qaDelayDistribution,
		   AbstractDistribution  faultyPortionDistribution
		   )
    {
	super(state, outResource);
	setName(name);
	setCapacity(maximum);
	postprocStore = _postprocStore ;
	testedPackmatStore = _testedPackmatStore;
	batchSize = _batchSize;
	
	qaDelay = new QaDelay(state,resource, faultyPortionDistribution);
	qaDelay.setDelayDistribution(qaDelayDistribution);
	qaDelay.setOfferPolicy​(Provider.OFFER_POLICY_FORWARD);
	
	prodDelay = new Delay(state,resource);
	prodDelay.setDelayDistribution(prodDelayDistribution);
	prodDelay.addReceiver(qaDelay);

	
	sinkProduct = new Sink(state,  _postprocStore.getTypical());
	sinkPackaging = new Sink(state, _testedPackmatStore.getTypical());


	//packagingMachine = new Source(state, outResource);
	//packagingMachine.setAutoSchedules​(false);

	addReceiver(dispatchStore);
    }

    /** This method should be called immediately after the constructor
	cteates a Packaging method. This code is put into a separate
	method so that we can create a DispatchStore object after creating
	a Packaging object
     */
    void setDispatchStore( sim.des.Queue _dispatchStore) {
	dispatchStore =  _dispatchStore;
	qaDelay.addReceiver(dispatchStore );
	qaDelay.addReceiver(this);
    }



    /** Do we have enough input materials of each kind to make a batch? */
    private boolean hasEnoughInputs() {
	return  testedPackmatStore.getAvailable()>=batchSize &&
	    postprocStore.getAvailable()>=batchSize;
    }

    int batchesStarted=0;

    
    public void step​(sim.engine.SimState state) {
	double workedUpon = prodDelay.getTotal() +  qaDelay.getTotal() + batchSize;
	boolean haveSpace = (getAvailable() + workedUpon  <=getCapacity()) ||
	    (dispatchStore.getAvailable() +  workedUpon  <= dispatchStore.getCapacity());
	
	if (haveSpace && hasEnoughInputs()) {
	    postprocStore.provide( sinkProduct, batchSize);
	    testedPackmatStore.provide( sinkPackaging, batchSize);
	    
	    //	    System.out.println("At t=" + state.schedule.getTime() + ", Packaging starts on a batch");
	    Resource batch = new CountableResource((CountableResource)getTypical(), batchSize);
	    Provider provider = null;  // why do we need it?
	    prodDelay.accept(provider, batch, batchSize, batchSize);
	    batchesStarted++;
	}
	
	//  the Queue.step() call resource offers to registered receivers
	super.step(state);
    }

    public String report() {
	return "[Packaging."+getName()+", has "+ prodDelay.getTotal() + " in the work, " +  qaDelay.getTotal() + " in QA, " + getAvailable() + " in ready storage. Packaged: faulty="+qaDelay.badResource+", good=" + qaDelay.releasedGoodResource+"]";

    }

}