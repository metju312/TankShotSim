package bullet;



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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

public class BulletsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private BulletsFederateAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;


    private boolean bulletInTheAir = false;
    private boolean isRegistered = false;
    private Vector3 bulletPosition;
    private Vector3 bulletVelocity;
    private int bulletId = 1;

    private double bulletCaliber = 125;//mm = 0.0125m
    private double bulletMass = 6.55;//kg

    protected Vector3 wind;
    protected double temperature;
    protected double pressure;

    protected boolean shouldStopRunning = false;


    protected ObjectClassHandle bulletHandle;
    protected AttributeHandle bulletIdHandle;
    protected AttributeHandle bulletPositionHandle;
    protected AttributeHandle bulletTypeHandle;

    protected ObjectClassHandle atmosphereHandle;
    protected AttributeHandle windHandle;
    protected AttributeHandle temperatureHandle;
    protected AttributeHandle pressureHandle;

    protected InteractionClassHandle shotHandle;
    protected ParameterHandle shotPositionHandle;
    protected ParameterHandle directionHandle;
    protected ParameterHandle typeHandle;

    protected InteractionClassHandle hitHandle;
    protected ParameterHandle hitTargetIdHandle;
    protected ParameterHandle hitDirectionHandle;
    protected ParameterHandle hitTypeHandle;

    protected InteractionClassHandle endSimulationHandle;
    protected ParameterHandle federateNumberHandle;

    protected ObjectInstanceHandle bulletInstanceHandle;
    protected ObjectInstanceHandle atmosphereInstanceHandle;


// Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = hla.rti1516e.RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        fedamb = new BulletsFederateAmbassador( this );
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
        //Publikacja Pocisku
        this.bulletHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Bullet");
        this.bulletIdHandle = rtiamb.getAttributeHandle(bulletHandle,"BulletID");
        this.bulletPositionHandle = rtiamb.getAttributeHandle(bulletHandle,"Position");
        this.bulletTypeHandle = rtiamb.getAttributeHandle(bulletHandle,"Type");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(bulletIdHandle);
        attributes.add(bulletPositionHandle);
        attributes.add(bulletTypeHandle);
        rtiamb.publishObjectClassAttributes(bulletHandle,attributes);

        //Subskrycja na Atmosferę
        this.atmosphereHandle  = rtiamb.getObjectClassHandle("HLAobjectRoot.Atmosphere");
        this.windHandle = rtiamb.getAttributeHandle(atmosphereHandle, "WindDirectory");
        this.temperatureHandle = rtiamb.getAttributeHandle(atmosphereHandle, "Temperature");
        this.pressureHandle = rtiamb.getAttributeHandle(atmosphereHandle, "Pressure");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(windHandle);
        attributes.add(temperatureHandle);
        attributes.add(pressureHandle);
        rtiamb.subscribeObjectClassAttributes(atmosphereHandle,attributes);

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

        //Subskrycja na Koniec Symulacji
        this.endSimulationHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndSimulation");
        this.federateNumberHandle = rtiamb.getParameterHandle(endSimulationHandle,"FederateNumber");
        rtiamb.subscribeInteractionClass(endSimulationHandle);

        //Publikacja na Koniec Symulacji
        rtiamb.publishInteractionClass(endSimulationHandle);
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

    private HLAfloat64BE[] wrapFloatData( float... data )
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
        int krok = 0;
        Random generator = new Random();
        while (fedamb.running)
        {

            if(bulletInTheAir)
            {
                if(!isRegistered)registerBulletObject();
                moveBullet();
                krok++;
            }
            else if(isRegistered)deleteBullet();
            if(krok>10)
            {
                krok=0;
                destroyBullet();}
            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime );
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        endTargetsFederate();
    }

    private void registerBulletObject() throws SaveInProgress, RestoreInProgress, ObjectClassNotPublished, ObjectClassNotDefined, FederateNotExecutionMember, RTIinternalError, NotConnected, ObjectInstanceNotKnown, AttributeNotDefined, AttributeNotOwned {
        bulletInstanceHandle= rtiamb.registerObjectInstance(bulletHandle);
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(2);
        HLAinteger32BE idValue = encoderFactory.createHLAinteger32BE(bulletId);
        HLAfixedArray<HLAfloat64BE> positionValue = encoderFactory.createHLAfixedArray(wrapFloatData(bulletPosition.toFloatArray()));
        attributes.put(bulletIdHandle,idValue.toByteArray());
        attributes.put(bulletPositionHandle,positionValue.toByteArray());
        rtiamb.updateAttributeValues(bulletInstanceHandle,attributes,generateTag());
        log( "Dodano obiekt pocisk, handle=" + bulletInstanceHandle );
        isRegistered=true;
    }

    protected void shotBullet(Vector3 pos, Vector3 dir, int type) throws RestoreInProgress, ObjectClassNotDefined, ObjectClassNotPublished, SaveInProgress, FederateNotExecutionMember, RTIinternalError, NotConnected, ObjectInstanceNotKnown, AttributeNotDefined, AttributeNotOwned {
        if(!bulletInTheAir)
        {
            bulletInTheAir = true;
            bulletPosition=pos;
            bulletVelocity = dir;
            log("Wystrzelono pocisk z pozycji "+pos.toStirng()+" w kierunku "+ dir.toStirng());
        }
    }

    private void updateBulletPosition() throws NotConnected, FederateNotExecutionMember, ObjectInstanceNotKnown, RestoreInProgress, AttributeNotOwned, AttributeNotDefined, SaveInProgress, RTIinternalError {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(2);
        HLAfixedArray<HLAfloat64BE> bulletPositionValue = encoderFactory.createHLAfixedArray(wrapFloatData(bulletPosition.toFloatArray()));
        HLAinteger32BE idValue = encoderFactory.createHLAinteger32BE(bulletId);
        attributes.put(bulletIdHandle,idValue.toByteArray());
        attributes.put(bulletPositionHandle,bulletPositionValue.toByteArray());
        rtiamb.updateAttributeValues(bulletInstanceHandle,attributes,generateTag());
        log( "Zaktualizowano pozycje obiektu pocisku, handle=" + bulletInstanceHandle );
    }

    private void moveBullet() throws ObjectInstanceNotKnown, RestoreInProgress, AttributeNotOwned, AttributeNotDefined, SaveInProgress, FederateNotExecutionMember, RTIinternalError, NotConnected
    {
        bulletVelocity.addVector(new Vector3(0.0,0.0,-0.980665));//0.98 oznacza że jedna jednostka odległości to 10 metrów, a jedna jednostka czasu to 1 s.
        Vector3 dragAcceleration = new Vector3(-bulletVelocity.x,-bulletVelocity.y,-bulletVelocity.z);
        dragAcceleration.normalize();
        dragAcceleration.timesA(calculateDragForce()/bulletMass);
        bulletVelocity.addVector(dragAcceleration);
        bulletPosition.addVector(bulletVelocity);
        updateBulletPosition();
        log("pocisk poruszył sie na pozycje "+ bulletPosition.toStirng()+" z prędkością = "+bulletVelocity.norm());
    }

    public void destroyBullet()throws RTIexception
    {
        bulletInTheAir=false;
        if(shouldStopRunning){
            fedamb.running = false;
        }
    }

    private void endTargetsFederate() throws RTIexception{
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAinteger32BE federateNumberValue = encoderFactory.createHLAinteger32BE(3);
        parameters.put( federateNumberHandle, federateNumberValue.toByteArray() );
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.sendInteraction(endSimulationHandle,parameters,generateTag(),time);
    }

    private void deleteBullet() throws ObjectInstanceNotKnown, RestoreInProgress, DeletePrivilegeNotHeld, SaveInProgress, FederateNotExecutionMember, RTIinternalError, NotConnected {
        rtiamb.deleteObjectInstance( bulletInstanceHandle, generateTag() );
        isRegistered=false;
        bulletId++;
        log("pocisk zakończył swój lot");
    }

    private double calculateDragForce()
    {
        double speedSquare = bulletVelocity.distanceFrom(wind).norm();
        speedSquare*=speedSquare;
        double density = 100*pressure/(287.05*(temperature+273.15));
        double area = Math.PI*bulletCaliber*bulletCaliber/400000000;
        double dragCoefficient = 0.295;
        return 0.5*density*speedSquare*area*dragCoefficient;
    }

    public static void main(String[] args) {
        String federateName = "Pociski";
        try {
            new BulletsFederate().run(federateName ,"Federacja");
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

//Pomocnicze//
    private void log( String message )
    {
        System.out.println( "BulletsFederate   : " + message );
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
