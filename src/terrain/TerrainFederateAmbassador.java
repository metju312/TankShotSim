package terrain;

import hla.rti1516e.NullFederateAmbassador;

public class TerrainFederateAmbassador extends NullFederateAmbassador
{
    private TerrainFederate federate;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    public TerrainFederateAmbassador( TerrainFederate federate )
    {
        this.federate = federate;
    }
}
