package environment;


import hla.rti.*;
import hla.rti.FederationExecutionAlreadyExists;
import hla.rti.RTIexception;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import hla.rti1516e.exceptions.*;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Random;

public class EnvironmentFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private EnvironmentFederateAmbassador fedamb;
    private final double timeStep           = 80.0;
    private int stock                       = 10;
    private int environmentHlaHandle;

// Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        log("Stworzenie i dołączenie do Federacji");
        try
        {
            File fom = new File( "tankfed.fed" );
            rtiamb.createFederationExecution( federationName,
                    fom.toURI().toURL() );
            log( "Federacja została stworzona" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Federacja już istnieje" );
        }
        catch( MalformedURLException urle )
        {
            log( "Błąd podczas wyczytywania modelii FOM: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new EnvironmentFederateAmbassador();
        rtiamb.joinFederationExecution( federateName, federationName, fedamb );
        log( "Dołaczono do federacji jako " + federateName);

        log("Synchronizacja");
        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );

        while( fedamb.isAnnounced == false )
        {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Osiągnięto punkt synchronizacji: " +READY_TO_RUN+ ", oczekiwanie na pozostałych federatów..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.tick();
        }

        log("Ustawienia rti");
        enableTimePolicy();

        publishAndSubscribe();

        registerEnvironmentObject();

    }


    private void finalizeFederate(String federationName) {
//        rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
//        log( "Resigned from Federation" );
//        try
//        {
//            rtiamb.destroyFederationExecution( federationName );
//            log( "Federacja została zniszczona" );
//        }
//        catch( FederationExecutionDoesNotExist dne )
//        {
//            log( "Federacja została już zniszczona" );
//        }
//        catch( FederatesCurrentlyJoined fcj )
//        {
//            log( "Fedaracja wciąż zaweira federatów" );
//        }
    }

//Metody pomocnicze RTI
    public void addToStock(int qty) {
        this.stock += qty;

        log("Added "+ qty + " at time: "+ fedamb.federateTime +", current stock: " + this.stock);
    }

    private void registerEnvironmentObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Environment");
        this.environmentHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        SuppliedAttributes attributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(environmentHlaHandle);
        int stockHandle = rtiamb.getAttributeHandle( "stock", classHandle );
        byte[] stockValue = EncodingHelpers.encodeInt(stock);

        attributes.add(stockHandle, stockValue);
        LogicalTime logicalTime = convertTime( time );
        rtiamb.updateAttributeValues(environmentHlaHandle, attributes, "actualize stock".getBytes(), logicalTime );
    }

    private void advanceTime( double timeToAdvance ) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( timeToAdvance );
        rtiamb.timeAdvanceRequest( newTime );

        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Environment");
        int stockHandle    = rtiamb.getAttributeHandle( "stock", classHandle );

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add( stockHandle );

        rtiamb.publishObjectClass(classHandle, attributes);

        int addBulletHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.AddBullet" );
        fedamb.addBulletHandle = addBulletHandle;
        rtiamb.subscribeInteractionClass( addBulletHandle );
    }

    private void enableTimePolicy() throws RTIexception
    {
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        this.rtiamb.enableTimeRegulation( currentTime, lookahead );

        while( fedamb.isRegulating == false )
        {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();

        while( fedamb.isConstrained == false )
        {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval( time );
    }

//////////////////////////////
//Faktyczne metody symulacji//

    private void run(String federateName, String federationName) throws Exception {
        initializeFederate(federateName,federationName);
        mainLoop();
        finalizeFederate(federationName);
    }

    private void mainLoop() throws RTIexception {
        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
            advanceTime(timeToAdvance);

            if(fedamb.externalEvents.size() > 0) {
                Collections.sort(fedamb.externalEvents , new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case ADD:
                            this.addToStock(externalEvent.getQty());
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Updating stock at time: " + timeToAdvance);
                updateHLAObject(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            rtiamb.tick();
        }
    }

    public static void main(String[] args) {
        String federateName = "Otoczenie";
        try {
            new EnvironmentFederate().run(federateName, "Federacja");
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

//Pomocnicze//
    private void log( String message )
    {
        System.out.println( "EnvironmentFederate   : " + message );
    }

    private void waitForUser()
    {
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try
        {
            reader.readLine();
        }
        catch( Exception e )
        {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }
}
