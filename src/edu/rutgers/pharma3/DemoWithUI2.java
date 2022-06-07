package edu.rutgers.pharma3;


import java.io.*;
import java.util.*;

import sim.portrayal.grid.*;

import sim.portrayal.network.*;
import sim.portrayal.continuous.*;
import sim.engine.*;
import sim.display.*;
import sim.portrayal.simple.*;
import sim.portrayal.*;
import javax.swing.*;
import java.awt.Color;
import java.awt.*;
import sim.field.network.*;
import sim.des.portrayal.*;


import edu.rutgers.util.*;

public class DemoWithUI2 extends GUIState    {


    public Display2D display;
    public JFrame displayFrame;
	
    ContinuousPortrayal2D layoutPortrayal = new ContinuousPortrayal2D();
    NetworkPortrayal2D graphPortrayal = new NetworkPortrayal2D();

   
    public DemoWithUI2() { super(new Demo(System.currentTimeMillis())); }    
    public DemoWithUI2(SimState state) { super(state); }
    public Object getSimulationInspectedObject() { return state; }
   
    public static String getName() { return "Pharma3 Network Demo"; }


    public void start()        {
        super.start();
        setupPortrayals();
    }

    public void load(SimState state)        {
        super.load(state);
        setupPortrayals();
    }


    public void setupPortrayals()        {
	Demo example = (Demo) state;      
        
        layoutPortrayal.setField(example.field.getNodes());
        graphPortrayal.setField(example.field);
        SimpleEdgePortrayal2D edge = new DelayedEdgePortrayal(); //new ResourceEdgePortrayal(1.0);
        edge.setBaseWidth(1);
        //edge.setShape(SimpleEdgePortrayal2D.SHAPE_TRIANGLE);
        graphPortrayal.setPortrayalForAll(edge);

        //layoutPortrayal.setPortrayalForAll(new MovablePortrayal2D(new RectanglePortrayal2D(5.0, false)));        
        //SimpleEdgePortrayal2D edge = ResourceEdge.getDefaultEdgePortrayal2D(); //new SimpleEdgePortrayal2D(Color.BLUE, Color.RED, Color.BLACK, new Font("SansSerif", Font.PLAIN, 2));
        //edge.setShape(SimpleEdgePortrayal2D.SHAPE_LINE_ROUND_ENDS);

        // reschedule and repaint the displayer
        display.reset();
        display.setBackdrop(Color.white);
        display.repaint();
    }

   public void init(Controller c)        {
        super.init(c);

        DESPortrayalParameters.setImageClass(DemoWithUI2.class);

        // make the displayer
        display = new Display2D(600,600,this);
        // turn off clipping
        display.setClipping(false);
        display.attach( graphPortrayal, "Connections" );
        display.attach( layoutPortrayal, "Layout" );

        displayFrame = display.createFrame();
        displayFrame.setTitle("Pharma 3");
        c.registerFrame(displayFrame);   // register the frame so it appears in the "Display" list
        displayFrame.setVisible(true);
   }

    public void quit()        {
	super.quit();
	
	if (displayFrame!=null) displayFrame.dispose();
	displayFrame = null;
	display = null;
    }

    
    public static void main(String[] argv)   throws IOException, IllegalInputException    {

	argv = Demo.processArgv(argv);
		
        DemoWithUI2 tutorial2 = new DemoWithUI2();
        sim.display.Console c = new sim.display.Console(tutorial2);
        c.setVisible(true);
    }
    
  
}
    