package target;

import Helpers.Vector3;
import bullet.BulletsFederate;
import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;

import java.util.HashMap;

public class TargetsFederateAmbassador extends NullFederateAmbassador {

    private TargetsFederate federate;


    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean running 			 = true;


    public TargetsFederateAmbassador(TargetsFederate federate)
    {
        this.federate=federate;
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
        if( label.equals(BulletsFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(BulletsFederate.READY_TO_RUN) )
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

        StringBuilder builder = new StringBuilder("");
        for( AttributeHandle attributeHandle : theAttributes.keySet() )
        {
            builder.append("Reflection for ");
            if(attributeHandle.equals(federate.shapeHandle)){
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
            }
        }
        log( builder.toString() );
    }


    public void receiveInteraction( InteractionClassHandle interactionClass,
                                    ParameterHandleValueMap theParameters,
                                    byte[] tag,
                                    OrderType sentOrdering,
                                    TransportationTypeHandle theTransport,
                                    SupplementalReceiveInfo receiveInfo )
            throws FederateInternalError
    {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        this.receiveInteraction( interactionClass,
                theParameters,
                tag,
                sentOrdering,
                theTransport,
                null,
                sentOrdering,
                receiveInfo );
    }

    @Override
    public void receiveInteraction( InteractionClassHandle interactionClass,
                                    ParameterHandleValueMap theParameters,
                                    byte[] tag,
                                    OrderType sentOrdering,
                                    TransportationTypeHandle theTransport,
                                    LogicalTime time,
                                    OrderType receivedOrdering,
                                    SupplementalReceiveInfo receiveInfo )
            throws FederateInternalError
    {
        StringBuilder builder = new StringBuilder( "Interaction Received:" );

        // print the handle
        builder.append( " handle=" + interactionClass );
        if ( interactionClass.equals(federate.hitHandle) ){
            builder.append( " Czołg trafił ! " );
//
//            //stworzenie factory
            DataElementFactory<HLAfloat64BE> factory = new DataElementFactory<HLAfloat64BE>()
            {
                public HLAfloat64BE createElement( int index )
                {
                    return federate.encoderFactory.createHLAfloat64BE();
                }
            };

            HLAfixedArray<HLAfloat64BE> vector = federate.encoderFactory.createHLAfixedArray( factory, 3 );

            try {
                vector.decode(theParameters.get(federate.hitDirectionHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            Vector3 direction = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());






            HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
            try {
                typeData.decode(theParameters.get(federate.hitTypeHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }

            int type = typeData.getValue();

            HLAinteger32BE idData = federate.encoderFactory.createHLAinteger32BE();
            try {
                idData.decode(theParameters.get(federate.hitTargetIdHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }

            int id = idData.getValue();


            builder.append( " W cel o id = "+id );
            federate.damageTarget(id,type,direction);

        }else if(interactionClass.equals(federate.endSimulationHandle)) {
            HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
            try {
                typeData.decode(theParameters.get(federate.federateNumberHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            int federateNumber = typeData.getValue();

            if(federateNumber == 3){
                running = false;
            }
        }

        log( builder.toString() );
    }


    private HLAfloat64BE[] wrapFloatData(float... data )
    {
        int length = data.length;
        HLAfloat64BE[] array = new HLAfloat64BE[length];
        for( int i = 0 ; i < length ; ++i )
            array[i] = federate.encoderFactory.createHLAfloat64BE( data[i] );

        return array;
    }
}


