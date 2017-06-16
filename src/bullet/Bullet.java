package bullet;

import Helpers.Vector3;

public class Bullet {
    private int id;
    private Vector3 position;

    public Bullet() {
    }

    public Bullet(int id) {
        this.id = id;
    }

    public Bullet(int id, Vector3 position) {
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
}
