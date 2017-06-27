package bullet;

import Helpers.Vector3;
import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Time;

public class BulletsFederateAmbassador extends NullFederateAmbassador {

    private BulletsFederate federate;


    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean running 			 = true;


    public BulletsFederateAmbassador(BulletsFederate federate)
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
        StringBuilder builder = new StringBuilder("Reflection for");
        for( AttributeHandle attributeHandle : theAttributes.keySet() )
        {
            if(attributeHandle.equals(federate.windHandle)){
                builder.append(" Wind: ");

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
                    vector.decode(theAttributes.get(federate.windHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                Vector3 position = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());

                builder.append(position.toStirng());
                federate.wind=position;

            } else if(attributeHandle.equals(federate.temperatureHandle)){
                builder.append( " Temperature=" );


                HLAfloat64BE typeData = federate.encoderFactory.createHLAfloat64BE();
                try {
                    typeData.decode(theAttributes.get(federate.temperatureHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                double temperature = typeData.getValue();
                builder.append(temperature);
                federate.temperature = temperature;
            } else if(attributeHandle.equals(federate.pressureHandle)){
                builder.append( " Pressure=" );


                HLAfloat64BE typeData = federate.encoderFactory.createHLAfloat64BE();
                try {
                    typeData.decode(theAttributes.get(federate.pressureHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                double pressure = typeData.getValue();
                builder.append(pressure);
                federate.pressure = pressure;
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
        if( interactionClass.equals(federate.shotHandle) )
        {
            //stworzenie factory
            DataElementFactory<HLAfloat64BE> factory = new DataElementFactory<HLAfloat64BE>()
            {
                public HLAfloat64BE createElement( int index )
                {
                    return federate.encoderFactory.createHLAfloat64BE();
                }
            };

            builder.append( " Czołg wystrzelił ! " );
            HLAfixedArray<HLAfloat64BE> vector = federate.encoderFactory.createHLAfixedArray( factory, 3 );
            try {
                vector.decode(theParameters.get(federate.shotPositionHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            Vector3 position = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());


            try {
                vector.decode(theParameters.get(federate.directionHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            Vector3 direction = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());


            HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
            try {
                typeData.decode(theParameters.get(federate.typeHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }

            int type = typeData.getValue();

            try {
                federate.shotBullet(position,direction,type);
            } catch (RTIexception restoreInProgress) {
                restoreInProgress.printStackTrace();
            }
        }else if(interactionClass.equals(federate.hitHandle))
        {
            try {
                federate.destroyBullet();
            } catch (RTIexception rtIexception) {
                rtIexception.printStackTrace();
            }
        }else if(interactionClass.equals(federate.endSimulationHandle)) {
            HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
            try {
                typeData.decode(theParameters.get(federate.federateNumberHandle));
            } catch (DecoderException e) {
                e.printStackTrace();
            }
            int federateNumber = typeData.getValue();

            if(federateNumber == 2){
                federate.shouldStopRunning = true;
            }
        }

        log( builder.toString() );
    }


    private HLAfloat64BE[] wrapFloatData( float... data )
    {
        int length = data.length;
        HLAfloat64BE[] array = new HLAfloat64BE[length];
        for( int i = 0 ; i < length ; ++i )
            array[i] = federate.encoderFactory.createHLAfloat64BE( data[i] );

        return array;
    }
}
