package environment;


import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class EnvironmentFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private EnvironmentFederateAmbassador fedamb;
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;


    protected ObjectClassHandle atmosphereHandle;
    protected AttributeHandle windDirectoryHandle;
    protected AttributeHandle temperatureHandle;
    protected AttributeHandle pressureHandle;

    protected ObjectClassHandle terrainHandle;
    protected AttributeHandle shapeHandle;

    protected ObjectClassHandle bulletHandle;
    protected AttributeHandle bulletIdHandle;
    protected AttributeHandle bulletPositionHandle;

// Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        fedamb = new EnvironmentFederateAmbassador( this );
        rtiamb.connect( fedamb, CallbackModel.HLA_EVOKED );

        log("Stworzenie i dołączenie do Federacji");
        try
        {
            URL[] modules = new URL[]{
                    (new File("foms/TankSim.xml")).toURI().toURL(),
                    (new File("foms/RestaurantFood.xml")).toURI().toURL(),
                    (new File("foms/RestaurantDrinks.xml")).toURI().toURL()
            };

            rtiamb.createFederationExecution( federationName, modules );
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

        URL[] joinModules = new URL[]{
                (new File("foms/RestaurantSoup.xml")).toURI().toURL()
        };

        rtiamb.joinFederationExecution( federateName,            // name for the federate
                "EnvironmentFederatType",   // federate type --- nie wiem po co gdzie i jak?
                federationName,     // name of federation
                joinModules );           // modules we want to add
        this.timeFactory = (HLAfloat64TimeFactory)rtiamb.getTimeFactory();
        log( "Dołaczono do federacji jako " + federateName );


        log("Synchronizacja");
        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );

        while( fedamb.isAnnounced == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Osiągnięto punkt synchronizacji: " +READY_TO_RUN+ ", oczekiwanie na pozostałych federatów..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }

        log("Ustawienia rti");
        enableTimePolicy();

        publishAndSubscribe();

        //registerAtmosphereObject();
        registerTerrainObject();
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

    private void enableTimePolicy() throws Exception
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

    private void registerTerrainObject() throws RTIexception {
        //TODO
//        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Environment");
//        this.environmentHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        //TODO
//        SuppliedAttributes attributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
//
//        int classHandle = rtiamb.getObjectClass(environmentHlaHandle);
//        int stockHandle = rtiamb.getAttributeHandle( "stock", classHandle );
//        byte[] stockValue = EncodingHelpers.encodeInt(stock);
//
//        attributes.add(stockHandle, stockValue);
//        LogicalTime logicalTime = convertTime( time );
//        rtiamb.updateAttributeValues(environmentHlaHandle, attributes, "actualize stock".getBytes(), logicalTime );
    }

    private void publishAndSubscribe() throws RTIexception {
        this.atmosphereHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Atmosphere");
        this.windDirectoryHandle = rtiamb.getAttributeHandle(atmosphereHandle,"WindDirectory");
        this.temperatureHandle = rtiamb.getAttributeHandle(atmosphereHandle,"Temperature");
        this.pressureHandle = rtiamb.getAttributeHandle(atmosphereHandle,"Pressure");

        this.terrainHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Terrain");
        this.shapeHandle = rtiamb.getAttributeHandle(terrainHandle,"Shape");

        this.bulletHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Bullet");
        this.bulletIdHandle = rtiamb.getAttributeHandle(bulletHandle,"BulletID");
        this.bulletPositionHandle = rtiamb.getAttributeHandle(bulletHandle,"Position");

        // Subskrypcja na Pocisk
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(bulletIdHandle);
        attributes.add(bulletPositionHandle);
        rtiamb.subscribeObjectClassAttributes(bulletHandle, attributes);

        // Publikacja na Teren
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(shapeHandle);
        rtiamb.publishObjectClassAttributes(terrainHandle, attributes);

        // Publikacja na Atmosfere
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(windDirectoryHandle);
        attributes.add(temperatureHandle);
        attributes.add(pressureHandle);
        rtiamb.publishObjectClassAttributes(atmosphereHandle, attributes);

        //TODO Publikacja na trafienie
        //rtiamb.publishInteractionClass(trafienie);

    }

    private void advanceTime( double timestep ) throws RTIexception
    {
        // request the advance
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( time );

        // wait for the time advance to be granted. ticking will tell the
        // LRC to start delivering callbacks to the federate
        while( fedamb.isAdvancing )
        {
            rtiamb.evokeMultipleCallbacks( 0.1, 0.2 );
        }
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

            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime );
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
