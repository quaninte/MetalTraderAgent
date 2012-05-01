package agent.trader.user.cnpm1.nhom6;

import java.util.Random;

/** Generate random integers in a certain range. */
public final class RandomRange {
  
  public static int getRandomInteger(int aStart, int aEnd){
    Random aRandom = new Random();
    if ( aStart > aEnd ) {
      throw new IllegalArgumentException("Start cannot exceed End.");
    }
    //get the range, casting to long to avoid overflow problems
    long range = (long)aEnd - (long)aStart + 1;
    // compute a fraction of the range, 0 <= frac < range
    long fraction = (long)(range * aRandom.nextDouble());
    int randomNumber =  (int)(fraction + aStart);    
    return randomNumber;
  }
  
  private static void log(String aMessage){
    System.out.println(aMessage);
  }
}