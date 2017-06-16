package target;

import Helpers.Vector3;
import hla.rti1516e.ObjectInstanceHandle;

public class Target {

    private int id;
    private Vector3 position;
    private ObjectInstanceHandle rtiInstance;
    private int armorType;
    private int hp;
    private int speed;
    public boolean isRegistered=false;

    public Target() {
    }

    public Target(int id) {
        this.id = id;
    }

    public Target(int id, Vector3 position) {
        this.id = id;
        this.position = position;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public ObjectInstanceHandle getRtiInstance() {
        return rtiInstance;
    }

    public void setRtiInstance(ObjectInstanceHandle rtiInstance) {
        this.rtiInstance = rtiInstance;
    }
}
