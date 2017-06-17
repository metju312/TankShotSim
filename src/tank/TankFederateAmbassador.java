package tank;

import Helpers.Vector3;
import hla.rti1516e.NullFederateAmbassador;
import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.FederateHandleSet;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.SynchronizationPointFailureReason;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import target.Target;

import java.util.ArrayList;
import java.util.HashMap;


public class TankFederateAmbassador extends NullFederateAmbassador
{
    private TankFederate federate;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    public TankFederateAmbassador( TankFederate federate )
    {
        this.federate = federate;
    }
    private void log( String message )
    {
        System.out.println( "FederateAmbassador: " + message );
    }


    @Override
    public void synchronizationPointRegistrationFailed( String label,
                                                        SynchronizationPointFailureReason reason )
    {
        log( "Failed to register sync point: " + label + ", reason="+reason );
    }

    @Override
    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    @Override
    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(TankFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed )
    {
        log( "Federation Synchronized: " + label );
        //if( label.equals(ExampleFederate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    @Override
    public void timeRegulationEnabled( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant( LogicalTime time )
    {
        this.federateTime = ((HLAfloat64Time)time).getValue();
        this.isAdvancing = false;
    }






    @Override
    public void discoverObjectInstance( ObjectInstanceHandle theObject,
                                        ObjectClassHandle theObjectClass,
                                        String objectName )
            throws FederateInternalError
    {
        //tutaj powinno być dodanie nowego obiektu który się obserwóje, typu
//        log( "Discoverd Object: handle=" + theObject + ", classHandle=" +
//                theObjectClass + ", name=" + objectName );
    }

    @Override
    public void reflectAttributeValues( ObjectInstanceHandle theObject,
                                        AttributeHandleValueMap theAttributes,
                                        byte[] tag,
                                        OrderType sentOrder,
                                        TransportationTypeHandle transport,
                                        SupplementalReflectInfo reflectInfo )
            throws FederateInternalError
    {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        reflectAttributeValues( theObject,
                theAttributes,
                tag,
                sentOrder,
                transport,
                null,
                sentOrder,
                reflectInfo );
    }

    @Override
    public void reflectAttributeValues( ObjectInstanceHandle theObject,
                                        AttributeHandleValueMap theAttributes,
                                        byte[] tag,
                                        OrderType sentOrdering,
                                        TransportationTypeHandle theTransport,
                                        LogicalTime time,
                                        OrderType receivedOrdering,
                                        SupplementalReflectInfo reflectInfo )
            throws FederateInternalError
    {
        StringBuilder builder = new StringBuilder("Reflection for: ");
        if(theAttributes.containsKey(federate.shapeHandle)){
            builder.append("Terrain: ");

            //stworzenie factory
            DataElementFactory<HLAfloat64BE> factory = new DataElementFactory<HLAfloat64BE>()
            {
                public HLAfloat64BE createElement( int index )
                {
                    return federate.encoderFactory.createHLAfloat64BE();
                }
            };

            HLAfixedArray<HLAfloat64BE> vector = federate.encoderFactory.createHLAfixedArray( factory, 3 );
            try {
                vector.decode(theAttributes.get(federate.shapeHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            Vector3 position = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());
            if(federate.terrain.get((int)(position.x+0.5))==null)federate.terrain.put((int)(position.x+0.5),new HashMap<>());
            federate.terrain.get((int)(position.x+0.5)).put((int)(position.y+0.5),position.z);

            builder.append(position.toStirng());
        } else if(theAttributes.containsKey(federate.targetIdHandle)){
            if(!targetExists(theObject)) {
                builder.append("New Target handle=");
                builder.append(theObject);
                Target target = new Target();

                HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
                try {
                    typeData.decode(theAttributes.get(federate.targetIdHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                int id = typeData.getValue();
                target.setId(id);
                target.setRtiInstance(theObject);
                federate.targets.add(target);

                builder.append(", modify position of Target handle=");
                builder.append(theObject);
            }
            Target target = getTarget(theObject);

            //stworzenie factory
            DataElementFactory<HLAfloat64BE> factory = new DataElementFactory<HLAfloat64BE>()
            {
                public HLAfloat64BE createElement( int index )
                {
                    return federate.encoderFactory.createHLAfloat64BE();
                }
            };

            HLAfixedArray<HLAfloat64BE> vector = federate.encoderFactory.createHLAfixedArray( factory, 3 );
            try {
                vector.decode(theAttributes.get(federate.targetPositionHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            Vector3 position = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());
            builder.append(", position: ");
            builder.append(position.toStirng());
            target.setPosition(position);
            }
        log( builder.toString() );
    }

    private boolean targetExists(ObjectInstanceHandle theObject) {
        for (Target target : federate.targets) {
            if(target.getRtiInstance().equals(theObject)){
                return true;
            }
        }
        return false;
    }

    private Target getTarget(ObjectInstanceHandle theObject){
        for (Target target : federate.targets) {
            if(target.getRtiInstance().equals(theObject)){
                return target;
            }
        }
        return null;
    }

    @Override
    public void removeObjectInstance( ObjectInstanceHandle theObject,
                                      byte[] tag,
                                      OrderType sentOrdering,
                                      SupplementalRemoveInfo removeInfo )
            throws FederateInternalError
    {
        log( "Object Removed: handle=" + theObject );
    }

    }

