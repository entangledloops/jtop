import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * @author snd
 * @since November 29, 2016
 */
public class Jtop extends Application
{
  static boolean headless = false;
  static String name = null;
  static Process process = null;

  Thread thread = null;

  @Override
  public void start(Stage primaryStage)
  {
    primaryStage.setTitle("jtop");

    //defining the axes
    final NumberAxis xAxis = new NumberAxis();
    xAxis.setLabel("Time");

    final NumberAxis yAxis = new NumberAxis();
    yAxis.setLabel("Memory");

    //creating the chart
    final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);

    final ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
    final PieChart pieChart = new PieChart(pieChartData);
    pieChart.setBorder(Border.EMPTY);
    pieChart.setScaleX(0.075);
    pieChart.setScaleY(0.075);
    pieChart.setAnimated(false);
    pieChart.setLegendVisible(false);
    pieChart.setLabelsVisible(false);
    pieChart.setCache(true);
    pieChart.setVisible(false);

    lineChart.setTitle(name);
    lineChart.setCreateSymbols(false);

    XYChart.Series cpu = new XYChart.Series();
    cpu.setName("CPU");

    XYChart.Series memory = new XYChart.Series();
    memory.setName("Memory");

    lineChart.getData().addAll(cpu, memory);

    final StackPane root = new StackPane();

    root.getChildren().add(lineChart);
    root.getChildren().add(pieChart);

    final Scene scene = new Scene(root, 1024, 768);
    primaryStage.setScene(scene);

    if (!headless) primaryStage.show();

    thread = new Thread(() ->
    {
      try
      {
        final long millis = 250;
        final long[] prevCpuStats = new long[4];
        final long[] curCpuStats = new long[ prevCpuStats.length ];
        final long[][] prevCoreStats = new long[ Runtime.getRuntime().availableProcessors() ][ prevCpuStats.length ];
        final long[][] curCoreStats = new long[ Runtime.getRuntime().availableProcessors() ][ prevCpuStats.length ];
        final Runtime runtime = Runtime.getRuntime();
        final AtomicLong index = new AtomicLong(0);

        // initialize with current cpu readings
        getCpu(prevCpuStats);
        getCores(prevCoreStats);

        while (process.isAlive())
        {
          Thread.sleep(millis);

          getCpu(curCpuStats);

          final long prevLoad = prevCpuStats[0]+prevCpuStats[1]+prevCpuStats[2];
          final long curLoad = curCpuStats[0]+curCpuStats[1]+curCpuStats[2];
          final double cpuLoadAvg = ( (double) (curLoad - prevLoad) ) / ( (double) ((curLoad+curCpuStats[3]) - (prevLoad+prevCpuStats[3])) );
          final double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / (double) runtime.totalMemory();

          for (int i = 0; i < prevCpuStats.length; ++i) prevCpuStats[i] = curCpuStats[i];

          if (!headless)
          {
            Platform.runLater(() ->
            {
              cpu.getData().add(new XYChart.Data(index.incrementAndGet(), cpuLoadAvg));
              memory.getData().add(new XYChart.Data(index.incrementAndGet(), memoryUsage));
            });
          }

          if (pieChart.isVisible())
          {
            getCores(curCoreStats);

            if (!headless)
            {
              Platform.runLater(() ->
              {
                pieChartData.clear();

                for (int core = 0; core < curCoreStats.length; ++core)
                {
                  final long prevCoreLoad = prevCoreStats[core][0] + prevCoreStats[core][1] + prevCoreStats[core][2];
                  final long curCoreLoad = curCoreStats[core][0] + curCoreStats[core][1] + curCoreStats[core][2];
                  final double coreLoadAvg = ((double) (curCoreLoad - prevCoreLoad)) / ((double) ((curCoreLoad + curCoreStats[core][3]) - (prevCoreLoad + prevCoreStats[core][3])));

                  for (int i = 0; i < prevCoreStats[core].length; ++i)
                    prevCoreStats[core][i] = curCoreStats[core][i];

                  pieChartData.add(new PieChart.Data("Core " + core, coreLoadAvg));
                }
              });
            }
          }
        }

        Platform.exit();
      }
      catch (Throwable ignored) {}
    });

    thread.start();
  }

  private static void getCpu(long[] stats)
  {
    try
    {
      final Scanner scanner = new Scanner(new File("/proc/stat"));
      scanner.next();
      for (int i = 0; i < stats.length; ++i) stats[i] = scanner.nextLong();
      scanner.close();
    }
    catch (Throwable t) { t.printStackTrace(); Platform.exit(); }
  }

  private static void getCores(long[][] cores)
  {
    try
    {
      final Scanner scanner = new Scanner(new File("/proc/stat"));
      for (int core = 0; core < cores.length; ++core)
      {
        scanner.nextLine(); scanner.next();
        for (int i = 0; i < cores[0].length; ++i) cores[core][i] = scanner.nextLong();
      }
      scanner.close();
    }
    catch (Throwable t) { t.printStackTrace(); Platform.exit(); }
  }

  @Override
  public void stop() throws Exception
  {
    thread.interrupt();
    process.destroy();
    super.stop();
  }

  public static void main(String[] args)
  {
    try
    {
      if ("--headless".equalsIgnoreCase(args[0])) headless = true;
      name = Stream.of(args).filter(arg -> !arg.equalsIgnoreCase("--headless")).reduce("", (s,t) -> s + " " + t);
      process = Runtime.getRuntime().exec(name);
      launch(name);
    }
    catch (Throwable t)
    {
      System.err.println("Failed to launch process:");
      t.printStackTrace();
      System.exit(1);
    }
  }
}
