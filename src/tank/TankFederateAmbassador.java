package tank;

import hla.rti1516e.NullFederateAmbassador;

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
}
