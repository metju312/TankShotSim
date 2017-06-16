package target;

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
import statistic.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";


    private RTIambassador rtiamb;
    private TargetsFederateAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;

    public final double generateTargetChance = 0.05;

    private int maxTargetId=0;

    protected List<Target> targets = new ArrayList<>();

    protected InteractionClassHandle hitHandle;
    protected ParameterHandle hitTargetIdHandle;

    protected ObjectClassHandle targetHandle;
    protected AttributeHandle targetIdHandle;
    protected AttributeHandle targetPositionHandle;

    // Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        fedamb = new TargetsFederateAmbassador( this );
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
        this.hitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Hit");
        this.targetHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Target");

        this.hitTargetIdHandle = rtiamb.getParameterHandle(hitHandle,"TargetID");

        this.targetIdHandle = rtiamb.getAttributeHandle(targetHandle,"TargetID");
        this.targetPositionHandle = rtiamb.getAttributeHandle(targetHandle,"Position");

        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(targetIdHandle);
        attributes.add(targetPositionHandle);

        rtiamb.publishObjectClassAttributes(targetHandle,attributes);

        rtiamb.subscribeInteractionClass(hitHandle);
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
        Random generator = new Random();
        while (fedamb.running)
        {
            if(generator.nextDouble() < generateTargetChance){
                generateTarget();
            }
            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime);

            for (Target target:targets)
            {
                if(!target.isRegistered)registerTargetObject(target);
                moveTarget(target);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void moveTarget(Target target)
    {

    }

    private void generateTarget() throws SaveInProgress, AttributeNotDefined, ObjectInstanceNotKnown, RestoreInProgress, NotConnected, ObjectClassNotDefined, InvalidLogicalTime, AttributeNotOwned, FederateNotExecutionMember, RTIinternalError, ObjectClassNotPublished {
        Random generator = new Random();
        int type = generator.nextInt(5);
        switch (type)
        {
            case 0:
            break;
            case 1:
                //break;
            case 2:
                //break;
            case 3:
                //break;
            case 4:
                //break;
            case 5:
                Target target = new Target(++maxTargetId,new Vector3(0.0,0.0,0.0));
                targets.add(target);
                log("Stworzono cel o id: "+maxTargetId);
                break;
        }
    }

    private void registerTargetObject(Target targetObject) throws SaveInProgress, RestoreInProgress, ObjectClassNotPublished, ObjectClassNotDefined, FederateNotExecutionMember, RTIinternalError, NotConnected, AttributeNotDefined, ObjectInstanceNotKnown, AttributeNotOwned, InvalidLogicalTime {
        targetObject.setRtiInstance(rtiamb.registerObjectInstance(targetHandle));
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(2);
        HLAinteger32BE idValue = encoderFactory.createHLAinteger32BE(targetObject.getId());
        HLAfixedArray<HLAfloat64BE> positionValue = encoderFactory.createHLAfixedArray(wrapFloatData(targetObject.getPosition().toFloatArray()));
        attributes.put(targetIdHandle,idValue.toByteArray());
        attributes.put(targetPositionHandle,positionValue.toByteArray());
        rtiamb.updateAttributeValues(targetObject.getRtiInstance(),attributes,generateTag(),timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        targetObject.isRegistered=true;
        log( "Dodano obiekt celu, handle=" + targetObject.getRtiInstance());
    }

    public static void main(String[] args) {
        String federateName = "Cele";
        try {
            new TargetsFederate().run(federateName ,"Federacja");
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    //Pomocnicze//
    private void log( String message )
    {
        System.out.println( "StatisticFederate   : " + message );
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


