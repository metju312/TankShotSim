package environment;


import Helpers.Vector3;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.*;
import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfixedArray;
import hla.rti1516e.encoding.HLAfloat64BE;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateOwnsAttributes;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.ObjectClassNotDefined;
import hla.rti1516e.exceptions.ObjectClassNotPublished;
import hla.rti1516e.exceptions.OwnershipAcquisitionPending;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
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
import java.util.Map;
import java.util.Random;

public class EnvironmentFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private final double targetHitbox = 1.0;

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
    protected AttributeHandle bulletTypeHandle;

    protected InteractionClassHandle hitHandle;
    protected ParameterHandle hitTargetIdHandle;
    protected ParameterHandle hitDirectionHandle;
    protected ParameterHandle hitTypeHandle;

    protected ObjectClassHandle targetHandle;
    protected AttributeHandle targetIdHandle;
    protected AttributeHandle targetPositionHandle;

    protected InteractionClassHandle endSimulationHandle;
    protected ParameterHandle federateNumberHandle;


    protected List<Target> targets = new ArrayList<>();
    protected Map<ObjectInstanceHandle,Integer> targetsInstances;

    private boolean bulletInTheAir = false;
    protected boolean bulletCollided = false;

    protected Vector3 bulletPosition;
    protected Vector3 hitDirection;
    protected int hitTarget;
    protected int bulletType;
    protected int bulletId = 1;
    protected ObjectInstanceHandle bulletInstance;

    protected ObjectInstanceHandle atmosphereInstance;


    public class Shape
    {
        public double z;
        public ObjectInstanceHandle rtiInstance;

        public Shape(double z)
        {
            this.z=z;
        }
    }

    //private Map<ObjectInstanceHandle, Double> terrain;

    private Shape[][] terrain;

    //private double[][] terrain;

    private Vector3 wind;
    private double temperature;
    private double pressure;

    // Metody RTI - tego nie ruszamy
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

