package target;

import Helpers.Vector3;
import hla.rti1516e.ObjectInstanceHandle;

public class Target {

    private int id;
    private Vector3 position;
    private Vector3 positionToAchieve;
    private ObjectInstanceHandle rtiInstance;
    private int armorType;
    private int hp;
    private Vector3 speed = new Vector3(0,0,0);
    public boolean isRegistered=false;

    private double maxSpeed = 1.2;
    private double acceleration = 0.1;

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

    public Vector3 getPositionToAchieve() {
        return positionToAchieve;
    }

    public void setPositionToAchieve(Vector3 postionToAchive) {
        this.positionToAchieve = postionToAchive;
    }

    public int getArmorType() {
        return armorType;
    }

    public void setArmorType(int armorType) {
        this.armorType = armorType;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public Vector3 getSpeed() {
        return speed;
    }

    public void setSpeed(Vector3 speed) {
        this.speed = speed;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }
}
