package bullet;

import hla.rti1516e.NullFederateAmbassador;

public class BulletFederateAmbassador extends NullFederateAmbassador
{
    private BulletFederate federate;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    public BulletFederateAmbassador( BulletFederate federate )
    {
        this.federate = federate;
    }
}