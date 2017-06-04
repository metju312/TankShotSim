package Helpers;

public class Vector3
{
    public double x;
    public double y;
    public double z;
    public Vector3(double x, double y, double z)
    {
        this.x=x;
        this.y=y;
        this.z=z;

    }
    public boolean equals(Vector3 b)
    {
        return x==b.x&&y==b.y&&z==b.z;
    }

    public double[] toArray()
    {
        return new double[]{x,y,z};
    }
    public float[] toFloatArray()
    {
        return new float[]{(float) x, (float) y, (float) z};
    }
}
