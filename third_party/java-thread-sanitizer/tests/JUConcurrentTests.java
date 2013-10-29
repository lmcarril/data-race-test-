/* Copyright (c) 2010 Google Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class contains tests for jtsan. Tests cover java.util.concurrent functionality.
 *
 * @author Konstantin Serebryany
 * @author Sergey Vorobyev
 */
public class JUConcurrentTests {

  //------------------ Positive tests ---------------------

  @RaceTest(expectRace = true,
      description = "Three threads writing under a reader lock, one under a writing lock")
  public void writingUnderReaderLock() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        lock.readLock().lock();
        sharedVar++;
        lock.readLock().unlock();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes locked with different locks")
  public void differentLocksWW2() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(2) {

      public void thread1() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        synchronized (this) {
          sharedVar++;
        }
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Concurrent access after correct CyclicBarrier")
  public void cyclicBarrierWrong() {
    new ThreadRunner(4) {
      CyclicBarrier barrier;

      public void setUp() {
        barrier = new CyclicBarrier(4);
        sharedVar = 0;
      }

      public void thread1() {
        synchronized (this) {
          sharedVar++;
        }
        try {
          barrier.await();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        if (sharedVar == 4) {
          sharedVar = 5;
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
      }
    };
  }


  //------------------ Negative tests ---------------------

  @RaceTest(expectRace = false,
      description = "Work with BlockingQueue. Two readers, two writers")
  public void arrayBlockingQueue() {
    final int capacity = 10;
    final int iter = 1000;
    new ThreadRunner(4) {

      BlockingQueue<Integer> q;

      public void setUp() {
        q = new ArrayBlockingQueue<Integer>(capacity);
      }

      public void thread1() {
        try {
          for (int i = 0; i < iter; i++) {
            q.put(i);
          }
        } catch (InterruptedException ex) {
          throw new RuntimeException("Exception in arrayBlockingQueue test", ex);
        }
      }

      public void thread2() {
        try {
          for (int i = 0; i < iter; i++) {
            Integer o = q.take();
          }
        } catch (InterruptedException ex) {
          throw new RuntimeException("Exception in arrayBlockingQueue test", ex);
        }
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread2();
      }

    };
  }

  @RaceTest(expectRace = false,
      description = "Use ReentrantLock.lockInteruptibly for acquired a lock")
  public void reentrantLockInterruptibly() {
    new ThreadRunner(2) {
      ReentrantLock lock;

      public void setUp() {
        lock = new ReentrantLock();
      }

      public void thread1() {
        try {
          lock.lockInterruptibly();
          sharedVar++;
          lock.unlock();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test reentrantLockInterruptibly", e);
        }
      }

      public void thread2() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "CountDownLatch")
  public void countDownLatch() {
    new ThreadRunner(4) {
      CountDownLatch latch;

      public void setUp() {
        latch = new CountDownLatch(3);
        sharedVar = 0;
      }

      public void thread1() {
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test CountDownLatch", e);
        }
        if (sharedVar == 3) {
          sharedVar = 4;
        } else {
          System.err.println("CountDownLatch assert");
          System.exit(1);
        }
      }

      public void thread2() {
        synchronized (this) {
          sharedVar++;
        }
        latch.countDown();
      }

      public void thread3() {
        thread2();
      }

      public void thread4() {
        thread2();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "After CyclicBarrier only one thread increments shared int")
  public void cyclicBarrier() {
    new ThreadRunner(4) {
      CyclicBarrier barrier;

      public void setUp() {
        barrier = new CyclicBarrier(4);
        sharedVar = 0;
      }

      public void thread1() {
        synchronized (this) {
          sharedVar++;
          shortSleep();
        }
        try {
          barrier.await();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
        sharedVar++;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Semaphore")
  public void semaphore() {
    final Semaphore semaphore = new Semaphore(0);
    new ThreadRunner(2) {

      public void thread1() {
        longSleep();
        sharedVar = 1;
        semaphore.release();
      }

      public void thread2() {
        try {
          semaphore.acquire();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReadWriteLock: write locks only")
  public void writeLocksOnly() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(2) {
      public void thread1() {
        lock.writeLock().lock();
        int v = sharedVar;
        lock.writeLock().unlock();
      }

      public void thread2() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReadWriteLock: both read and write locks")
  public void readAndWriteLocks() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        lock.readLock().lock();
        int v = sharedVar;
        lock.readLock().unlock();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        lock.writeLock().lock();
        int v = sharedVar;
        lock.writeLock().unlock();
      }

      public void thread4() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReentrantReadWriteLock: tryLock")
  public void tryLock() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        while (!lock.readLock().tryLock()) {
          shortSleep();
        }
        int v = sharedVar;
        lock.readLock().unlock();
      }

      public void thread2() {
        try {
          while (!lock.readLock().tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          int v = sharedVar;
          lock.readLock().unlock();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public void thread3() {
        while (!lock.writeLock().tryLock()) {
          shortSleep();
        }
        sharedVar++;
        lock.writeLock().unlock();
      }

      public void thread4() {
        try {
          while (!lock.writeLock().tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          sharedVar++;
          lock.writeLock().unlock();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test tryLock", e);
        }
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReentrantLock: simple access")
  public void reentrantLockSimple() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(2) {
      public void thread1() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReentrantLock: tryLocks")
  public void tryLock2() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(3) {
      public void thread1() {
        while (!lock.tryLock()) {
          shortSleep();
        }
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        try {
          while (!lock.tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          sharedVar++;
          lock.unlock();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test tryLock2", e);
        }
      }

      public void thread3() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "AtomicInteger increment")
  public void atomicInteger() {
    final AtomicInteger i = new AtomicInteger();
    new ThreadRunner(4) {

      public void thread1() {
        i.incrementAndGet();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
      }
    };
  }

  // Example from http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/main/java/util/
  // concurrent/locks/LockSupport.java?view=markup .
  class FIFOMutex {
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Queue<Thread> waiters
        = new ConcurrentLinkedQueue<Thread>();

    public void lock() {
      boolean wasInterrupted = false;
      Thread current = Thread.currentThread();
      waiters.add(current);

      // Block while not first in queue or cannot acquire lock.
      while (waiters.peek() != current ||
          !locked.compareAndSet(false, true)) {
        LockSupport.park(this);
        if (Thread.interrupted()) // ignore interrupts while waiting.
          wasInterrupted = true;
      }

      waiters.remove();
      if (wasInterrupted)          // reassert interrupt status on exit.
        current.interrupt();
    }

    public void unlock() {
      locked.set(false);
      LockSupport.unpark(waiters.peek());
    }
  }

  @RaceTest(expectRace = false,
      description = "Use custom reentrant lock - FIFOMutex. " +
          "Test LockSupport happens-before relations")
  public void fifoMutexUser() {
    new ThreadRunner(4) {
      FIFOMutex mu;

      public void setUp() {
        mu = new FIFOMutex();
      }

      public void thread1() {
        mu.lock();
        shortSleep();
        sharedVar++;
        mu.unlock();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Test happens-before relations between FutureTask calculation and get")
  public void futureTask() {
    FutureTask<int[]> future = new FutureTask<int[]>(new Callable<int[]>() {
      public int[] call() {
        int[] res = new int[1];
        res[0] = 42;
        return res;
      }
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(future);
    try {
      int[] futureRes = future.get();
      futureRes[0]++;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    executor.shutdown();
  }

  @RaceTest(expectRace = true,
      description = "Test separate monitor and lock instances of Lock")
  public void lockNeMonitor() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(2) {
      public void thread1() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        longSleep();
        synchronized(lock) {
          sharedVar++;
        }
      }
    };
  }

  @ExcludedTest(reason = "SynchronousQueue is not supported yet")
  @RaceTest(expectRace = false, description = "Test SynchronousQueue")
  public void synchronousQueue() {
    final SynchronousQueue<String> queue = new SynchronousQueue<String>();
    new ThreadRunner(2) {
      public void thread1() {
        try {
          sharedVar++;
          queue.put(new String("test"));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public void thread2() {
        try {
          String s = queue.take();
          sharedVar++;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

}
