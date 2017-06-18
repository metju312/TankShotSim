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

    public void addVector(Vector3 b)
    {
        x+=b.x;
        y+=b.y;
        z+=b.z;
    }

    public void subtractVector(Vector3 b)
    {
        x-=b.x;
        y-=b.y;
        z-=b.z;
    }

    public void timesA(double a)
    {
        x*=a;
        y*=a;
        z*=a;
    }

    public Vector3 distanceFrom(Vector3 b)
    {
        return new Vector3(this.x-b.x,y-b.y,z-b.z);
    }

    public Vector3 crossProduct(Vector3 vecB)
    {
        return new Vector3(this.y*vecB.z - this.z*vecB.y,
                           this.x*vecB.z - this.z*vecB.x,
                           this.x*vecB.y - this.y*vecB.x);
    }

    public double dotProduct(Vector3 vecB)
    {
        return this.x*vecB.x+this.y*vecB.y+this.z*vecB.z;
    }

    public double norm()
    {
        return Math.sqrt(x*x+y*y+z*z);
    }

    public void normalize()
    {
        double norm= this.norm();
        if (norm>0)
        {
            x = x / norm;
            y = y / norm;
            z = z / norm;
        }
    }

    public String toStirng()
    {
        return "Składowe: x = " + x + "; y = "+y+"; z = "+z;
    }


}
