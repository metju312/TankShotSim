package bullet;


import hla.rti.*;
import hla.rti.AttributeHandleSet;
import hla.rti.FederatesCurrentlyJoined;
import hla.rti.FederationExecutionAlreadyExists;
import hla.rti.LogicalTime;
import hla.rti.LogicalTimeInterval;
import hla.rti.RTIambassador;
import hla.rti.RTIexception;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import hla.rti1516e.*;
import hla.rti1516e.exceptions.*;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class BulletsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private BulletsFederateAmbassador fedamb;
    private final double timeStep           = 10.0;
    private int bulletHlaHandle;
    private int reloadTime = 6000;
    private boolean shouldShoot = true;
    private int actualPosition = 0;
    private int actualIdBullet = 1;

// Metody RTI
    private void initializeFederate(String federateName, String federationName) throws Exception{
        log("Tworzenie abasadorów i połączenia");
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        log("Stworzenie i dołączenie do Federacj");
        try
        {
            File fom = new File( "tankfed.fed" );
            rtiamb.createFederationExecution( "ExampleFederation",
                    fom.toURI().toURL() );
            log( "Federacja została stworzona" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Federacja już istnieje" );
        }
        catch( MalformedURLException urle )
        {
            log( "Błąd podczas wczytywania modelii FOM: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new BulletsFederateAmbassador();
        rtiamb.joinFederationExecution( "BulletsFederate", "ExampleFederation", fedamb );
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

    }


    private void finalizeFederate(String federationName) {
//        rtiamb.resignFederationExecution( hla.rti1516e.ResignAction.DELETE_OBJECTS );
//        log( "Resigned from Federation" );
//        try
//        {
//            rtiamb.destroyFederationExecution( federationName );
//            log( "Federacja została zniszczona" );
//        }
//        catch( hla.rti1516e.exceptions.FederationExecutionDoesNotExist dne )
//        {
//            log( "Federacja została już zniszczona" );
//        }
//        catch( hla.rti1516e.exceptions.FederatesCurrentlyJoined fcj )
//        {
//            log( "Fedaracja wciąż zaweira federatów" );
//        }
    }

//Metody pomocnicze RTI

    private void calculatePosition() {
        actualPosition++;
    }

    private void sleep(int time) throws InterruptedException {
        Thread.sleep(time);
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

    private void publishAndSubscribe() throws RTIexception {
        int bulletHandle = rtiamb.getObjectClassHandle("ObjectRoot.Bullet");
        int idbulletHandle = rtiamb.getAttributeHandle( "idbullet", bulletHandle );
        int positionHandle = rtiamb.getAttributeHandle( "position", bulletHandle );

        AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idbulletHandle);
        attributes.add(positionHandle);

        rtiamb.publishObjectClass(bulletHandle, attributes);

//        była interakcja
//        int addProductHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.AddBullet" );
//        rtiamb.publishInteractionClass(addProductHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        SuppliedAttributes attributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int bulletHandle = rtiamb.getObjectClass(bulletHlaHandle);
        int positionHandle = rtiamb.getAttributeHandle( "position", bulletHandle );
        calculatePosition();
        byte[] positionValue = EncodingHelpers.encodeInt(actualPosition);

        attributes.add(positionHandle, positionValue);
        LogicalTime logicalTime = convertTime( time );

        log("PociskId: " + actualIdBullet + " , pozycja: " + actualPosition);
        rtiamb.updateAttributeValues(bulletHlaHandle, attributes, "actualize position".getBytes(), logicalTime );
    }

    private void registerBulletObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Bullet");
        this.bulletHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void sendInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();
        //byte[] quantity = EncodingHelpers.encodeInt(random.nextInt(10) + 1);
        byte[] quantity = EncodingHelpers.encodeInt(6);

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddBullet");
        int speedHandle = rtiamb.getParameterHandle( "speed", interactionHandle );

        parameters.add(speedHandle, quantity);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void advanceTime( double timestep ) throws RTIexception
    {
        log("requesting time advance for: " + timestep);
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( newTime );
        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private double randomTime() {
        Random r = new Random();
        return 1 +(9 * r.nextDouble());
    }

    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

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

    private void mainLoop() throws RTIexception, InterruptedException{
        int krok = 0;
        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
            advanceTime( 1.0 );
            //advanceTime(timeToAdvance);
            if(shouldShoot){
                registerBulletObject();
                shouldShoot=false;
            }
            //TODO tu jakiś inny czas trzeba dać
            updateHLAObject(timeToAdvance);
            sleep(1000);
            rtiamb.tick();
            krok++;
            if(krok%8==0){
                //TODO zniszczyć poprzedni obiekt
                //shouldShoot=true;
            }
        }
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
}
