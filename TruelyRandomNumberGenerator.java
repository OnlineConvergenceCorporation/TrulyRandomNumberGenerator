/**
 * 
 */
package com.onlineconvergence.spintowinserver.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.onlineconvergence.spintowinserver.dbos.DailyEntriesDBO;

/**
 * @author James M. Turner
 * (c) 2015 Online Convergence Corporation
 * 
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 *
 */

public class TruelyRandomNumberGenerator {

	private static final Stack<Integer> numberPool = new Stack<Integer>();
	
	private static final String RANDOM_URL = "https://www.random.org/integers/?min=0&max=65535&col=1&base=10&format=plain&rnd=new&num=";
	
	private static final ReentrantLock randomLock = new ReentrantLock();
	
	private static final int REQUEST_TIMEOUT = 10;
	
	private static int POOLSIZE = 1000;
	
	private static final int POOLMINIMUM = 16; 

	public static class RandomNumberGeneratorFailure extends RuntimeException {

		static final long serialVersionUID = 2850126990353608029L;
		
	}
	
	/**
	 * Create a new random number generator.
	 */
	public TruelyRandomNumberGenerator() {
		super();
		// Someone else may have already fetched a pool, so don't do it automatically
		if (numberPool.size() <= POOLMINIMUM) {
			try {
				if (randomLock.tryLock(REQUEST_TIMEOUT,TimeUnit.SECONDS)) {
					// Don't hang the creation thread doing a pre-fetch
					// Check again, someone may have been fetching when we checked, but now we own the lock, so it can only be us fetching
					if (numberPool.size() <= POOLMINIMUM) {
						Thread th = new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									if (randomLock.tryLock(REQUEST_TIMEOUT,TimeUnit.SECONDS)) {
										try {
											fetchMorePoolIntegers();
										} finally {
											randomLock.unlock();
										}
									} else {
										throw new RandomNumberGeneratorFailure();
									}
								} catch (InterruptedException e) {
									throw new RandomNumberGeneratorFailure();
								}
							}
						});
						randomLock.unlock();
						th.start();
					}
				} else {
					// Lock timed out, very bad news
					throw new RandomNumberGeneratorFailure();
				}
			} catch (InterruptedException e) {
				throw new RandomNumberGeneratorFailure();
			}

		}
	}
	
	
	private void fetchMorePoolIntegers() {
		BufferedReader r = null;
		try {
			URL url = new URL(RANDOM_URL + POOLSIZE);
			HttpURLConnection c = (HttpURLConnection) url.openConnection();
			// Might have gotten a 503 because quota is exhausted
			if (c.getResponseCode() != 200) {
				throw new RandomNumberGeneratorFailure();
				
			}
			InputStream str = c.getInputStream();
			r = new BufferedReader(new InputStreamReader(str));
			for (int n = 0; n < POOLSIZE; n++) {
				numberPool.push(Integer.valueOf(r.readLine()));
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new RandomNumberGeneratorFailure();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RandomNumberGeneratorFailure();
		}
		finally {
			if (r != null) {
				try {
					r.close();
				} catch (IOException e) {
					e.printStackTrace();
					throw new RandomNumberGeneratorFailure();
				}
			}
		}
	}
		
	private Integer getNextIntegerInPool() {
		try {
			Integer num = numberPool.pop();
			// Proactively get more numbers asynchronously
			if (numberPool.size() <= POOLMINIMUM) {
				if (randomLock.tryLock()) {
					try {
						new Thread(new Runnable() {
						
							@Override
							public void run() {
								fetchMorePoolIntegers();
							}
						}).start();
					} finally {
						randomLock.unlock();
					}
				}
			}
			return num;
		} catch (EmptyStackException ex) {
			try {
				if (randomLock.tryLock(REQUEST_TIMEOUT,TimeUnit.SECONDS)) {
					// Try again, someone else may have just fetched
					try {
						int number = numberPool.pop();
						return number;
					} catch (EmptyStackException ex1) {
						// We have to run this synchronously, that sucks...
						fetchMorePoolIntegers();
						return numberPool.pop();
					} finally {
						randomLock.unlock();
					}
				} else {
					// Couldn't get a lock, bad news
					throw new RandomNumberGeneratorFailure();
				}
			} catch (InterruptedException e) {
				throw new RandomNumberGeneratorFailure();
			}
		}
	}

	
	/**
	 * Generates a Long which is guaranteed to be evenly distributed across the range 0 to maxvalue
	 * @param maxvalue The maximum value that will be returned
	 * @return A random value between 0 and maxvalue
	 */
	
	public long getTruelyRandomNumber64BitNumber(Long maxvalue) {
		Long l = 0L;
		//Start by creating a 63 bit number
		for (int i = 0; i < 4; i++) {
			l = l << 16;
			l = l + getNextIntegerInPool();
		}
		while (true) {
			l = l & 0x7FFFFFFFFFFFFFFFL;
		
			// Get the value by doing a modulo of the random number with the maximum value
			Long val = l % maxvalue;
		
			// If the value is in the remainder of the random value mod the max value, shift two bytes and do it again, otherwise return the value
			Long mult = l / maxvalue;
			if (((Long.MAX_VALUE - maxvalue) >= (mult * maxvalue)) || (val == 0L)) {
				return val;
			}
			
			l = ((l & 0xFFFFFFFFFFFFL) << 16) + getNextIntegerInPool();
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TruelyRandomNumberGenerator gen = new TruelyRandomNumberGenerator();
		for (int i = 0; i < 10; i++) {
			System.out.format("Random number %d is %,d\n", i, gen.getTruelyRandomNumber64BitNumber(Long.MAX_VALUE));
		}
	}

}
