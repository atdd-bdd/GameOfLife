package GameOfLife;

public class Size {
    public int x;
    public int y;
    public Size(int x, int y){
        this.x = x;
        this.y = y;
    }
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }

    @Override
    public String toString() {
        return x + "," + y;
    }

}
