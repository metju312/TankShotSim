package statistic;

import Helpers.Vector3;
import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfixedArray;
import hla.rti1516e.encoding.HLAfloat64BE;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import target.Target;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StatisticFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private StatisticFederateAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;

    //Statistic parameters:
    protected List<Target> targets = new ArrayList<>();
    protected int shotCount = 0;
    protected int hitCount = 0;
    protected int missCount = 0;

    protected InteractionClassHandle shotHandle;
    protected ParameterHandle shotPositionHandle;
    protected ParameterHandle directionHandle;
    protected ParameterHandle typeHandle;

    protected InteractionClassHandle hitHandle;
    protected ParameterHandle hitTargetIdHandle;
    protected ParameterHandle hitDirectionHandle;
    protected ParameterHandle hitTypeHandle;

    protected ObjectClassHandle targetHandle;
    protected AttributeHandle targetIdHandle;
    protected AttributeHandle targetPositionHandle;

    protected InteractionClassHandle endSimulationHandle;
    protected ParameterHandle federateNumberHandle;

    // Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        fedamb = new StatisticFederateAmbassador( this );
        rtiamb.connect( fedamb, CallbackModel.HLA_EVOKED );

        log("Stworzenie i dołączenie do Federacj");
        try
        {
            URL[] modules = new URL[]{
                    (new File("foms/HLAstandardMIM.xml")).toURI().toURL(),
                    (new File("foms/TankSim.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution( federationName, modules );
            log( "Federacja została stworzona" );
        }
        catch( hla.rti1516e.exceptions.FederationExecutionAlreadyExists exists )
        {
            log( "Federacja już istnieje" );
        }
        catch( MalformedURLException urle )
        {
            log( "Błąd podczas wyczytywania modelii FOM: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        URL[] joinModules = new URL[]{
                (new File("foms/RestaurantSoup.xml")).toURI().toURL()
        };//nie wiem od czego zależy czy dajemy dany moduł tutaj zamiast wcześniej

        rtiamb.joinFederationExecution( federateName,            // name for the federate
                "TankFederatType",   // federate type --- nie wiem po co gdzie i jak?
                federationName,     // name of federation
                joinModules );           // modules we want to add
        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();
        log( "Dołaczono do federacji jako " + federateName );

        log("Synchronizacja");
        // tworzymy punkt synchronizacji, nie jestem pewien czy
        //wszyscy federaci muszą zgłosić się do tego punktu żeby zacząć
        //czy wystarczy żeby ten jeden ogłosił że do niego doszedł
        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );
        // czekamy na to aż rti doda punkt
        while( fedamb.isAnnounced == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
        waitForUser();
        // zgłaszamy rti że doszliśmy do punktu i jesteśmy gotowi ruszyć, jeśli federat ma jakieś obliczenia które
        // które wymagają dużo czasu to powinny być przed tym (na przykład generowanie mapy terenu)
        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Osiągnięto punkt synchronizacji:  " +READY_TO_RUN+ ", oczekiwanie na pozostałych federatów" );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }


        log("Ustawienia rti");
        enableTimePolicy();
        publishAndSubscribe();
    }


    private void finalizeFederate(String federationName) throws OwnershipAcquisitionPending, InvalidResignAction, FederateOwnsAttributes, RTIinternalError, FederateNotExecutionMember, CallNotAllowedFromWithinCallback, NotConnected {
        rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
        log( "Resigned from Federation" );
        try
        {
            rtiamb.destroyFederationExecution( federationName );
            log( "Federacja została zniszczona" );
        }
        catch( FederationExecutionDoesNotExist dne )
        {
            log( "Federacja została już zniszczona" );
        }
        catch( FederatesCurrentlyJoined fcj )
        {
            log( "Fedaracja wciąż zaweira federatów" );
        }

    }

    //Metody pomocnicze RTI
    private void enableTimePolicy() throws RTIexception
    {
        HLAfloat64Interval lookahead = timeFactory.makeInterval( fedamb.federateLookahead );
        this.rtiamb.enableTimeRegulation( lookahead );
        while( fedamb.isRegulating == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
        this.rtiamb.enableTimeConstrained();
        while( fedamb.isConstrained == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
    }

    private void publishAndSubscribe() throws RTIexception
    {
        //Subskrycja na Wystrzał
        this.shotHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Shot");
        this.shotPositionHandle = rtiamb.getParameterHandle(shotHandle,"Position");
        this.directionHandle = rtiamb.getParameterHandle(shotHandle,"Direction");
        this.typeHandle = rtiamb.getParameterHandle(shotHandle,"Type");
        rtiamb.subscribeInteractionClass(shotHandle);

        //Subskrycja na Trafienie
        this.hitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Hit");
        this.hitTargetIdHandle = rtiamb.getParameterHandle(hitHandle,"TargetID");
        this.hitDirectionHandle = rtiamb.getParameterHandle(hitHandle,"HitDirection");
        this.hitTypeHandle = rtiamb.getParameterHandle(hitHandle,"Type");
        rtiamb.subscribeInteractionClass(hitHandle);

        //Subskrybcja na Cel
        this.targetHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Target");
        this.targetIdHandle = rtiamb.getAttributeHandle(targetHandle,"TargetID");
        this.targetPositionHandle = rtiamb.getAttributeHandle(targetHandle,"Position");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(targetIdHandle);
        attributes.add(targetPositionHandle);
        rtiamb.subscribeObjectClassAttributes(targetHandle, attributes);

        //Subskrycja na Koniec Symulacji
        this.endSimulationHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndSimulation");
        this.federateNumberHandle = rtiamb.getParameterHandle(endSimulationHandle,"FederateNumber");
        rtiamb.subscribeInteractionClass(endSimulationHandle);
    }

    private void advanceTime( double timestep ) throws RTIexception
    {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( time );
        while( fedamb.isAdvancing )rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
    }

    private byte[] generateTag()
    {
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }

    private HLAfloat64BE[] wrapFloatData(float... data )
    {
        int length = data.length;
        HLAfloat64BE[] array = new HLAfloat64BE[length];
        for( int i = 0 ; i < length ; ++i )
            array[i] = this.encoderFactory.createHLAfloat64BE( data[i] );

        return array;
    }

//////////////////////////////
//Faktyczne metody symulacji//


    private void run(String federateName, String federationName) throws Exception {
        initializeFederate(federateName,federationName);
        mainLoop();
        finalizeFederate(federationName);
    }

    private void mainLoop() throws RTIexception, InterruptedException{
        while (fedamb.running)
        {
            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String federateName = "Statystyka";
        try {
            new statistic.StatisticFederate().run(federateName ,"Federacja");
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    //Pomocnicze//
    private void log( String message )
    {
        System.out.println( "StatisticFederate   : " + message );
    }

    protected void logStatistics()
    {
        log("Shots: " + shotCount + ", Hits: " + hitCount + "Prob: " + hitCount/(double)shotCount);
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



    private void sleep(int time) throws InterruptedException {
        Thread.sleep(time);
    }
}

