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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class TargetsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";


    private RTIambassador rtiamb;
    private TargetsFederateAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;

    public final double generateTargetChance = 0.1;

    private int maxTargetId=0;


    protected List<Target> targets = new ArrayList<>();
    protected List<Vector3> positionsToAchieve = new ArrayList<>();
    protected double positionToAchieveHitbox = 2;
    protected Map<Integer,Map<Integer,Double>> terrain;

    protected InteractionClassHandle hitHandle;
    protected ParameterHandle hitTargetIdHandle;
    protected ParameterHandle hitDirectionHandle;
    protected ParameterHandle hitTypeHandle;

    protected ObjectClassHandle targetHandle;
    protected AttributeHandle targetIdHandle;
    protected AttributeHandle targetPositionHandle;

    protected ObjectClassHandle terrainHandle;
    protected AttributeHandle shapeHandle;

    private boolean shouldGeneratePointsToAchieve = true;
    private boolean terrainExists = false;

    private Target struckTarget=null;

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
        //Subskrybcja na Trafienie
        this.hitHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.Hit");
        this.hitTargetIdHandle = rtiamb.getParameterHandle(hitHandle,"TargetID");
        this.hitDirectionHandle = rtiamb.getParameterHandle(hitHandle,"HitDirection");
        this.hitTypeHandle = rtiamb.getParameterHandle(hitHandle,"Type");
        rtiamb.subscribeInteractionClass(hitHandle);

        //Publikacja Celu
        this.targetHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Target");
        this.targetIdHandle = rtiamb.getAttributeHandle(targetHandle,"TargetID");
        this.targetPositionHandle = rtiamb.getAttributeHandle(targetHandle,"Position");
        AttributeHandleSet attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(targetIdHandle);
        attributes.add(targetPositionHandle);
        rtiamb.publishObjectClassAttributes(targetHandle,attributes);

        //Subskrybcja na Teren
        this.terrainHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Terrain");
        this.shapeHandle = rtiamb.getAttributeHandle(terrainHandle,"Shape");
        attributes = rtiamb.getAttributeHandleSetFactory().create();
        attributes.add(shapeHandle);
        rtiamb.subscribeObjectClassAttributes(terrainHandle,attributes);


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
        terrain = new HashMap<>();
        mainLoop();
        finalizeFederate(federationName);
    }

    private void mainLoop() throws RTIexception, InterruptedException{
        Random generator = new Random();
        //generateTarget();
        while (fedamb.running)
        {
            if(terrainExists){
                //if(generator.nextDouble() < generateTargetChance){
                    //generateTarget();
                //}
            }

            advanceTime( 1.0 );
            log( "Time Advanced to " + fedamb.federateTime);

            if(terrain.size() != 0){
                terrainExists = true;
            }
            if(terrainExists){
                if(shouldGeneratePointsToAchieve){
                    initializePositionsToAchieve();
                    generateTarget();
                    shouldGeneratePointsToAchieve = false;
                }
            }

            if(struckTarget!=null)
            {
                removeTargetObject();
            }
            for (Target target:targets)
            {
                moveTarget(target);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected void initializePositionsToAchieve() {
        //półkole
        int x = 16;
        int y = -17;
        positionsToAchieve.add(new Vector3(x,y,getZ(x,y)));
        x = 4;
        y = -9;
        positionsToAchieve.add(new Vector3(x,y,getZ(x,y)));
        x = 1;
        y = 2;
        positionsToAchieve.add(new Vector3(x,y,getZ(x,y)));
        x = 4;
        y = 9;
        positionsToAchieve.add(new Vector3(x,y,getZ(x,y)));
        x = 16;
        y = 17;
        positionsToAchieve.add(new Vector3(x,y,getZ(x,y)));
    }

    private void moveTarget(Target target) throws RTIexception {
        definePositionToAchieve(target);
        Vector3 dir = target.getPositionToAchieve().distanceFrom(target.getPosition());
        dir.normalize();
        double speedNorm= target.getSpeed().dotProduct(dir);
        if(speedNorm<0.0)speedNorm=0.0;
        if(speedNorm<target.getMaxSpeed())dir.timesA(target.getAcceleration()+speedNorm);
        else dir.timesA(speedNorm);
        target.getPosition().addVector(dir);
        target.getPosition().z=getZ(target.getPosition().x,target.getPosition().y)+1;
        target.setSpeed(dir);
        updateTargetObject(target);
        log("Cel: " + target.getId() + " poruszył się na pozycję : "+target.getPosition().toStirng()+" z prędkością : "+target.getSpeed().norm()+" / "+target.getSpeed().toStirng());
    }

    private void definePositionToAchieve(Target target) {
        if(target.getPositionToAchieve() == null){
            target.setPositionToAchieve(positionsToAchieve.get(0));
        }else if(achieved(target, positionsToAchieve.get(positionsToAchieve.size()-1))){ //osiągnięto ostatni
            log("Cel: " + target.getId() + " uciekł!");
            //TODO destroy
        }else{ // wybór następnego punktu
            for (int i = 0; i < positionsToAchieve.size(); i++) {
                if(target.getPositionToAchieve().equals(positionsToAchieve.get(i))){ // znajdź dokładnie taki sam punkt na liście Achieve
                    if(achieved(target, positionsToAchieve.get(i))){ //jeśli go osiągnięto
                        target.setPositionToAchieve(positionsToAchieve.get(i+1));
                        log("Cel: " + target.getId() + " zwrot w  kierunku: " + target.getPositionToAchieve().toStirng());
                        break;
                    }
                }
            }
        }
    }

    private boolean achieved(Target target, Vector3 checkingPosition) {
        if(pointAndLineDistance(target.getPosition(),target.getSpeed(),checkingPosition)<= positionToAchieveHitbox){
            return true;
        }
        return false;
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

    protected void damageTarget(int id, int type, Vector3 dir)
    {
        log("pocisk trafił w cell o id = "+id);
        struckTarget=null;
        for(Target target:targets)
        {
            if(target.getId()==id)struckTarget=target;
        }
        if(struckTarget!=null)
        {
            log("znalezione ten cel, usuwamy");
            //Magia, obliczanie obrarzeń itp. póki co trafiony-zatopiony
            targets.remove(struckTarget);
        }
    }

    private void generateTarget() throws SaveInProgress, AttributeNotDefined, ObjectInstanceNotKnown, RestoreInProgress, NotConnected, ObjectClassNotDefined, InvalidLogicalTime, AttributeNotOwned, FederateNotExecutionMember, RTIinternalError, ObjectClassNotPublished {
        Random generator = new Random();
        int type = generator.nextInt(5);

        //zawsze generuj czołg - //TODO potem to usunąć i generować czołgi o różnych typach
        Target target = new Target(++maxTargetId,new Vector3(18.0,-18.0,0.0));
        targets.add(target);
        registerTargetObject(target);
        log("Stworzono cel o id: "+maxTargetId);

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
//                Target target = new Target(++maxTargetId,new Vector3(5.0,-5.0,0.0));
//                targets.add(target);
//                registerTargetObject(target);
//                log("Stworzono cel o id: "+maxTargetId);
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
        rtiamb.updateAttributeValues(targetObject.getRtiInstance(),
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        targetObject.isRegistered=true;
        log( "Dodano obiekt celu, handle=" + targetObject.getRtiInstance());
    }

    private void updateTargetObject(Target targetObject) throws RTIexception
    {
        AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(2);
        HLAinteger32BE idValue = encoderFactory.createHLAinteger32BE(targetObject.getId());
        HLAfixedArray<HLAfloat64BE> positionValue = encoderFactory.createHLAfixedArray(wrapFloatData(targetObject.getPosition().toFloatArray()));
        attributes.put(targetIdHandle,idValue.toByteArray());
        attributes.put(targetPositionHandle,positionValue.toByteArray());
        rtiamb.updateAttributeValues(targetObject.getRtiInstance(),
                attributes,
                generateTag(),
                timeFactory.makeTime( fedamb.federateTime+fedamb.federateLookahead ));
        targetObject.isRegistered=true;
        //log( "Zmodyfikowano pozycję celu, handle=" + targetObject.getRtiInstance());
    }

    private void removeTargetObject() throws ObjectInstanceNotKnown, RestoreInProgress, DeletePrivilegeNotHeld, SaveInProgress, FederateNotExecutionMember, RTIinternalError, NotConnected {
        rtiamb.deleteObjectInstance(struckTarget.getRtiInstance(),generateTag());
        struckTarget=null;
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
        System.out.println( "TargetsFederate   : " + message );
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

    private double pointAndLineDistance(Vector3 linePointA, Vector3 linePointB, Vector3 point)
    {
        return linePointB.distanceFrom(linePointA).crossProduct(point.distanceFrom(linePointA)).norm()
                /linePointB.distanceFrom(linePointA).norm();
    }
}


