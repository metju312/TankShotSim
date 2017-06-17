package environment;

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
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Time;

import java.util.ArrayList;

public class EnvironmentFederateAmbassador extends NullFederateAmbassador {

    private EnvironmentFederate federate;

    protected double federateTime        = 0.0;
    protected double grantedTime         = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean running 			 = true;
    protected int finishHandle           = 0;
    protected int bulletHandle = 0;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    public EnvironmentFederateAmbassador(EnvironmentFederate federate) {
        this.federate = federate;
    }

    private void log( String message )
    {
        System.out.println( "EnvironmentFederateAmbassador: " + message );
    }

    @Override
    public void synchronizationPointRegistrationFailed( String label, SynchronizationPointFailureReason reason)
    {
        log( "Failed to register sync point: " + label + ", reason=" + reason);
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
        if( label.equals(EnvironmentFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized( String label, FederateHandleSet failed)
    {
        log( "Federation Synchronized: " + label );
        //if( label.equals(EnvironmentFederate.READY_TO_RUN) )
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
        //tutaj powinno być dodanie nowego obiektu który się obserwuje, typu
//        log( "Discoverd Object: handle=" + theObject + ", classHandle=" +
//                theObjectClass + ", name=" + objectName );

        if(theObjectClass.equals(federate.bulletHandle)){

        }

        StringBuilder builder = new StringBuilder( "Discover Object Instance:" );
        builder.append( " handle=" + theObjectClass );
        log(builder.toString());
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
        StringBuilder builder = new StringBuilder("");
        for( AttributeHandle attributeHandle : theAttributes.keySet() )
        {
            if(attributeHandle.equals(federate.bulletIdHandle)){
                builder.append("Reflection for Bullet: ");
                builder.append( attributeHandle );
                builder.append( " bulletId=" );

                HLAinteger32BE typeData = federate.encoderFactory.createHLAinteger32BE();
                try {
                    typeData.decode(theAttributes.get(federate.bulletIdHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                int id = typeData.getValue();
                builder.append(id);
                builder.append(" ");

            } else if(attributeHandle.equals(federate.bulletPositionHandle)){
                builder.append( "BulletPosition= " );

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
                    vector.decode(theAttributes.get(federate.bulletPositionHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                Vector3 position = new Vector3(vector.get(0).getValue(), vector.get(1).getValue(),vector.get(2).getValue());

                builder.append(position.toStirng());
            }
        }
        log( builder.toString() );
    }

    @Override
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
        if( interactionClass.equals("federate.servedHandle - znowu odpowienni handle w federacie") )
        {
            builder.append( " (DrinkServed)" );
        }

        // print the tag
        builder.append( ", tag=" + new String(tag) );
        // print the time (if we have it) we'll get null if we are just receiving
        // a forwarded call from the other reflect callback above
        if( time != null )
        {
            builder.append( ", time=" + ((HLAfloat64Time)time).getValue() );
        }

        // print the parameer information
        builder.append( ", parameterCount=" + theParameters.size() );
        builder.append( "\n" );
        for( ParameterHandle parameter : theParameters.keySet() )
        {
            // print the parameter handle
            builder.append( "\tparamHandle=" );
            builder.append( parameter );
            // print the parameter value
            builder.append( ", paramValue=" );
            builder.append( theParameters.get(parameter).length );
            builder.append( " bytes" );
            builder.append( "\n" );
        }

        log( builder.toString() );
    }

//    @Override
//    public void receiveInteraction( int interactionClass,
//                                    ReceivedInteraction theInteraction,
//                                    byte[] tag,
//                                    LogicalTime theTime,
//                                    EventRetractionHandle eventRetractionHandle )
//    {
//        StringBuilder builder = new StringBuilder( "Interaction Received:" );
//        if(interactionClass == addBulletHandle) {
//            try {
//                int qty = EncodingHelpers.decodeInt(theInteraction.getValue(0));
//                double time =  convertTime(theTime);
//                externalEvents.add(new ExternalEvent(qty, ExternalEvent.EventType.ADD , time));
//                builder.append("AddBullet , time=" + time);
//                builder.append(" qty=").append(qty);
//                builder.append( "\n" );
//
//            } catch (ArrayIndexOutOfBounds ignored) {
//
//            }
//        }
//        log( builder.toString() );
//    }

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