//Metody pomocnicze RTI - z tego ruszamy tylko publishAndSubscribe

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
        // Subskrypcja na Pocisk
        this.bulletHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Bullet");
        this.bulletIdHandle = rtiamb.getAttributeHandle(bulletHandle,"BulletID");
        this.bulletPositionHandle = rtiamb.getAttributeHandle(bulletHandle,"Position");
        this.bulletTypeHandle = rtiamb.getAttributeHandle(bulletHandle,"Type");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(bulletIdHandle);
        attributes.add(bulletPositionHandle);
        attributes.add(bulletTypeHandle);
        rtiamb.subscribeObjectClassAttributes(bulletHandle, attributes);

        // Publikacja Atmosfery
        this.atmosphereHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Atmosphere");
        this.windDirectoryHandle = rtiamb.getAttributeHandle(atmosphereHandle,"WindDirectory");
        this.temperatureHandle = rtiamb.getAttributeHandle(atmosphereHandle,"Temperature");
        this.pressureHandle = rtiamb.getAttributeHandle(atmosphereHandle,"Pressure");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(windDirectoryHandle);
        attributes.add(temperatureHandle);
        attributes.add(pressureHandle);
        rtiamb.publishObjectClassAttributes(atmosphereHandle, attributes);

        // Publikacja na Teren
        this.terrainHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Terrain");
        this.shapeHandle = rtiamb.getAttributeHandle(terrainHandle,"Shape");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(shapeHandle);
        rtiamb.publishObjectClassAttributes(terrainHandle, attributes);

        // Publikacja na Trafienie
        this.hitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Hit");
        this.hitTargetIdHandle = rtiamb.getParameterHandle(hitHandle,"TargetID");
        this.hitDirectionHandle = rtiamb.getParameterHandle(hitHandle,"HitDirection");
        this.hitTypeHandle = rtiamb.getParameterHandle(hitHandle,"Type");
        rtiamb.publishInteractionClass(hitHandle);

        //Subskrybcja na Cel
        this.targetHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Target");
        this.targetIdHandle = rtiamb.getAttributeHandle(targetHandle,"TargetID");
        this.targetPositionHandle = rtiamb.getAttributeHandle(targetHandle,"Position");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(targetIdHandle);
        attributes.add(targetPositionHandle);
        rtiamb.subscribeObjectClassAttributes(targetHandle, attributes);

        //Subskrycja na Koniec Symulacji
        this.endSimulationHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.EndSimulation");
        this.federateNumberHandle = rtiamb.getParameterHandle(endSimulationHandle,"FederateNumber");
        rtiamb.subscribeInteractionClass(endSimulationHandle);

        //Publikacja na Koniec Symulacji
        rtiamb.publishInteractionClass(endSimulationHandle);

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

    private void run(String federateName, String federationName) throws Exception
    {
        initializeFederate(federateName,federationName);
        mainLoop();
        finalizeFederate(federationName);
    }

    private void mainLoop() throws RTIexception
    {
        setUpEnvironment();
        while (fedamb.running)
        {
            forecast();

            if(bulletCollided)
            {
                if(bulletPosition!=null)sendHitInteraction(hitTarget, hitDirection);
                else bulletCollided=false;
            }
            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime );
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        endStatisticsFederate();
    }

    private void endStatisticsFederate() throws RTIexception{
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(1);
        HLAinteger32BE federateNumberValue = encoderFactory.createHLAinteger32BE(5);
        parameters.put( federateNumberHandle, federateNumberValue.toByteArray() );
        HLAfloat64Time time = timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead );
        rtiamb.sendInteraction(endSimulationHandle,parameters,generateTag(),time);
    }

    private void setUpEnvironment() throws RTIexception
    {
        temperature= 25.9;
        wind = new Vector3(0.5,0.05,0.01);
        pressure = 1020.0;
        generateTerrain();
        registerAtmosphereObject();
    }

    public static void main(String[] args)
    {
        String federateName = "Otoczenie";
        try {
            new EnvironmentFederate().run(federateName, "Federacja");
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }

    private void generateTerrain() throws RTIexception
    {
        int x=50;
        int y=50;
        //Tymczasowy, przykładowy teren
        terrain=new Shape[x][y];
        terrain[4][0] = new Shape( 2.4);
        terrain[5][0] = new Shape( 3.5);
        terrain[4][1] = new Shape(  2.0);
        terrain[5][1] = new Shape(  2.9);
        terrain[4][2] = new Shape(  2.0);
        terrain[5][2] = new Shape(  2.9);
        terrain[4][3] = new Shape(  2.0);
        terrain[5][3] = new Shape(  2.9);
        terrain[4][4] = new Shape(  2.0);
        terrain[5][4] = new Shape(  2.9);
        terrain[4][5] = new Shape(  2.0);
        terrain[5][5] = new Shape(  2.9);
        terrain[4][6] = new Shape(  2.0);
        terrain[5][6] = new Shape(  2.9);
        terrain[4][7] = new Shape(  2.0);
        terrain[5][7] = new Shape(  2.9);
        terrain[4][8] = new Shape(  2.0);
        terrain[5][8] = new Shape(  2.9);
        //TODO: Faktyczny generator terenu

        for(int i=0;i<x;i++)
            for(int j=0;j<y;j++)
                if(terrain[i][j]!=null)registerTerrainObject(i,j,terrain[i][j]);
    }

    private void forecast() throws RTIexception
    {
        //TODO: faktyczna generacja pogody
        Random generator = new Random();
        temperature+=generator.nextDouble()-0.5;
        pressure+=generator.nextDouble()*5-2.5;
        wind.addVector(new Vector3(generator.nextDouble()-0.5,generator.nextDouble()-0.5,generator.nextDouble()-0.5));
        updateAtmosphereObject();
    }

    private void registerAtmosphereObject() throws SaveInProgress, RestoreInProgress, ObjectClassNotPublished, ObjectClassNotDefined, FederateNotExecutionMember, RTIinternalError, NotConnected, AttributeNotDefined, ObjectInstanceNotKnown, AttributeNotOwned, InvalidLogicalTime
    {
        atmosphereInstance = rtiamb.registerObjectInstance(atmosphereHandle);
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        HLAfixedArray<HLAfloat64BE> windValue = encoderFactory.createHLAfixedArray(wrapFloatData(wind.toFloatArray()));
        HLAfloat64BE pressureValue = encoderFactory.createHLAfloat64BE(pressure);
        HLAfloat64BE temperatureValue = encoderFactory.createHLAfloat64BE(temperature);
        attributes.put(windDirectoryHandle,windValue.toByteArray());
        attributes.put(pressureHandle,pressureValue.toByteArray());
        attributes.put(temperatureHandle,temperatureValue.toByteArray());
        rtiamb.updateAttributeValues(atmosphereInstance,
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        log( "Dodano obiekt atmosfery, handle=" + atmosphereInstance);
    }

    private void updateAtmosphereObject() throws RTIexception
    {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(3);
        HLAfixedArray<HLAfloat64BE> windValue = encoderFactory.createHLAfixedArray(wrapFloatData(wind.toFloatArray()));
        HLAfloat64BE pressureValue = encoderFactory.createHLAfloat64BE(pressure);
        HLAfloat64BE temperatureValue = encoderFactory.createHLAfloat64BE(temperature);
        attributes.put(windDirectoryHandle,windValue.toByteArray());
        attributes.put(pressureHandle,pressureValue.toByteArray());
        attributes.put(pressureHandle,pressureValue.toByteArray());
        rtiamb.updateAttributeValues(atmosphereInstance,
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        log( "Zmieniono atrybuty atmosfery, handle=" + atmosphereInstance);
    }

    private void registerTerrainObject(double x, double y, Shape z) throws RTIexception
    {
        z.rtiInstance = rtiamb.registerObjectInstance(terrainHandle);
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(1);
        HLAfixedArray<HLAfloat64BE> shape = encoderFactory.createHLAfixedArray(wrapFloatData(new Vector3(x,y,z.z).toFloatArray()));
        attributes.put(shapeHandle,shape.toByteArray());
        rtiamb.updateAttributeValues(z.rtiInstance,
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        log( "Dodano obiekt terenu na pozycji ("+x +","+y+"), handle=" + atmosphereInstance);
    }

    private void updateTerrainObject(double x, double y, Shape z) throws RTIexception
    {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(1);
        HLAfixedArray<HLAfloat64BE> shape = encoderFactory.createHLAfixedArray(wrapFloatData(new Vector3(x,y,z.z).toFloatArray()));
        attributes.put(shapeHandle,shape.toByteArray());
        rtiamb.updateAttributeValues(z.rtiInstance,
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        log( "Zaktualizowano obiekt terenu na pozycji ("+x +","+y+"), handle=" + atmosphereInstance);
    }

    private void sendHitInteraction(int targetID, Vector3 direction) throws RTIexception
    {
        ParameterHandleValueMap parameters = rtiamb.getParameterHandleValueMapFactory().create(3);
        HLAinteger32BE idValue = encoderFactory.createHLAinteger32BE(targetID);
        HLAinteger32BE typeValue = encoderFactory.createHLAinteger32BE(bulletType);
        HLAfixedArray<HLAfloat64BE> directionValue = encoderFactory.createHLAfixedArray(wrapFloatData(direction.toFloatArray()));
        parameters.put(hitTargetIdHandle,idValue.toByteArray());
        parameters.put(hitDirectionHandle,directionValue.toByteArray());
        parameters.put(hitTypeHandle,typeValue.toByteArray());
        rtiamb.sendInteraction(hitHandle,parameters,generateTag(),timeFactory.makeTime(fedamb.federateTime+fedamb.federateLookahead));
        bulletInTheAir=false;
        bulletPosition=null;
        log("wysłano interakcje trafienia");
    }

    protected void updateBulletPosition(Vector3 position)
    {
        log("Ruch pocisku z "+bulletPosition.toStirng()+" do "+ position.toStirng());
        for (Target target : targets)
            if (pointAndLineDistance(bulletPosition,position,target.getPosition())<=targetHitbox)
            {
                bulletCollided=true;
                hitTarget=target.getId();
                position.subtractVector(bulletPosition);
                hitDirection=bulletPosition;
                log("Pocisk trafił w cel");
                break;
            }


        if(!bulletCollided)if(checkHeight(bulletPosition,position))
        {
            bulletCollided = true;
            hitTarget = -1;
            position.subtractVector(bulletPosition);
            hitDirection = bulletPosition;
            log("Pocisk trafił w ziemię");
        }
        if(!bulletCollided)bulletPosition=position;
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

    private double pointAndLineDistance(Vector3 linePointA, Vector3 linePointB, Vector3 point)
    {
        return linePointB.distanceFrom(linePointA).crossProduct(point.distanceFrom(linePointA)).norm()
                / linePointB.distanceFrom(linePointA).norm();
    }

    private boolean checkHeight(Vector3 linePointA, Vector3 linePointB)
    {
        int i = (int) linePointA.x;
        int j = (int) linePointA.y;
        int n = (int) linePointB.x;
        int m = (int) linePointB.y;
        Vector3 vector = linePointB.distanceFrom(linePointA);
        double x;
        double y;
        double z;
        double aWeight;
        if(i<n)
        {
            i++;
            for (; i <= n; i++)
            {
                aWeight = (i-linePointA.x) / vector.x;
                y = linePointA.y * aWeight + linePointB.y * (1 - aWeight);
                z = linePointA.z * aWeight + linePointB.z * (1 - aWeight);
                //log("posisk "+(z-getZ(i,y))+" nad ziemią");
                if (z < getZ(i, y)) return true;
            }
        }
        else
        {
            n++;
            for (; i >= n; i--)
            {
                aWeight = (linePointA.x - i) / vector.x;
                y = linePointA.y * aWeight + linePointB.y * (1 - aWeight);
                z = linePointA.z * aWeight + linePointB.z * (1 - aWeight);
                //log("posisk "+(z-getZ(i,y))+" nad ziemią");
                if (z < getZ(i, y)) return true;
            }
        }
        if(j<m)
        {
            j++;
            for(;j<=m;j++)
            {
                aWeight = (j-linePointA.y)/vector.y;
                x = linePointA.x * aWeight + linePointB.x * (1 - aWeight);
                z = linePointA.z * aWeight + linePointB.z * (1 - aWeight);
                //log("posisk "+(z-getZ(x,j))+" nad ziemią");
                if (z < getZ(x, j)) return true;
            }
        }
        else
        {
            m++;
            for(;j>=m;j--)
            {
                aWeight = (j-linePointA.y)/vector.y;
                x = linePointA.x * aWeight + linePointB.x * (1 - aWeight);
                z = linePointA.z * aWeight + linePointB.z * (1 - aWeight);
                //log("posisk "+(z-getZ(x,j))+" nad ziemią");
                if (z < getZ(x, j)) return true;
            }
        }
        if(linePointB.z<getZ(linePointB.x,linePointB.y)) return true;
        return false;
    }

    private double getZ(int x,int y)
    {
        if(x<0||y<0||x>terrain.length-1||y>terrain.length-1)return 0.0;
        Shape pos = terrain[x][y];
        if(pos!=null)return pos.z;
        else return 0.0;
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

}
