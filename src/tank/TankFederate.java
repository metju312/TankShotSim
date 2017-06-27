package tank;

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
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TankFederate
{
    public int ammo = 2;
    public final double shotChance = 0.05;

    private final int reloadTime = 20;
    private final double maxSpeed = 1.0;
    private final double acceleration = 0.05;
    private final double bulletSpeed = 5.0;

    public Vector3 position= new Vector3(14.0,2.0, 1.0) ;

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private TankFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;

    protected ObjectClassHandle terrainHandle;
    protected AttributeHandle shapeHandle;

    protected ObjectClassHandle targetHandle;
    protected AttributeHandle targetIdHandle;
    protected AttributeHandle targetPositionHandle;

    protected InteractionClassHandle shotHandle;
    protected ParameterHandle shotPositionHandle;
    protected ParameterHandle directionHandle;
    protected ParameterHandle typeHandle;

    protected InteractionClassHandle endSimulationHandle;
    protected ParameterHandle federateNumberHandle;

    //protected double[][] terrain;
    protected Map<Integer,Map<Integer,Double>> terrain;

    protected ObjectInstanceHandle[][] terrainHandles;

    protected int chosenTarget=-1;

    private Vector3 speed;

    protected List<Target> targets = new ArrayList<>();

// Metody RTI

    public void initializeFederate( String federateName, String federationName) throws Exception
    {
        log("Tworzenie abasadorów i połączenia");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
        fedamb = new TankFederateAmbassador( this );
        rtiamb.connect( fedamb, CallbackModel.HLA_EVOKED );

        log("Stworzenie i dołączenie do Federacj");
        try
        {
            URL[] modules = new URL[]{
                    (new File("foms/HLAstandardMIM.xml")).toURI().toURL(),
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

    public void finalizeFederate(String federationName) throws OwnershipAcquisitionPending, InvalidResignAction, FederateOwnsAttributes, RTIinternalError, FederateNotExecutionMember, CallNotAllowedFromWithinCallback, NotConnected {
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

    private void publishAndSubscribe() throws RTIexception
    {
        //Subskrybcja na Teren
        this.terrainHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Terrain");
        this.shapeHandle = rtiamb.getAttributeHandle(terrainHandle,"Shape");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(shapeHandle);
        rtiamb.subscribeObjectClassAttributes(terrainHandle, attributes);

        //Subskrybcja na Cel
        this.targetHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Target");
        this.targetIdHandle = rtiamb.getAttributeHandle(targetHandle,"TargetID");
        this.targetPositionHandle = rtiamb.getAttributeHandle(targetHandle,"Position");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(targetIdHandle);
        attributes.add(targetPositionHandle);
        rtiamb.subscribeObjectClassAttributes(targetHandle, attributes);

        //Publikacja Wystrzału
        this.shotHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Shot");
        this.shotPositionHandle = rtiamb.getParameterHandle(shotHandle,"Position");
        this.directionHandle = rtiamb.getParameterHandle(shotHandle,"Direction");
        this.typeHandle = rtiamb.getParameterHandle(shotHandle,"Type");
        rtiamb.publishInteractionClass(shotHandle);

        //Publikacja Końca Symulacji
        this.endSimulationHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndSimulation");
        this.federateNumberHandle = rtiamb.getParameterHandle(endSimulationHandle,"FederateNumber");
        rtiamb.publishInteractionClass(endSimulationHandle);
    }

    private void advanceTime( double timestep) throws RTIexception
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
        terrain = new HashMap<>();
        terrainHandles = new ObjectInstanceHandle[50][50];
        targets = new ArrayList<>();
        speed = new Vector3(0,0,0);
        mainLoop();
        finalizeFederate(federationName);
    }

    private void mainLoop() throws RTIexception
    {
        Random generator = new Random();
        int reloadingTimer = 0;

        while( ammo>0)
        {
            if(chosenTarget<0) {
                int targetsNumber = targets.size();
                if (targetsNumber > 0)
                {
                    chosenTarget= generator.nextInt(targetsNumber);
                    log("Wybrano cel o handle = "+chosenTarget);
                }
            }
            if(chosenTarget>=0)
            {
                moveTank();
                if(reloadingTimer<=0)
                {
                    int bulletType =100+ generator.nextInt(4);
                    shotBullet(bulletType);
                    log("Wystrzelono poscisk");
                    reloadingTimer=reloadTime;
                }
                else reloadingTimer--;
            }
            /* nie usuwam bo może być potrzebne do tesotwania
            if(generator.nextDouble()<shotChance)
            {
                bulletType =100+ generator.nextInt(4);
                shotBullet(bulletType);
                log("Wystrzelono poscisk");
            }
            else
                log("Celowanie");
*/
            // 9.3 request a time advance and wait until we get it
            advanceTime( 1.0 );

            log( "Time Advanced to " + fedamb.federateTime );
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        endBulletsFederate();
    }

    private void endBulletsFederate() throws RTIexception{
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAinteger32BE federateNumberValue = encoderFactory.createHLAinteger32BE(2);
        parameters.put( federateNumberHandle, federateNumberValue.toByteArray() );
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.sendInteraction(endSimulationHandle,parameters,generateTag(),time);
    }

    private void moveTank()
    {
        Vector3 dir = targets.get(chosenTarget).getPosition().distanceFrom(position);
        dir.normalize();
        double speedNorm= speed.dotProduct(dir);
        if(speedNorm<0.0)speedNorm=0.0;
        if(speedNorm<maxSpeed)dir.timesA(acceleration+speedNorm);
        else dir.timesA(speedNorm);
        position.addVector(dir);
        position.z=getZ(position.x,position.y)+1;
        speed=dir;
        log("Czołg poruszył się na pozycję : "+position.toStirng()+" z prędkością : "+speed.norm()+" / "+speed.toStirng());
    }

    private double getZ(int x, int y)
    {
        Map<Integer, Double> mapAtX = terrain.get(x);
        Double valueAtY;
        if(mapAtX==null)return 0.0;
        else valueAtY = mapAtX.get(y);
        if(valueAtY==null)return 0.0;
        else return valueAtY;
    }

    private double getZ(double x, double y)
    {
        int leftDownX, leftDownY;
        double leftDownDistance, leftUpDistance, rightUpDistance, rightDownDistance, sum;
        double leftDownZ, leftUpZ, rightUpZ, rightDownZ;
        leftDownX = (int)x;
        leftDownY = (int)y;
        leftDownZ = getZ(leftDownX,leftDownY);
        leftUpZ = getZ(leftDownX,leftDownY+1);
        rightDownZ = getZ(leftDownX+1,leftDownY);
        rightUpZ = getZ(leftDownX+1,leftDownY+1);
        leftDownDistance = Math.sqrt(Math.pow(x-leftDownX,2)+Math.pow(y-leftDownY,2));
        leftUpDistance = Math.sqrt(Math.pow(x-leftDownX,2)+Math.pow(leftDownY+1-y,2));
        rightUpDistance = Math.sqrt(Math.pow(leftDownX+1-x,2)+Math.pow(leftDownY+1-y,2));
        rightDownDistance = Math.sqrt(Math.pow(leftDownX+1-x,2)+Math.pow(y-leftDownY,2));
        double z=Math.min(Math.min(leftDownZ,leftUpZ),Math.min(rightDownZ,rightUpZ));
        leftDownZ -= z;
        leftUpZ -= z;
        rightDownZ -= z;
        rightUpZ -= z;
        leftDownDistance = 1-leftDownDistance;
        leftUpDistance = 1-leftUpDistance;
        rightDownDistance = 1-rightDownDistance;
        rightUpDistance = 1-rightUpDistance;
        if(leftDownDistance<0)leftDownDistance=0;
        if(leftUpDistance<0)leftUpDistance=0;
        if(rightDownDistance<0)rightDownDistance=0;
        if(rightUpDistance<0)rightUpDistance=0;
        sum= leftDownDistance+leftUpDistance+rightDownDistance+rightUpDistance;
        leftDownDistance = leftDownDistance/sum;
        leftUpDistance = leftUpDistance/sum;
        rightDownDistance = rightDownDistance/sum;
        rightUpDistance = rightUpDistance/sum;
        z += leftDownZ*leftDownDistance+
                leftUpZ*leftUpDistance+
                rightDownZ*rightDownDistance+
                rightUpZ*rightUpDistance;
        return z;
    }

    private void shotBullet(int bulletType) throws RTIexception
    {
        ammo--;
        Vector3 dir = targets.get(chosenTarget).getPosition().distanceFrom(position);
        dir.normalize();
        dir.z=dir.z+0.9;//TODO: ustawić kąt strzału zależny od odległości itp
        dir.timesA(bulletSpeed);
        sendShotInteraction(bulletType,dir);
    }

    private void sendShotInteraction(int bulletType, Vector3 direction) throws RTIexception
    {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(3);
        HLAfixedArray<HLAfloat64BE> shotPositionValue = encoderFactory.createHLAfixedArray(wrapFloatData(position.toFloatArray()));
        HLAfixedArray<HLAfloat64BE> DirectionValue = encoderFactory.createHLAfixedArray(wrapFloatData(direction.toFloatArray()));
        HLAinteger32BE typeValue = encoderFactory.createHLAinteger32BE( bulletType );
        parameters.put( shotPositionHandle, shotPositionValue.toByteArray() );
        parameters.put( directionHandle, DirectionValue.toByteArray() );
        parameters.put( typeHandle, typeValue.toByteArray() );
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.sendInteraction(shotHandle,parameters,generateTag(),time);
    }

    public static void main(String[] args)
    {
        String federateName = "Rudy";
        try
        {
            // run the zzzexample federate
            new TankFederate().run( federateName ,"Federacja");
        }
        catch( Exception rtie )
        {
            // an exception occurred, just log the information and exit
            rtie.printStackTrace();
        }
    }




    //Pomocnicze//
    private void log( String message )
    {
        System.out.println( "Federat czołgu   : " + message );
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

    private HLAfloat64BE[] wrapFloatData( float... data )
    {
        int length = data.length;
        HLAfloat64BE[] array = new HLAfloat64BE[length];
        for( int i = 0 ; i < length ; ++i )
            array[i] = this.encoderFactory.createHLAfloat64BE( data[i] );

        return array;
    }

    private byte[] generateTag()
    {
        return ("(timestamp) "+System.currentTimeMillis()).getBytes();
    }

    private void printTerrain(int maxX, int maxY)
    {
        for(int y =0; y <maxY;y++)
        {
            StringBuilder builder = new StringBuilder();
            for (int x = 0; x < maxX; x++)
            {
                builder.append(" "+getZ(x,y));
            }
            log(builder.toString());
        }
    }
}
