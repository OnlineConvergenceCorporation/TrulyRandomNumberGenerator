# TrulyRandomNumberGenerator
A Java Frontend to random.org that pools numbers and issues out 63 bit longs on demand

# Usage
```java
    import com.onlineconvergence.spintowinserver.utilities.TruelyRandomNumberGenerator;
    .
    .
    .
    TruelyRandomNumberGenerator ran = new TruelyRandomNumberGenerator();
    long winner = ran.getTruelyRandomNumber64BitNumber(totalEntries);
```

# Notes
As configured, the library grabs 1000 16 bit integers at a time from random.org and parcels them out as needed, asynchonously fetching more when the pool
starts to run low. If you exceed the daily quota on random.org (or the network request fails/times out), you will get a RandomNumberGeneratorFailure runtime
exception, which you should be prepared to handle gracefully.

