import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicLong;

public class TSPForkJoin {

  final Object lock = new Object();
  private long distance[][];
  private List<? extends TSPPoint> points; // First element (0,0)
  private List<Integer> bestPath = null;
  private long bestPathLength = Integer.MAX_VALUE;
  private long startMillies, bestSolution = 0;
  private boolean bestSolutionFound = false;
  private int costX;
  private boolean abort;
  private int costY;
  private AtomicLong tasks;
  private AtomicLong done;

  public TSPForkJoin(List<? extends TSPPoint> labels) {
    this(labels, 1, 1);
  }

  public TSPForkJoin(List<? extends TSPPoint> points, int costX, int costY) {
    done = new AtomicLong();
    tasks = new AtomicLong();
    this.costX = costX;
    this.costY = costY;
    this.points = points;
    abort = false;
  }

  public long getAllTasks() {
    return tasks.get();
  }

  public long getDoneTasks() {
    return done.get();
  }

  public void compute() {
    // матрица расстояний
    distance = new long[points.size()][points.size()];
    for (int i = -1; ++i < points.size();) {
      int x1 = points.get(i).getX();
      int y1 = points.get(i).getY();
      distance[i][i] = 0;
      for (int j = -1; ++j < i;) {
        int x2 = points.get(j).getX();
        int y2 = points.get(j).getY();
        distance[i][j] = Math.max(Math.abs(x1 - x2) * costX, Math.abs(y1 - y2) * costY);
        distance[j][i] = distance[i][j];
      }
    }
    ArrayList<Integer> freeLabels = new ArrayList<>(points.size());
    startMillies = System.currentTimeMillis();
    for (int i = -1; ++i < points.size();) {
      freeLabels.add(i);
    }
    ForkJoinPool pool = new ForkJoinPool();
    pool.invoke(new TSPAction(new ArrayList<>(), freeLabels, 0, 0, null));
    if (!abort) {
      bestSolutionFound = true;
    }
    System.out.println("Execution time is " + (System.currentTimeMillis() - startMillies) + " ms");
  }

  public String printPath(List<Integer> currentPath) {
    StringBuilder path = new StringBuilder();
    path.append("{ ");
    for (int i = -1; ++i < currentPath.size();) {
      if (i == 0) {
        path.append(" ");
        path.append(points.get(currentPath.get(i)));
      } else {
        path.append(" | ");
        path.append(points.get(currentPath.get(i)));
      }
    }
    path.append(" }");
    return path.toString();
  }

  private void removePoint(List<Integer> array, int value) {
    for (int i = -1; ++i < array.size();) {
      if (array.get(i) == value) {
        array.remove(i);
        return;
      }
    }
  }

  /**
   * Найти ближайшую неиспользуемую точку в массиве расстояний для точки с индексом curPos
   */
  private int getNextNearestPoint(int curPos, ArrayList<Integer> freePoints) {
    long minDistance = Long.MAX_VALUE;
    int minIndex = -1;
    for (Integer freePoint : freePoints) {

      if (minDistance > distance[curPos][freePoint]) {
        minDistance = distance[curPos][freePoint];
        minIndex = freePoint;
      }
    }
    removePoint(freePoints, minIndex);
    return minIndex;
  }

  public List<Integer> getBestPath() {
    while (bestPath == null) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
      }
    }
    abort = true;
    synchronized (lock) {
      return bestPath;
    }
  }

  public long getBestPathLength() {
    return bestPathLength;
  }

  private void savePath(ArrayList<Integer> curPath, long curLength) {
    if (curLength < bestPathLength) {
      synchronized (lock) {
        if (curLength < bestPathLength) {
          bestPathLength = curLength;
          bestSolution = System.currentTimeMillis();
          bestPath = (ArrayList<Integer>) curPath.clone();
        }
      }
      System.out.println(tasks.get() + " shots. Path found in " + (bestSolution - startMillies) + " ms. Length=" + curLength);
    }
  }

  public boolean isBestSolutionFound() {
    return bestSolutionFound;
  }

  public void setAbort() {
    abort = true;
  }

  class TSPAction extends RecursiveAction {

    private final ArrayList<Integer> currentPath;
    private final ArrayList<Integer> freePoints;
    private final int curPos;
    private final long length;
    private final TSPAction next;

    public TSPAction(ArrayList<Integer> currentPath, ArrayList<Integer> freePoints, int curPos, long length, TSPAction next) {
      this.currentPath = currentPath;
      this.freePoints = freePoints;
      this.curPos = curPos;
      this.length = length;
      this.next = next;
      tasks.incrementAndGet();
    }

    @Override
    protected void compute() {
      int nextIndex;
      TSPAction nextLevel = null;
      if (bestPathLength < length || abort) {
        done.incrementAndGet();
        return;
      }
      ArrayList<Integer> newPath = (ArrayList) currentPath.clone();
      newPath.add(curPos);
      ArrayList<Integer> newFreePoints = (ArrayList) freePoints.clone();
      removePoint(newFreePoints, curPos);

      if (newFreePoints.isEmpty()) {

        savePath(newPath, length);
      } else {
        ArrayList<Integer> localFreePoints = (ArrayList) newFreePoints.clone();


        nextIndex = getNextNearestPoint(curPos, localFreePoints);
        if (nextIndex >= 0) {
          new TSPAction(newPath, newFreePoints, nextIndex, length + distance[curPos][nextIndex], nextLevel).compute();
        }


        while (localFreePoints.size() > 0 && !abort) {
          nextIndex = getNextNearestPoint(curPos, localFreePoints);
          if (nextIndex >= 0) {
            nextLevel = new TSPAction(newPath, newFreePoints, nextIndex, length + distance[curPos][nextIndex], nextLevel);
            nextLevel.fork();
          }
        }

        while (nextLevel != null) {
          if (nextLevel.tryUnfork()) {
            nextLevel.compute();
          } else {
            nextLevel.join();
          }
          nextLevel = nextLevel.getNext();
        }
      }
      done.incrementAndGet();
    }

    public TSPAction getNext() {
      return next;
    }

  }

  public static void main(String[] args) {

    List<TSPPoint> points = new ArrayList<>(31);
    for (int i = 0; i < 10; i++) {
      points.add(new TSPPoint((int) (Math.random() * 100), (int) (Math.random() * 100), String.valueOf(i)));
    }

    System.out.println("Points " + points);
    TSPForkJoin tsp = new TSPForkJoin(points);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        tsp.setAbort();
      }
    });

    tsp.compute();
    System.out.println("Best solution in " + tsp.getAllTasks() + " shots. Length " + tsp.getBestPathLength());
    System.out.println(tsp.printPath(tsp.getBestPath()));
  }

}
